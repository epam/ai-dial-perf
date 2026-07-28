package builders;

import core.Configs;
import core.PropertiesHolder;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gatling.javaapi.core.CoreDsl.*;

public class Scenarios {

    private static final Logger logger = LoggerFactory.getLogger(Scenarios.class);

    public static ChainBuilder aiDialAdminCreateModelAPIChain(int maxAttempts, int pauseDuration) {
        return exec(Requests.getAllModelsAPI())
        .exec(session -> session.set("modelName", "perf_model_" + System.currentTimeMillis()))
        .exec(Requests.createModelAPI("#{modelName}"))
       .exec(Requests.getModelDetailsAPI("#{modelName}"))
        .group("Sync Model State Total").on(
                exec(Requests.syncModelStateAPI("#{modelName}"))
                .exec(session -> {
                    logger.info("[Sync Model State - initial] Response: {}", session.getString("syncModelStateAPIResponseBody"));
                    return session.set("syncAttempts", 0).set("syncCreated", false);
                })
                .asLongAs(session ->
                        session.getInt("syncAttempts") < maxAttempts && !session.getBoolean("syncCreated")
                ).on(
                        exec(Requests.syncModelStateAPI("#{modelName}"))
                                .exec(session -> {
                                    logger.info("[Sync Model State - attempt {}] Response: {}", session.getInt("syncAttempts") + 1, session.getString("syncModelStateAPIResponseBody"));
                                    String status = session.getString("syncModelStatus");
                                    boolean created = status != null && status.equalsIgnoreCase("FULLY_SYNCED");
                                    int attempts = session.getInt("syncAttempts") + 1;
                                    return session
                                            .set("syncCreated", created)
                                            .set("syncAttempts", attempts);
                                })
                                .doIf(session -> !session.getBoolean("syncCreated") && session.getInt("syncAttempts") < maxAttempts)
                                .then(pause(pauseDuration)))
                .doIf(session -> !session.getBoolean("syncCreated"))
                .then(exec(session -> {
                    logger.warn("[Sync Model State] Model did not reach FULLY_SYNCED state after {} attempts", session.getInt("syncAttempts"));
                    return session.markAsFailed();
                })));
    }

    public static ChainBuilder aiDialAdminUIAuthChain() {
        return exec(feed(csv(PropertiesHolder.aiAdminUsersFile).circular()))
                .exec(session -> session.set("hpgrequestid", java.util.UUID.randomUUID().toString()))
                .exec(AzureADAuthenticationUIRequests.navigateToSignIn())
                .exec(AzureADAuthenticationUIRequests.initiateAzureADSignIn())
                .exec(AzureADAuthenticationUIRequests::prepareRedirectUrls)
                .exec(AzureADAuthenticationUIRequests.followAzureADRedirect())
                .exec(AzureADAuthenticationUIRequests.getCredentialType())
                .exec(AzureADAuthenticationUIRequests::preparePasswordSubmitUrl)
                .exec(AzureADAuthenticationUIRequests.submitPassword())
                .exec(AzureADAuthenticationUIRequests::extractOAuthParams)
                .doIf(AzureADAuthenticationUIRequests::isBssoInterrupt)
                .then(
                        exec(AzureADAuthenticationUIRequests::prepareBssoUrl)
                        .exec(AzureADAuthenticationUIRequests.bssoResubmit())
                        .exec(AzureADAuthenticationUIRequests::extractOAuthParams)
                )
                .doIf(AzureADAuthenticationUIRequests::hasLoginRedirect)
                .then(exec(AzureADAuthenticationUIRequests.oauthCallback()))
                .doIf(AzureADAuthenticationUIRequests::needsManualCallback)
                .then(exec(AzureADAuthenticationUIRequests.oauthCallbackManual()))
                .exec(AzureADAuthenticationUIRequests.verifyAuthentication());
    }

    public static ScenarioBuilder aiDialAdminAuth0CreateModelScenario(int maxAttempts, int pauseDuration) {
        return scenario("AI Dial Admin - Auth0 Auth + Create Model")
                .exec(aiDialAdminAuth0UIAuthChain())
                .exec(aiDialAdminCreateModelAPIChain(maxAttempts, pauseDuration));
    }

    public static ScenarioBuilder aiDialAdminAuth0UIAuthScenario() {
        return scenario("AI Dial Admin - Auth0 UI Auth")
                .exec(aiDialAdminAuth0UIAuthChain());
    }

