"""Thin HTTP client over the deployment-manager API."""

from __future__ import annotations

import logging

import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

log = logging.getLogger("mcp.api")


class DeploymentApi:
    """Thin HTTP client over a pooled, retrying `requests.Session`.

    A default per-request timeout is applied so a hung server never blocks
    forever, and transient 5xx/429 responses are retried for idempotent GETs.
    """

    def __init__(self, base_url: str, token: str, timeout: int = 30):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

        self.session = requests.Session()
        self.session.verify = False
        self.session.headers.update(
            {"Authorization": f"Bearer {token}", "If-None-Match": "*"}
        )
        retry = Retry(
            total=3,
            backoff_factor=0.5,
            status_forcelist=(429, 500, 502, 503, 504),
            allowed_methods=frozenset({"GET", "DELETE"}),
            raise_on_status=False,
        )
        adapter = HTTPAdapter(max_retries=retry, pool_connections=10, pool_maxsize=10)
        self.session.mount("http://", adapter)
        self.session.mount("https://", adapter)

    def close(self) -> None:
        self.session.close()

    def _request(self, method: str, path: str, expected=None, **kwargs) -> requests.Response:
        kwargs.setdefault("timeout", self.timeout)
        resp = self.session.request(method, f"{self.base_url}{path}", **kwargs)
        log.info("%s %s -> %s", method, path, resp.status_code)
        if expected is not None and resp.status_code != expected:
            raise AssertionError(f"Expected {expected}, got {resp.status_code}: {resp.text}")
        return resp

    def post(self, path, json_body=None, expected=None):
        return self._request("POST", path, expected=expected, json=json_body)

    def get(self, path, expected=None):
        return self._request("GET", path, expected=expected)

    def delete(self, path, expected=None):
        return self._request("DELETE", path, expected=expected)
