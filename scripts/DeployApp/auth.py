"""Pure-HTTP Auth0 login -> NextAuth session cookie -> decrypted bearer token.

Flow (no browser):
  scrape the NextAuth csrfToken -> initiate the Auth0 sign-in -> run the
  username/password challenge + login -> post the wsfed callback -> read the
  `__Secure-next-auth.session-token` cookie and decrypt it into a bearer token.
"""

from __future__ import annotations

import base64
import html
import json
import logging
import re
from urllib.parse import parse_qs, unquote, urlparse

import requests
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from jwcrypto import jwe, jwk

from config import Config
from constants import (
    BROWSER_HEADERS,
    DEFAULT_AUTH0_AUDIENCE,
    DEFAULT_AUTH0_CLIENT,
    DEFAULT_AUTH0_CONNECTION,
    DEFAULT_AUTH0_TENANT,
    SESSION_COOKIE_PREFIX,
    USER_AGENT,
)

log = logging.getLogger("mcp.auth")


def _derived_encryption_key(secret: bytes) -> bytes:
    hkdf = HKDF(
        algorithm=hashes.SHA256(),
        length=32,
        salt=b"",
        info=b"NextAuth.js Generated Encryption Key",
        backend=default_backend(),
    )
    return hkdf.derive(secret)


def _decrypt_token(token: str, encryption_secret: bytes) -> dict:
    key = jwk.JWK(
        kty="oct",
        k=base64.urlsafe_b64encode(encryption_secret).decode("utf-8").rstrip("="),
    )
    jwe_token = jwe.JWE()
    jwe_token.deserialize(token)
    jwe_token.decrypt(key)
    return json.loads(jwe_token.payload)


def _search(text: str, pattern: str) -> str | None:
    m = re.search(pattern, text)
    return m.group(1) if m else None


def _first(qs: dict, key: str) -> str | None:
    values = qs.get(key)
    return values[0] if values else None


def _input_value(html_text: str, name: str) -> str | None:
    """Extract the `value` of a hidden <input> by name, tolerant of attribute order."""
    tag = re.search(
        r'<input\b[^>]*\bname=["\']' + re.escape(name) + r'["\'][^>]*>', html_text, re.I
    )
    if not tag:
        return None
    value = re.search(r'\bvalue=["\'](.*?)["\']', tag.group(0), re.I | re.S)
    return html.unescape(value.group(1)) if value else None


def _session_jwe_from_cookies(jar) -> str:
    """Reassemble the (possibly chunked) NextAuth session-token cookie into one JWE."""
    chunks = []
    for cookie in jar:
        if cookie.name == SESSION_COOKIE_PREFIX:
            chunks.append((0, cookie.value))
        elif cookie.name.startswith(SESSION_COOKIE_PREFIX + "."):
            try:
                chunks.append((int(cookie.name[len(SESSION_COOKIE_PREFIX) + 1:]), cookie.value))
            except ValueError:
                continue
    chunks.sort(key=lambda c: c[0])
    return "".join(unquote(value) for _, value in chunks)