    public static ChainBuilder aiDialApplicationRequestsChain() {
        String bucket = PropertiesHolder.appBucket;
        String appPath = PropertiesHolder.appName;

        return exec(Requests.mcpToolsList(bucket, appPath))
                .exec(Requests.getApplication(bucket, appPath))
                .exec(Requests.getApplicationTypeSchemas())
                .exec(Requests.getApplicationTypeSchema(PropertiesHolder.applicationSchemaId))
                .exec(Requests.updateApplicationMcp(bucket, appPath))
                .exec(Requests.getApplicationMetadata(bucket, appPath));
    }

    public static ScenarioBuilder aiDialApplicationRequestsScenario() {
        return scenario("Admin Application requests")
                .exec(aiDialApplicationRequestsChain());
    }

    public static ChainBuilder toolsetRequestsChain() {
        String bucket = PropertiesHolder.toolsetBucket;
        String toolsetName = PropertiesHolder.toolsetName;
        String toolsetPath = PropertiesHolder.toolsetPath;

        return exec(Requests.getBucket())
                .exec(Requests.updateToolset(bucket, toolsetPath, toolsetName))
                .exec(Requests.toolsetMcpToolsList(bucket, toolsetPath))
                .exec(Requests.getToolset(bucket, toolsetPath))
                .exec(Requests.getToolsetTools(bucket, toolsetPath))
                .exec(Requests.getToolsetAllowedTools(bucket, toolsetPath))
                .exec(Requests.getToolsetMetadata(bucket, toolsetPath))
                .exec(Requests.deleteToolset(bucket, toolsetPath));
    }

    public static ScenarioBuilder toolsetRequestsScenario() {
        return scenario("Toolset requests")
                .exec(toolsetRequestsChain());
    }
    
    public static ScenarioBuilder toolsetUpdateOnlyScenario() {
        return scenario("Toolset update only")
                .exec(Requests.updateToolset(
                        PropertiesHolder.toolsetBucket,
                        PropertiesHolder.toolsetPath,
                        PropertiesHolder.toolsetName));
    }

    public static ChainBuilder promptRequestsChain() {
        String bucket = PropertiesHolder.promptBucket;
        String promptName = PropertiesHolder.promptName;
        String displayName = PropertiesHolder.promptDisplayName;

        return exec(Requests.updatePrompt(bucket, promptName, displayName))
                .exec(Requests.getPrompt(bucket, promptName))
                .exec(Requests.getPromptMetadata(bucket, promptName))
                .exec(Requests.deletePrompt(bucket, promptName));
    }

    public static ScenarioBuilder promptRequestsScenario() {
        return scenario("Prompt requests")
                .exec(promptRequestsChain());
    }

    public static ChainBuilder fileRequestsChain() {
        String bucket = PropertiesHolder.fileBucket;
        String fileName = PropertiesHolder.fileName;
        String sourceUrl = "files/" + bucket + "/" + fileName;

        return exec(Requests.getFile(bucket, fileName))
                .exec(Requests.getFileMetadata(bucket, fileName))
                .exec(Requests.copyResource(sourceUrl, "files/" + bucket + "/sun2.jpg"))
                .exec(Requests.moveResource(sourceUrl, "files/" + bucket + "/new/" + fileName))
                .exec(Requests.deleteFile(bucket, fileName));
    }

    public static ScenarioBuilder fileRequestsScenario() {
        return scenario("File requests")
                .exec(fileRequestsChain());
    }

    public static ChainBuilder deploymentListingChain() {
        return exec(Requests.listDeployments())
                .exec(Requests.listOpenAiDeployments())
                .exec(Requests.getOpenAiDeployment())
                .exec(Requests.listOpenAiModels())
                .exec(Requests.getOpenAiModel())
                .exec(Requests.listOpenAiApplications())
                .exec(Requests.getOpenAiApplication())
                .exec(Requests.listOpenAiToolsets())
                .exec(Requests.getOpenAiToolset());
    }

    public static ScenarioBuilder deploymentListingScenario() {
        return scenario("Deployment listing")
                .exec(deploymentListingChain());
    }

