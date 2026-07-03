"""Configuration: `.env` loading, env resolution and the `Config` dataclass.

Environment variables are resolved with the same principle as the Java
`core.PropertiesHolder`: the first non-blank value among a set of alias keys
wins, otherwise a default is used. The repo-root `.env` (the main folder, two
levels above this script) is auto-loaded, falling back to a `.env` in the CWD.

The Auth0 username/password are not env vars: they are read from the
`azure-users.csv` feeder (columns `username,password`), the same file the Java
Gatling scenarios use via `PropertiesHolder.aiAdminUsersFile`.
"""

from __future__ import annotations

import argparse
import csv
import os
import sys
from dataclasses import dataclass
from pathlib import Path

# Default users feeder, relative to `src/main/resources` (mirrors the Java
# `PropertiesHolder.aiAdminUsersFile` default). Overridable via env.
DEFAULT_USERS_FILE = "data/azure-users.csv"


def resolve(*keys: str, default: str = "") -> str:
    """First non-blank env value among `keys`, else `default`.

    Mirrors `core.PropertiesHolder.resolve`: a `.env` file is loaded into the
    environment first, so checking env vars covers both file keys and real env
    variables under a single set of aliases.
    """
    for key in keys:
        value = os.getenv(key)
        if value and value.strip():
            return value.strip()
    return default


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() not in ("false", "0", "no", "off", "")


def _env_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    try:
        return int(raw) if raw is not None else default
    except ValueError:
        return default


@dataclass
class Config:
    url_admin: str
    url_depl: str
    user: str
    password: str
    nextauth_secret: str
    client_id: str = ""
    cleanup: bool = False
    build_timeout: int = 180
    status_timeout: int = 280
    poll_interval: int = 10
    http_timeout: int = 30


def load_dotenv() -> None:
    """Load the repo-root `.env` (two levels up), falling back to CWD `.env`.

    Minimal parser (no external dependency); existing env vars take precedence.
    """
    repo_root = Path(__file__).resolve().parents[2]
    for candidate in (repo_root / ".env", Path.cwd() / ".env"):
        if not candidate.is_file():
            continue
        for line in candidate.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            key, value = key.strip(), value.strip().strip('"').strip("'")
            os.environ.setdefault(key, value)


def _users_file_path() -> Path:
    """Resolve the users CSV path.

    An override (`aiAdminUsersFile` / `AZURE_USERS_FILE`) may be absolute or
    relative to the CWD; otherwise the default is resolved against the repo's
    `src/main/resources` directory, matching the Java Gatling feeder.
    """
    resources_dir = Path(__file__).resolve().parents[2] / "src" / "main" / "resources"
    override = resolve("aiAdminUsersFile", "AZURE_USERS_FILE")
    if override:
        candidate = Path(override).expanduser()
        if candidate.is_absolute() or candidate.exists():
            return candidate
        return resources_dir / override
    return resources_dir / DEFAULT_USERS_FILE


def load_first_user(path: Path) -> tuple[str, str]:
    """Return the first non-blank `(username, password)` row from the CSV."""
    if not path.is_file():
        sys.exit(f"Users file not found: {path} (expected columns: username,password)")
    with path.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            username = (row.get("username") or "").strip()
            password = (row.get("password") or "").strip()
            if username and password:
                return username, password
    sys.exit(f"No valid 'username,password' row found in {path}")


def build_config(args: argparse.Namespace) -> Config:
    load_dotenv()

    # Resolve required values using PropertiesHolder-style key aliases
    # (primary name matches the repo-root .env).
    required = {
        "URL_ADMIN": resolve("URL_ADMIN", "url_admin", "aiAdminBaseUrl"),
        "URL_DEPLOY_SERVICE": resolve("URL_DEPLOY_SERVICE", "url_depl"),
        "NEXTAUTH_SECRET": resolve("NEXTAUTH_SECRET", "nextAuthSecret"),
    }
    missing = [name for name, value in required.items() if not value]
    if missing:
        sys.exit(f"Missing required environment variables: {', '.join(missing)}")

    # Auth0 username/password come from the azure-users.csv feeder, not env.
    username, password = load_first_user(_users_file_path())

    cleanup = args.cleanup if args.cleanup is not None else _env_bool("CLEANUP", False)
    return Config(
        url_admin=required["URL_ADMIN"],
        url_depl=required["URL_DEPLOY_SERVICE"],
        user=username,
        password=password,
        nextauth_secret=required["NEXTAUTH_SECRET"],
        client_id=resolve(
            "DIAL_ADMIN_CLIENT_ID", "dialAdminClientId", "client_id", "clientId", "auth0ClientId"
        ),
        cleanup=cleanup,
        build_timeout=args.build_timeout or _env_int("IMAGE_BUILD_TIMEOUT", 180),
        status_timeout=args.status_timeout or _env_int("DEPLOYMENT_STATUS_TIMEOUT", 280),
        poll_interval=args.poll_interval or _env_int("POLL_INTERVAL", 10),
        http_timeout=args.http_timeout or _env_int("HTTP_TIMEOUT", 30),
    )
