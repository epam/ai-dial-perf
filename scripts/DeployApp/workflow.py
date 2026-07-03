"""Workflow steps: create + build an MCP image, create + run a deployment.

Also provides best-effort cleanup of the created resources.
"""

from __future__ import annotations

import logging
import random
import time

from api import DeploymentApi
from config import Config
from constants import (
    ALLOWED_DOMAINS,
    BUILD_FAILED,
    BUILD_SUCCESSFUL,
    DEFAULT_MCP_DOCKER_IMAGE,
    STATUS_RUNNING,
)

log = logging.getLogger("mcp.workflow")


def random_name(prefix: str) -> str:
    return f"{prefix}{random.randint(100000, 999999)}"


def create_mcp_image(api: DeploymentApi, name: str) -> str:
    body = {
        "name": name,
        "id": None,
        "version": "1.0.0",
        "description": "",
        "source": {"$type": "docker", "imageUri": DEFAULT_MCP_DOCKER_IMAGE},
        "buildStatus": "NOT_BUILT",
        "allowedDomains": ALLOWED_DOMAINS,
        "imageBuilder": "BUILDKIT",
        "$type": "mcp",
        "transportType": "local",
    }
    resp = api.post("/api/v1/images/definitions", body, expected=201)
    return resp.json()["id"]


def _latest_sse_status(text: str) -> str | None:
    return next(
        (
            line.split(":", 1)[1].strip()
            for line in reversed(text.splitlines())
            if line.startswith("data:")
        ),
        None,
    )


def build_image_and_wait(api: DeploymentApi, image_id: str, cfg: Config) -> None:
    api.post("/api/v1/images/builds", {"imageDefinitionId": image_id}, expected=201)
    start = time.monotonic()
    while True:
        resp = api.get(f"/api/v1/images/builds/{image_id}/status")
        status = _latest_sse_status(resp.text)
        elapsed = int(time.monotonic() - start)
        log.info("  build status: %s (%ds)", status, elapsed)
        if status == BUILD_SUCCESSFUL:
            return
        if status == BUILD_FAILED or elapsed > cfg.build_timeout:
            raise TimeoutError(f"Image build did not succeed (last status: {status})")
        time.sleep(cfg.poll_interval)


def create_mcp_deployment(api: DeploymentApi, name: str, image_id: str) -> str:
    body = {
        "name": name,
        "displayName": name,
        "version": "1.0.0",
        "description": "",
        "$type": "mcp",
        "status": "not_deployed",
        "source": {"$type": "internal_image", "imageDefinitionId": image_id},
        "metadata": {"envs": []},
        "scaling": {"minReplicas": 0, "maxReplicas": 1, "scaleToZeroDelaySeconds": 300},
        "resources": {
            "requests": {"cpu": "0.5", "memory": "1073741824"},
            "limits": {"cpu": "0.5", "memory": "1073741824"},
        },
        "containerPort": None,
        "transport": "http_streaming",
    }
    resp = api.post("/api/v1/deployments", body, expected=201)
    return resp.json()["name"]


def wait_for_status(api: DeploymentApi, name: str, expected: str, cfg: Config) -> str:
    start = time.monotonic()
    while True:
        status = api.get(f"/api/v1/deployments/{name}").json().get("status")
        elapsed = int(time.monotonic() - start)
        log.info("  deployment status: %s (%ds)", status, elapsed)
        if status == expected:
            return status
        if elapsed > cfg.status_timeout:
            raise TimeoutError(f"Status did not become '{expected}' in time (last: {status})")
        time.sleep(cfg.poll_interval)


def run_deployment_and_wait(api: DeploymentApi, name: str, cfg: Config) -> str:
    api.post(f"/api/v1/deployments/{name}/deploy", expected=200)
    return wait_for_status(api, name, STATUS_RUNNING, cfg)


def cleanup(api: DeploymentApi, deployment_name: str | None, image_id: str | None) -> None:
    """Best-effort teardown: one failure must not hide another resource leak."""
    log.info("Cleaning up...")
    if deployment_name:
        try:
            api.delete(f"/api/v1/deployments/{deployment_name}")
        except Exception as exc:  # noqa: BLE001 - cleanup must not raise
            log.warning("Failed to delete deployment %s: %s", deployment_name, exc)
    if image_id:
        try:
            api.delete(f"/api/v1/images/definitions/{image_id}")
        except Exception as exc:  # noqa: BLE001 - cleanup must not raise
            log.warning("Failed to delete image %s: %s", image_id, exc)
