# AI Dial Admin Performance Testing Framework

Performance testing framework for [AI Dial Admin](https://github.com/epam/ai-dial-admin-backend) using Gatling.

## Scenarios

| Scenario | Description |
|---|---|
| `aiDialAdminCreateModelAPI` | Azure AD authentication + Create Model via API + Sync model state polling |

## Prerequisites

- JDK 17+
- Gradle 8.5+
- Azure AD credentials for Dial Admin users

## Environment Variables

Create a `.env` file or export the following:

```
DIAL_ADMIN_SCOPE=<Azure AD scope>
DIAL_ADMIN_CLIENT_ID=<Azure AD client ID>
DIAL_ADMIN_CLIENT_SECRET=<Azure AD client secret>
```

The `azure-users.csv` file should be placed at `src/main/resources/data/azure-users.csv` with columns: `username,password`.

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

## Configuration Parameters

| Parameter | Description | Default |
|---|---|---|
| `scenarioName` | Scenario to execute | `aiDialAdminCreateModelAPI` |
| `aiAdminBaseUrl` | AI Dial Admin base URL | `https://ai-dial-admin-test.imf-eid.projects.epam.com/` |
| `users` | Number of concurrent users | `10` |
| `duration` | Test duration (e.g. 300s, 10m, 1h) | `600s` |
| `durationRampUp` | Ramp-up duration | `20s` |
| `durationRampDown` | Ramp-down duration | `20s` |
| `openModel` | Use open injection model | `false` |
| `modelSyncAttempts` | Max sync polling attempts | `50` |
| `modelSyncPauseDuration` | Pause between sync polls (seconds) | `5` |
| `modelEndpoint` | Model endpoint URL for created models | `https://api.openai.com/v1gpt-4-turbo/chat/completions/` |
