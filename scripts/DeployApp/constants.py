"""Shared constants for the MCP deploy script.

Single source of truth for API payload defaults, status literals and the Auth0
defaults (which mirror `core.PropertiesHolder`).
"""

from __future__ import annotations

import base64
import json

# --- MCP image / deployment payloads --------------------------------------- #
DEFAULT_MCP_DOCKER_IMAGE = "d0nets/simple-mcp:0.0.4"
ALLOWED_DOMAINS = [
    "toolbox-data.anchore.io",
    "production.cloudfront.docker.com",
    "ghcr.io",
    "pkg-containers.githubusercontent.com",
    "*",
]

# --- Status literals -------------------------------------------------------- #
BUILD_SUCCESSFUL = "BUILD_SUCCESSFUL"
BUILD_FAILED = "BUILD_FAILED"
STATUS_RUNNING = "running"

# --- Auth0 defaults (single source of truth; mirror core.PropertiesHolder) -- #
DEFAULT_AUTH0_TENANT = "aidial"
DEFAULT_AUTH0_CONNECTION = "test-gke-dial"
DEFAULT_AUTH0_AUDIENCE = "test_gke_dial"

# --- HTTP headers used to look like a real browser during the Auth0 flow ---- #
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36"
)
BROWSER_HEADERS = {
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.9",
    "Upgrade-Insecure-Requests": "1",
}
DEFAULT_AUTH0_CLIENT = base64.b64encode(
    json.dumps({"name": "auth0.js-ulp", "version": "9.23.3"}).encode()
).decode()
SESSION_COOKIE_PREFIX = "__Secure-next-auth.session-token"