    public static ChainBuilder sharingRequestsChain() {
        return exec(Requests.getBucket(Configs.DIAL_CORE_API_HEADERS, "ownerBucket"))
                .exec(session -> {
                    long ts = System.currentTimeMillis();
                    int rnd = ThreadLocalRandom.current().nextInt(1_000_000);
                    String base = "perf-share-" + ts + "-" + rnd;
                    String bucket = session.getString("ownerBucket");
                    return session
                            .set("shareResA", base + "-a")
                            .set("shareResB", base + "-b")
                            .set("shareUrlA", "prompts/" + bucket + "/" + base + "-a")
                            .set("shareUrlB", "prompts/" + bucket + "/" + base + "-b");
                })
                // owner (Api-Key #1) creates two private prompt resources to share
                .exec(Requests.createSharePrompt(Configs.DIAL_CORE_API_HEADERS, "#{ownerBucket}", "#{shareResA}"))
                .exec(Requests.createSharePrompt(Configs.DIAL_CORE_API_HEADERS, "#{ownerBucket}", "#{shareResB}"))
                // create an invitation link for resource A
                .exec(Requests.shareResource("Share - Create Invitation (A)", Configs.DIAL_CORE_API_HEADERS,
                        "#{shareUrlA}", "READ", "invitationLink"))
                // owner-side invitation views
                .exec(Requests.getInvitations(Configs.DIAL_CORE_API_HEADERS))
                .exec(Requests.getInvitation(Configs.DIAL_CORE_API_HEADERS, "#{invitationLink}"))
                // owner lists resources shared by them
                .exec(Requests.getSharedResources("Share - List (shared by me)", Configs.DIAL_CORE_API_HEADERS,
                        "\"PROMPT\"", "others"))
                // receiver (Api-Key #2) accepts the invitation and lists resources shared with them
                .doIf(session -> !PropertiesHolder.dialCoreApiKey2.isEmpty()).then(
                        exec(Requests.acceptInvitation(Configs.DIAL_CORE_API_HEADERS_2, "#{invitationLink}"))
                        .exec(Requests.getSharedResources("Share - List (shared with me)", Configs.DIAL_CORE_API_HEADERS_2,
                                "\"PROMPT\"", "me"))
                )
                // copy the access of resource A onto resource B
                .exec(Requests.copySharedResources(Configs.DIAL_CORE_API_HEADERS, "#{shareUrlA}", "#{shareUrlB}"))
                // receiver discards resource B shared with them
                .doIf(session -> !PropertiesHolder.dialCoreApiKey2.isEmpty()).then(
                        exec(Requests.discardSharedResources(Configs.DIAL_CORE_API_HEADERS_2, "#{shareUrlB}"))
                )
                // owner revokes all shared access to resource A
                .exec(Requests.revokeSharedResources(Configs.DIAL_CORE_API_HEADERS, "#{shareUrlA}"))
                // create a throwaway invitation on resource B, then delete it (covers deleteInvitation)
                .exec(Requests.shareResource("Share - Create Invitation (B)", Configs.DIAL_CORE_API_HEADERS,
                        "#{shareUrlB}", "READ", "invitationLinkB"))
                .exec(Requests.deleteInvitation(Configs.DIAL_CORE_API_HEADERS, "#{invitationLinkB}"))
                // cleanup created prompt resources
                .exec(Requests.deletePrompt("#{ownerBucket}", "#{shareResA}"))
                .exec(Requests.deletePrompt("#{ownerBucket}", "#{shareResB}"));
    }

    public static ScenarioBuilder sharingRequestsScenario() {
        return scenario("Sharing requests")
                .exec(sharingRequestsChain());
    }

    /**
     * Covers the per-request-permissions endpoints. NOTE: these operations are only
     * permitted with a per-request API key issued by DIAL Core to a deployment; a plain
     * Api-Key returns 403. Configure {@code dialCoreApiKey}/{@code shareReceiverDeployment}
     * accordingly (e.g. run behind a deployment) for a green result.
     */
    public static ChainBuilder perRequestPermissionsChain() {
        return exec(Requests.getBucket(Configs.DIAL_CORE_API_HEADERS, "ownerBucket"))
                .exec(session -> session.set("prpUrl",
                        "prompts/" + session.getString("ownerBucket") + "/perf-prp-" + System.currentTimeMillis()))
                .exec(Requests.grantPerRequestPermissions(Configs.DIAL_CORE_API_HEADERS,
                        "#{prpUrl}", "READ", PropertiesHolder.shareReceiverDeployment))
                .exec(Requests.getPerRequestPermissions(Configs.DIAL_CORE_API_HEADERS, "me"))
                .exec(Requests.revokePerRequestPermissions(Configs.DIAL_CORE_API_HEADERS,
                        "#{prpUrl}", "READ", PropertiesHolder.shareReceiverDeployment));
    }

    public static ScenarioBuilder perRequestPermissionsScenario() {
        return scenario("Per-request permissions")
                .exec(perRequestPermissionsChain());
    }

