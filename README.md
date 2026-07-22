# AI Dial Admin Performance Testing Framework

Performance testing framework for [AI Dial Admin](https://github.com/epam/ai-dial-admin-backend) using Gatling.

## Scenarios

| Scenario | Description |
|---|---|
| `aiDialAdminCreateModelAPI` | Azure AD authentication + Create Model via API + Sync model state polling |
| `sharingRequests` | Full DIAL Core **Sharing** API workflow: create/list/copy/revoke shared resources, invitation list/get/accept/delete, and (with a second Api-Key) receiver accept/discard |
| `perRequestPermissions` | DIAL Core per-request-permissions grant/list/revoke (requires a per-request API key — a plain Api-Key returns 403) |
| `publicationRequests` | DIAL Core **Publications** API workflow: rule list, create publish request, list, get, delete |
| `publicationAdmin` | Admin-only DIAL Core **Publications** endpoints: update/approve/reject (requires an admin-privileged Api-Key — a regular key returns 403) |

### Sharing scenario configuration

The `sharingRequests` scenario needs a DIAL Core Api-Key for the resource owner
(`dialCoreApiKey`). Provide `dialCoreApiKey2` (a second identity) to also exercise
the receiver-side accept/discard steps; leaving it empty runs the owner-only subset.
The `perRequestPermissions` scenario additionally uses `shareReceiverDeployment`
(the receiving deployment id).

## Prerequisites

- JDK 17+
- Gradle 8.5+
- Azure AD credentials for Dial Admin users
- Python 3.9+ (only for the [MCP deployment script](#mcp-container-deployment-script-python))

## Environment Variables

A `.env` file in the **project root** is auto-loaded by both the Gatling
framework and the [MCP deployment script](#mcp-container-deployment-script-python).
Create it with the following keys:

```
# --- Auth (used by Gatling + MCP deploy script) ---
NEXTAUTH_SECRET=<nextauth-secret>          # secret used to decrypt the NextAuth session cookie
DIAL_ADMIN_CLIENT_ID=<Azure AD / Auth0 client ID>

# --- Base URLs (used by the MCP deploy script) ---
URL_ADMIN=https://<admin-app-host>          # Admin app base URL (Auth0 login)
URL_DEPLOY_SERVICE=https://<deployment-manager-host>   # deployment-manager API base URL
```

| Variable | Used by | Description |
|---|---|---|
| `NEXTAUTH_SECRET` | Gatling + deploy script | Secret that derives the NextAuth cookie-decryption key |
| `DIAL_ADMIN_CLIENT_ID` | Gatling + deploy script | Azure AD / Auth0 client ID |
| `URL_ADMIN` | Deploy script | Admin app base URL (Auth0 login) |
| `URL_DEPLOY_SERVICE` | Deploy script | Deployment-manager API base URL |

> The `.env` file is git-ignored — never commit real secrets.

### User credentials

For Auth0/Azure usernames and passwords the `azure-users.csv` file should be placed at `src/main/resources/data/azure-users.
csv` with columns: `username,password`.

```csv
username,password
dial_admin@example.com,<password>
```

## Running Tests Locally

### Run a debug simulation (single iteration):

```bash
./gradlew gatlingRun --simulation=DebugSimulation \
  -DscenarioName=aiDialAdminCreateModelAPI \
  -DaiAdminBaseUrl=https://your-dial-admin-url/ \
  -Dusers=1 \
  -Dduration=300s
```

### Run a load simulation:

```bash
./gradlew gatlingRun --simulation=LoadSimulation \
  -DscenarioName=aiDialAdminCreateModelAPI \
  -DaiAdminBaseUrl=https://your-dial-admin-url/ \
  -Dusers=5 \
  -Dduration=10m \
  -DdurationRampUp=1m \
  -DdurationRampDown=30s
```

## MCP Container Deployment Script (Python)

`scripts/DeployApp/run_mcp_container.py` is a self-contained Python tool that
builds an MCP image and **runs (deploys)** a container against the deployment
manager. It never stops/undeploys the container — it leaves it running.

What it does:

1. Logs into the DIAL Admin app via Auth0 using a pure-HTTP flow (no browser)
   and decrypts the NextAuth session cookie into a bearer token.
2. Creates an MCP image definition and builds it (waits for `BUILD_SUCCESSFUL`).
3. Creates an MCP deployment (container) from that image.
4. Deploys the container and waits until its status is `running`.

The logic is split into focused modules so the entry point stays thin:

| File | Responsibility |
|---|---|
| `run_mcp_container.py` | CLI parsing + orchestration (the file you run) |
| `config.py` | `.env` loading, env resolution, `Config` dataclass, users-CSV reader |
| `auth.py` | Pure-HTTP Auth0 login → decrypted bearer token |
| `api.py` | `DeploymentApi` HTTP client (pooled + retrying) |
| `workflow.py` | Create/build image, create/run deployment, cleanup |
| `constants.py` | Shared constants / API payload defaults / Auth0 defaults |

### Prerequisites

- Python 3.9+
- The project-root `.env` populated with `URL_ADMIN`, `URL_DEPLOY_SERVICE`,
  `NEXTAUTH_SECRET` (see [Environment Variables](#environment-variables))
- Credentials in `src/main/resources/data/azure-users.csv`

### Setup

```bash
cd scripts/DeployApp
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

### Execution

```bash
cd scripts/DeployApp
python run_mcp_container.py
```

Optional env: `DIAL_ADMIN_CLIENT_ID` (auto-derived from the login page when
omitted) and `AZURE_USERS_FILE` (override the users-CSV location; defaults to
`data/azure-users.csv` under `src/main/resources`).

## Configuration Parameters

| Parameter | Description | Default |
|---|---|---|
| `scenarioName` | Scenario to execute | `aiDialAdminCreateModelAPI` |
| `aiAdminBaseUrl` | AI Dial Admin base URL | `https:://ai-dial-admin.example.com/` |
| `users` | Number of concurrent users | `10` |
| `duration` | Test duration (e.g. 300s, 10m, 1h) | `600s` |
| `durationRampUp` | Ramp-up duration | `20s` |
| `durationRampDown` | Ramp-down duration | `20s` |
| `openModel` | Use open injection model | `false` |
| `modelSyncAttempts` | Max sync polling attempts | `50` |
| `modelSyncPauseDuration` | Pause between sync polls (seconds) | `5` |
| `modelEndpoint` | Model endpoint URL for created models | `https://api.openai.com/v1gpt-4-turbo/chat/completions/` |
