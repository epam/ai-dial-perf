# AI Dial Admin Performance Testing Framework

Performance testing framework for [AI Dial Admin](https://github.com/epam/ai-dial-admin-backend) using Gatling.

## Scenarios

| Scenario | Description |
|---|---|
| `aiDialAdminCreateModelAPI` | Azure AD authentication + Create Model via API + Sync model state polling |
| `createKeyWithRole` | Create a unique role and key with the DIAL Core API key, then verify the key's assigned role |
| `sharingRequests` | Full DIAL Core **Sharing** API workflow: create/list/copy/revoke shared resources, invitation list/get/accept/delete, and (with a second Api-Key) receiver accept/discard |
| `perRequestPermissions` | DIAL Core per-request-permissions grant/list/revoke (requires a per-request API key — a plain Api-Key returns 403) |
| `publicationRequests` | Full DIAL Core **Publications** workflow: create/get/list/update/approve/reject/delete, rules and published-resource listing, plus unpublish cleanup |
| `mcpContainerMixedRequests` | Authenticates, starts an MCP container, saves its URL as `toolsetEndpoint`, then runs all six in-scope request workflows with one shared configurable probability (excludes per-request permissions) |

### Sharing scenario configuration

The `sharingRequests` scenario needs a DIAL Core Api-Key for the resource owner
(`DIAL_CORE_API_KEY`). Provide `DIAL_CORE_API_KEY_2` (a second identity) to also exercise
the receiver-side accept/discard steps; leaving it empty runs the owner-only subset.
The `perRequestPermissions` scenario additionally uses `SHARE_RECEIVER_DEPLOYMENT`
(the receiving deployment id) and `DIAL_CORE_PER_REQUEST_API_KEY` (a per-request key
issued by DIAL Core to the sending deployment). For backwards compatibility,
the per-request key falls back to `DIAL_CORE_API_KEY` when it is not set.

### Publications scenario configuration

The `publicationRequests` scenario uses `DIAL_CORE_API_KEY` for the publication owner
and authenticates the administrator through the existing Admin UI Auth0 flow. The
administrator credentials must be present in `src/main/resources/data/azure-users.csv`.
If UI authentication is unavailable, provide an administrator token directly through
`publicationAdminBearerToken` or `PUBLICATION_ADMIN_BEARER_TOKEN`.
Each iteration uses unique private and public prompt paths and removes both through
the publication unpublish workflow and source-resource cleanup.

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
# --- Admin authentication and service URLs ---
NEXTAUTH_SECRET=<nextauth-secret>          # secret used to decrypt the NextAuth session cookie
URL_ADMIN=https://<admin-app-host>          # Admin app base URL (Auth0 login)
URL_DEPLOY_SERVICE=https://<deployment-manager-host>   # deployment-manager API base URL

# --- DIAL Core ---
DIAL_CORE_BASE_URL=https://<dial-core-host>
DIAL_CORE_API_KEY=<owner-api-key>
DIAL_CORE_API_KEY_2=<receiver-api-key>      # optional; sharing receiver-side steps only

# --- Existing public fixtures / standalone endpoint ---
APP_NAME=<existing-public-application>
TOOLSET_ENDPOINT=https://<standalone-mcp-endpoint>
FILE_NAME=<existing-public-file>
```

| Variable | Used by | Description |
|---|---|---|
| `NEXTAUTH_SECRET` | Gatling + deploy script | Secret that derives the NextAuth cookie-decryption key |
| `URL_ADMIN` | Gatling + deploy script | Admin app base URL used for Auth0 login |
| `URL_DEPLOY_SERVICE` | Gatling + deploy script | Deployment-manager API base URL |
| `DIAL_CORE_BASE_URL` | Gatling | DIAL Core base URL |
| `DIAL_CORE_API_KEY` | Gatling | Owner identity used by DIAL Core scenarios |
| `DIAL_CORE_API_KEY_2` | Gatling | Optional second identity used by sharing receiver-side steps |
| `APP_NAME` | Application requests | Existing application in the public bucket |
| `TOOLSET_ENDPOINT` | Standalone toolset requests | MCP endpoint; mixed MCP tests derive it from the deployment response |
| `FILE_NAME` | File requests | Existing source file in the public bucket |

Auth0 scope and client ID are derived from the login response. Prompt and toolset
identifiers are generated per iteration, the application schema ID is selected from
the schema-list response, and all fixture buckets use the `public` code constant.

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

### Create a key with a role

This scenario uses `DIAL_CORE_API_KEY` from the project `.env` file; it does not perform UI authorization.

```bash
gradle gatlingRun --simulation=DebugSimulation \
  -DscenarioName=createKeyWithRole \
  -Dusers=1 \
  -DdialCoreBaseUrl=https://core-ai-dial-admin-frontend-pr-4096.gke.test.dial.parts/
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