    public static ChainBuilder aiDialAdminAuth0UIAuthChain() {
        return exec(feed(csv(PropertiesHolder.aiAdminUsersFile).circular()))
                .exec(Auth0AuthenticationUIRequests.navigateToSignIn())
                .exec(Auth0AuthenticationUIRequests.initiateAuth0SignIn())
                .exec(Auth0AuthenticationUIRequests::extractAuth0LoginParams)
                .exec(Auth0AuthenticationUIRequests::prepareCsrfToken)
                .exec(Auth0AuthenticationUIRequests.usernamePasswordChallenge())
                .exec(Auth0AuthenticationUIRequests.usernamePasswordLogin())
                .exec(Auth0AuthenticationUIRequests.loginCallback());
    }

    /*
    ***************************************************************
    * MCP container deployment (precondition)
    * Port of scripts/DeployApp/run_mcp_container.py: create + build an MCP image,
    * then create + run a deployment, polling until the container is "running".
    *
    * Designed to be used as a PRECONDITION before other tests: it persists the
    * created resource ids into the Gatling session so downstream chains in the same
    * scenario can reuse them:
    *   #{mcpImageId}         the built image-definition id
    *   #{mcpDeploymentName}  the running deployment (container) name
    *   #{mcpContainerRunning} boolean, true once status == "running"
    *   #{mcpContainerUrl}    public MCP endpoint URL once the container is running
    ***************************************************************
    */

    private static final String MCP_BUILD_SUCCESSFUL = "BUILD_SUCCESSFUL";
    private static final String MCP_BUILD_FAILED = "BUILD_FAILED";
    private static final String MCP_STATUS_RUNNING = "running";

