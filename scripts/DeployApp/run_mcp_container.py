#!/usr/bin/env python3
"""Entry point: build an MCP image and run (deploy) a container.

Only *runs* the container (it never stops/undeploys it):
  1. Logs into the DIAL Admin app via Auth0 using a pure-HTTP flow (no browser)
     and decrypts the NextAuth session cookie into a bearer token (see `auth`).
  2. Creates an MCP image definition and builds it (waits for BUILD_SUCCESSFUL).
  3. Creates an MCP deployment (container) from that image.
  4. Runs (deploys) the container and waits until status == "running", then
     leaves it running.

The heavy lifting lives in helper modules so this file stays a thin CLI +
orchestration layer:
  constants.py  shared constants / API payload defaults / Auth0 defaults
  config.py     `.env` loading, env resolution and the `Config` dataclass
  auth.py       pure-HTTP Auth0 login -> decrypted bearer token
  api.py        `DeploymentApi` HTTP client (pooled + retrying)
  workflow.py   create/build image, create/run deployment, cleanup

Environment variables are resolved with the same principle as the Java
`core.PropertiesHolder`: the first non-blank value among a set of alias keys
wins, otherwise a default is used. The repo-root `.env` (the main folder, two
levels above this script) is auto-loaded, falling back to a `.env` in the CWD.

Required env (first matching alias is used):
  URL_ADMIN / url_admin / aiAdminBaseUrl  base URL of the Admin app (Auth0 login)
  URL_DEPLOY_SERVICE / url_depl           base URL of the deployment-manager API
  NEXTAUTH_SECRET / nextAuthSecret        secret used to derive the cookie-decryption key

Auth0 credentials (NOT env vars) come from a CSV feeder with columns
`username,password` (the first valid row is used), the same file the Java
Gatling scenarios read via `PropertiesHolder.aiAdminUsersFile`:
  src/main/resources/data/azure-users.csv   (override: AZURE_USERS_FILE / aiAdminUsersFile)

Optional Auth0 override (auto-derived from the login page when omitted):
  DIAL_ADMIN_CLIENT_ID / dialAdminClientId / client_id / clientId / auth0ClientId

The Auth0 tenant, connection, audience and client-telemetry header are fixed
defaults (see constants.py) since they are not configured via `.env`.

Optional runtime knobs / CLI flags:
  IMAGE_BUILD_TIMEOUT=180        seconds to wait for the build      (--build-timeout)
  DEPLOYMENT_STATUS_TIMEOUT=280  seconds to wait for run status      (--status-timeout)
  POLL_INTERVAL=10               seconds between status polls       (--poll-interval)
  HTTP_TIMEOUT=30                per-request timeout in seconds     (--http-timeout)

Setup:
  pip install -r requirements.txt
  python run_mcp_container.py
"""

from __future__ import annotations

import argparse
import logging
import sys

import urllib3

from api import DeploymentApi
from auth import get_access_token
from config import build_config
from constants import STATUS_RUNNING
from workflow import (
    build_image_and_wait,
    cleanup,
    create_mcp_deployment,
    create_mcp_image,
    random_name,
    run_deployment_and_wait,
)

urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

log = logging.getLogger("mcp")


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    clean = parser.add_mutually_exclusive_group()
    clean.add_argument("--cleanup", dest="cleanup", action="store_true", default=None,
                       help="delete the created deployment + image afterwards (env CLEANUP)")
    clean.add_argument("--no-cleanup", dest="cleanup", action="store_false",
                       help="leave the container running (default)")
    parser.add_argument("--build-timeout", type=int, default=None, help="image build timeout (s)")
    parser.add_argument("--status-timeout", type=int, default=None, help="run status timeout (s)")
    parser.add_argument("--poll-interval", type=int, default=None, help="status poll interval (s)")
    parser.add_argument("--http-timeout", type=int, default=None, help="per-request timeout (s)")
    parser.add_argument("-v", "--verbose", action="store_true", help="enable debug logging")
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(message)s",
        datefmt="%H:%M:%S",
    )
    cfg = build_config(args)

    log.info("Authenticating (Auth0 HTTP login + token decryption)...")
    token = get_access_token(cfg)
    api = DeploymentApi(cfg.url_depl, token, timeout=cfg.http_timeout)

    image_id: str | None = None
    deployment_name: str | None = None
    try:
        log.info("Creating and building MCP image...")
        image_id = create_mcp_image(api, random_name("TestImage"))
        build_image_and_wait(api, image_id, cfg)

        log.info("Creating MCP deployment...")
        deployment_name = create_mcp_deployment(api, random_name("testcontainername"), image_id)

        log.info("Running deployment...")
        status = run_deployment_and_wait(api, deployment_name, cfg)
        assert status == STATUS_RUNNING, f"Expected running, got {status}"

        log.info("PASSED: container is running (deployment '%s').", deployment_name)
        return 0
    finally:
        if cfg.cleanup:
            cleanup(api, deployment_name, image_id)
        api.close()


if __name__ == "__main__":
    sys.exit(main())