def get_access_token(cfg: Config) -> str:
    admin = cfg.url_admin.rstrip("/")
    session = requests.Session()
    session.verify = False
    session.headers.update({"User-Agent": USER_AGENT})

    # 1) Load the NextAuth sign-in page and grab its csrfToken.
    resp = session.get(admin + "/", headers=BROWSER_HEADERS, timeout=cfg.http_timeout)
    log.debug("GET / -> %s", resp.status_code)
    csrf_token = _search(resp.text, r'name="csrfToken"[^>]*value="([^"]+)"')
    if not csrf_token:
        raise RuntimeError("Could not find the NextAuth csrfToken on the sign-in page.")

    # 2) Initiate the Auth0 sign-in; redirects land on the Auth0 login page.
    resp = session.post(
        admin + "/api/auth/signin/auth0",
        data={"csrfToken": csrf_token, "callbackUrl": "/"},
        headers={
            **BROWSER_HEADERS,
            "Content-Type": "application/x-www-form-urlencoded",
            "Origin": admin,
            "Referer": admin + "/api/auth/signin?callbackUrl=%2F",
        },
        timeout=cfg.http_timeout,
    )
    login_url = resp.url
    parsed = urlparse(login_url)
    auth0_host = f"{parsed.scheme}://{parsed.netloc}"
    qs = parse_qs(parsed.query)
    log.debug("Auth0 login page: %s", auth0_host)

    state = _first(qs, "state")
    redirect_uri = _first(qs, "redirect_uri") or admin + "/api/auth/callback/auth0"
    audience = _first(qs, "audience") or DEFAULT_AUTH0_AUDIENCE
    code_challenge = _first(qs, "code_challenge") or ""
    code_challenge_method = _first(qs, "code_challenge_method") or "S256"
    scope = _first(qs, "scope") or "openid email profile offline_access"

    # Auth0 embeds a base64 `data-config` blob (client id, tenant, csrf, ...).
    config_json: dict = {}
    decoded_config = ""
    data_config = _search(resp.text, r'data-config="([^"]+)"')
    if data_config:
        try:
            decoded_config = base64.b64decode(data_config).decode("utf-8", "replace")
            config_json = json.loads(decoded_config)
            log.debug("  parsed data-config: %s", list(config_json.keys()))
        except Exception as e:  # noqa: BLE001 - fall back to regex/env below
            log.debug("  failed to parse data-config: %s", e)
            config_json = {}
    else:
        log.debug("  data-config not found on Auth0 login page")

    auth0_csrf = (
        _search(resp.text, r'"_csrf"\s*:\s*"([^"]+)"')
        or _search(resp.text, r'name="_csrf"[^>]*value="([^"]+)"')
        or config_json.get("_csrf")
        or _search(decoded_config, r'"_csrf"\s*:\s*"([^"]+)"')
        or ""
    )
    # client_id is present on the Auth0 login page URL (as `client` / `client_id`),
    # in the embedded data-config, or can be forced via env.
    client_id = (
        cfg.client_id
        or _first(qs, "client")
        or _first(qs, "client_id")
        or config_json.get("clientID")
        or config_json.get("client_id")
        or ""
    )
    tenant = config_json.get("auth0Tenant") or DEFAULT_AUTH0_TENANT

    if not state:
        raise RuntimeError("Auth0 'state' was not present in the login page URL.")
    if not client_id:
        raise RuntimeError(
            "Auth0 client_id not found on the login page; set DIAL_ADMIN_CLIENT_ID."
        )

    auth0_headers = {
        "Accept": "*/*",
        "auth0-client": DEFAULT_AUTH0_CLIENT,
        "Origin": auth0_host,
        "Referer": login_url,
    }

    # 3) Username/password challenge (Auth0 requires it before login).
    resp = session.post(
        auth0_host + "/usernamepassword/challenge",
        json={"state": state},
        headers=auth0_headers,
        timeout=cfg.http_timeout,
    )
    log.debug("POST /usernamepassword/challenge -> %s", resp.status_code)

    # 4) Username/password login -> returns a wsfed auto-submit form.
    payload = {
        "client_id": client_id,
        "redirect_uri": redirect_uri,
        "tenant": tenant,
        "response_type": "code",
        "scope": scope,
        "state": state,
        "connection": DEFAULT_AUTH0_CONNECTION,
        "username": cfg.user,
        "password": cfg.password,
        "popup_options": {},
        "sso": True,
        "_intstate": "deprecated",
        "_csrf": auth0_csrf,
        "audience": audience,
        "code_challenge_method": code_challenge_method,
        "code_challenge": code_challenge,
        "protocol": "oauth2",
    }
    resp = session.post(
        auth0_host + "/usernamepassword/login",
        json=payload,
        headers=auth0_headers,
        timeout=cfg.http_timeout,
    )
    if resp.status_code != 200:
        raise RuntimeError(f"Auth0 login failed ({resp.status_code}): {resp.text[:300]}")
    wa = _input_value(resp.text, "wa")
    wresult = _input_value(resp.text, "wresult")
    wctx = _input_value(resp.text, "wctx")
    if not (wa and wresult and wctx):
        raise RuntimeError(
            "Auth0 login did not return the wsfed callback form (wa/wresult/wctx); "
            "check credentials or connection."
        )

    # 5) Post the wsfed callback; redirects back to the app set the session cookie.
    resp = session.post(
        auth0_host + "/login/callback",
        data={"wa": wa, "wresult": wresult, "wctx": wctx},
        headers={
            **BROWSER_HEADERS,
            "Content-Type": "application/x-www-form-urlencoded",
            "Origin": auth0_host,
            "Referer": login_url,
        },
        timeout=cfg.http_timeout,
    )
    log.debug("POST /login/callback -> %s (final %s)", resp.status_code, resp.url)

    encrypted = _session_jwe_from_cookies(session.cookies)
    if not encrypted:
        raise RuntimeError(
            "Auth0 flow completed but no __Secure-next-auth.session-token cookie was set."
        )
    decoded = _decrypt_token(encrypted, _derived_encryption_key(cfg.nextauth_secret.encode()))
    token = decoded.get("access_token") or decoded.get("accessToken")
    if not token:
        raise RuntimeError("Decrypted NextAuth session contains no access_token.")
    return token