    /**
     * Latest Server-Sent-Events {@code data:} value from the build-status stream.
     * Mirrors {@code workflow._latest_sse_status}: scan lines bottom-up for {@code data:}.
     */
    private static String latestSseStatus(String body) {
        if (body == null) {
            return null;
        }
        String[] lines = body.split("\\R");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.startsWith("data:")) {
                return line.substring("data:".length()).trim();
            }
        }
        return null;
    }

    private static String mcpRandomName(String prefix) {
        return prefix + ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
    }

    private static String mcpContainerUrl(String deploymentName) {
        URI adminUri = URI.create(PropertiesHolder.aiAdminBaseUrl);
        String host = adminUri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("aiAdminBaseUrl must include a host");
        }

        String mcpHost = host.replaceFirst("^admin-", "").replaceFirst("\\.gke\\.", ".knative.gke.");
        return "%s://dm-%s.%s/mcp".formatted(adminUri.getScheme(), deploymentName, mcpHost);
    }

    /** MCP container precondition using the configured PropertiesHolder defaults. */
    public static ChainBuilder runMcpContainerChain() {
        return runMcpContainerChain(
                PropertiesHolder.mcpBuildMaxAttempts, PropertiesHolder.mcpBuildPollDuration,
                PropertiesHolder.mcpStatusMaxAttempts, PropertiesHolder.mcpStatusPollDuration,
                PropertiesHolder.mcpCleanup);
    }

    public static ChainBuilder runMcpContainerChain(int buildMaxAttempts, int buildPollDuration,
                                                    int statusMaxAttempts, int statusPollDuration,
                                                    boolean cleanup) {
        return exec(session -> session
                        .set("mcpImageNameReq", mcpRandomName("TestImage"))
                        .set("mcpDeploymentNameReq", mcpRandomName("testcontainername"))
                        // Initialize outcome flags so downstream guards / logging never hit a missing key
                        // when the precondition short-circuits (e.g. the deploy host is unreachable).
                        .set("mcpBuildDone", false)
                        .set("mcpContainerRunning", false))
                // 1) create the image definition + trigger a build, then poll until BUILD_SUCCESSFUL
                .exec(Requests.createMcpImage("#{mcpImageNameReq}"))
                // Only proceed to build/deploy if the image was actually created (short-circuit on failure).
                .doIf(session -> session.contains("mcpImageId")).then(
                        exec(Requests.buildMcpImage())
                        .group("MCP Image Build Total").on(
                        exec(session -> session
                                .set("mcpBuildAttempts", 0)
                                .set("mcpBuildDone", false)
                                .set("mcpBuildFailed", false))
                        .asLongAs(session -> session.getInt("mcpBuildAttempts") < buildMaxAttempts
                                && !session.getBoolean("mcpBuildDone")
                                && !session.getBoolean("mcpBuildFailed"))
                        .on(
                                exec(Requests.getMcpImageBuildStatus())
                                .exec(session -> {
                                    String status = latestSseStatus(session.getString("mcpBuildStatusBody"));
                                    int attempts = session.getInt("mcpBuildAttempts") + 1;
                                    logger.info("[MCP Image Build - attempt {}] status: {}", attempts, status);
                                    return session
                                            .set("mcpBuildDone", MCP_BUILD_SUCCESSFUL.equals(status))
                                            .set("mcpBuildFailed", MCP_BUILD_FAILED.equals(status))
                                            .set("mcpBuildAttempts", attempts);
                                })
                                .doIf(session -> !session.getBoolean("mcpBuildDone")
                                        && !session.getBoolean("mcpBuildFailed")
                                        && session.getInt("mcpBuildAttempts") < buildMaxAttempts)
                                .then(pause(buildPollDuration)))
                        .doIf(session -> !session.getBoolean("mcpBuildDone"))
                        .then(exec(session -> {
                            logger.warn("[MCP Image Build] image did not reach BUILD_SUCCESSFUL after {} attempts (failed={})",
                                    session.getInt("mcpBuildAttempts"), session.getBoolean("mcpBuildFailed"));
                            return session.markAsFailed();
                        })))
                // 2) create + run the deployment, poll until status == "running" (only if the build succeeded)
                .doIf(session -> session.getBoolean("mcpBuildDone")).then(
                        exec(Requests.createMcpDeployment("#{mcpDeploymentNameReq}"))
                        .exec(Requests.runMcpDeployment())
                        .group("MCP Deployment Run Total").on(
                                exec(session -> session
                                        .set("mcpStatusAttempts", 0)
                                        .set("mcpContainerRunning", false))
                                .asLongAs(session -> session.getInt("mcpStatusAttempts") < statusMaxAttempts
                                        && !session.getBoolean("mcpContainerRunning"))
                                .on(
                                        exec(Requests.getMcpDeploymentStatus())
                                        .exec(session -> {
                                            String status = session.getString("mcpDeploymentStatus");
                                            int attempts = session.getInt("mcpStatusAttempts") + 1;
                                            logger.info("[MCP Deployment Run - attempt {}] status: {}", attempts, status);
                                            return session
                                                    .set("mcpContainerRunning", MCP_STATUS_RUNNING.equalsIgnoreCase(status))
                                                    .set("mcpStatusAttempts", attempts);
                                        })
                                        .doIf(session -> !session.getBoolean("mcpContainerRunning")
                                                && session.getInt("mcpStatusAttempts") < statusMaxAttempts)
                                        .then(pause(statusPollDuration)))
                                .doIf(session -> !session.getBoolean("mcpContainerRunning"))
                                .then(exec(session -> {
                                    logger.warn("[MCP Deployment Run] container did not reach 'running' after {} attempts (last status: {})",
                                            session.getInt("mcpStatusAttempts"), session.getString("mcpDeploymentStatus"));
                                    return session.markAsFailed();
                                })))))
                .exec(session -> {
                    if (session.getBoolean("mcpContainerRunning")) {
                        String mcpUrl = mcpContainerUrl(session.getString("mcpDeploymentName"));
                        logger.info("PASSED: MCP container is running. Reusable session data -> mcpImageId='{}', mcpDeploymentName='{}', mcpContainerUrl='{}'",
                                session.getString("mcpImageId"), session.getString("mcpDeploymentName"), mcpUrl);
                        return session.set("mcpContainerUrl", mcpUrl);
                    }
                    return session;
                })
                // 3) optional best-effort cleanup (Python CLEANUP / --cleanup); off by default so the
                //    container stays up for the following tests to reuse.
                .doIf(session -> cleanup).then(
                        doIf(session -> session.contains("mcpDeploymentName"))
                                .then(exec(Requests.deleteMcpDeployment()))
                        .doIf(session -> session.contains("mcpImageId"))
                                .then(exec(Requests.deleteMcpImage())));
    }

    /**
     * Scenario that authenticates via Auth0 and then runs the MCP container precondition.
     * Compose additional chains after {@code runMcpContainerChain()} to reuse the running
     * container (via {@code #{mcpDeploymentName}}, {@code #{mcpImageId}}, and
     * {@code #{mcpContainerUrl}}) in later tests.
     */
    public static ScenarioBuilder runMcpContainerScenario() {
        return scenario("Run MCP Container (precondition)")
                .exec(aiDialAdminAuth0UIAuthChain())
                .exec(runMcpContainerChain());
    }
}
