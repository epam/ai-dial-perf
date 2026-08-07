package builders;

import core.Configs;
import core.PropertiesHolder;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;

import java.net.URI;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gatling.javaapi.core.CoreDsl.*;

public class Scenarios {

    private static final Logger logger = LoggerFactory.getLogger(Scenarios.class);
    private static final String PUBLIC_BUCKET = "public";
    private static final String TOOLSET_VERSION = "0.0.1";
    private static final String DEFAULT_ROLE_NAME = "default";
    private static final AtomicReference<McpRunResources> MCP_RESOURCES = new AtomicReference<>();

    private record McpRunResources(String ownerApiKey, String receiverApiKey, String endpoint) {
    }

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
        String bucket = PUBLIC_BUCKET;
        String appPath = PropertiesHolder.appName;

        return exec(Requests.getApplication(bucket, appPath))
                .exec(Requests.mcpToolsList(bucket, appPath))
                .exec(Requests.getApplicationTypeSchemas())
                .exec(Requests.getApplicationTypeSchema("#{applicationSchemaId}"))
                .exec(Requests.updateApplicationMcp(bucket, appPath))
                .exec(Requests.getApplicationMetadata(bucket, appPath));
    }

    public static ScenarioBuilder aiDialApplicationRequestsScenario() {
        return scenario("Admin Application requests")
                .exec(aiDialApplicationRequestsChain());
    }

    public static ChainBuilder toolsetRequestsChain() {
        return exec(Scenarios::prepareToolsetSession)
                .exec(Requests.getBucket())
                .exec(Requests.updateToolset(PUBLIC_BUCKET, "#{toolsetPath}",
                        "#{toolsetName}", "#{toolsetVersion}"))
                .exec(Requests.toolsetMcpToolsList(PUBLIC_BUCKET, "#{toolsetPath}"))
                .exec(Requests.getToolset(PUBLIC_BUCKET, "#{toolsetPath}"))
                .exec(Requests.getToolsetTools(PUBLIC_BUCKET, "#{toolsetPath}"))
                .exec(Requests.getToolsetAllowedTools(PUBLIC_BUCKET, "#{toolsetPath}"))
                .exec(Requests.getToolsetMetadata(PUBLIC_BUCKET, "#{toolsetPath}"))
                .exec(Requests.deleteToolset(PUBLIC_BUCKET, "#{toolsetPath}"));
    }

    public static ScenarioBuilder toolsetRequestsScenario() {
        return scenario("Toolset requests")
                .exec(toolsetRequestsChain());
    }
    
    public static ScenarioBuilder toolsetUpdateOnlyScenario() {
        return scenario("Toolset update only")
                .exec(Scenarios::prepareToolsetSession)
                .exec(Requests.updateToolset(PUBLIC_BUCKET, "#{toolsetPath}",
                        "#{toolsetName}", "#{toolsetVersion}"));
    }

    public static ChainBuilder promptRequestsChain() {
        return exec(session -> {
                    String name = "perf-prompt-" + java.util.UUID.randomUUID();
                    return session
                            .set("promptName", name)
                            .set("promptDisplayName", "Performance prompt " + name);
                })
                .exec(Requests.updatePrompt(PUBLIC_BUCKET, "#{promptName}", "#{promptDisplayName}"))
                .exec(Requests.getPrompt(PUBLIC_BUCKET, "#{promptName}"))
                .exec(Requests.getPromptMetadata(PUBLIC_BUCKET, "#{promptName}"))
                .exec(Requests.deletePrompt(PUBLIC_BUCKET, "#{promptName}"));
    }

    public static ScenarioBuilder promptRequestsScenario() {
        return scenario("Prompt requests")
                .exec(promptRequestsChain());
    }

    public static ChainBuilder fileRequestsChain() {
        String bucket = PUBLIC_BUCKET;
        String fileName = PropertiesHolder.fileName;
        String sourceUrl = "files/" + bucket + "/" + fileName;

        return exec(session -> session.set("fileWorkName", "perf-" + java.util.UUID.randomUUID() + ".jpg"))
                .exec(Requests.getFile(bucket, fileName))
                .exec(Requests.getFileMetadata(bucket, fileName))
                .exec(Requests.copyResource(sourceUrl, "files/" + bucket + "/#{fileWorkName}"))
                .exec(Requests.moveResource("files/" + bucket + "/#{fileWorkName}",
                        "files/" + bucket + "/new/#{fileWorkName}"))
                .exec(Requests.deleteFile(bucket, "new/#{fileWorkName}"));
    }

    private static Session prepareToolsetSession(Session session) {
        String name = "perf-toolset-" + java.util.UUID.randomUUID();
        Session prepared = session
                .set("toolsetName", name)
                .set("toolsetPath", name + "__" + TOOLSET_VERSION)
                .set("toolsetVersion", TOOLSET_VERSION);
        if (!prepared.contains("toolsetEndpoint")
                || prepared.getString("toolsetEndpoint") == null
                || prepared.getString("toolsetEndpoint").isBlank()) {
            prepared = prepared.set("toolsetEndpoint", PropertiesHolder.toolsetEndpoint);
        }
        return prepared;
    }

    private static ChainBuilder createCoreApiKeysChain() {
        return exec(session -> prepareCoreApiKeyCreation(session, "owner"))
                .exec(Requests.createKeyWithRoleAPI())
                .exitHereIfFailed()
                .exec(session -> session.set("DIAL_CORE_API_KEY", session.getString("keyValue")))
                .exec(session -> prepareCoreApiKeyCreation(session, "receiver"))
                .exec(Requests.createKeyWithRoleAPI())
                .exitHereIfFailed()
                .exec(session -> session.set("DIAL_CORE_API_KEY_2", session.getString("keyValue")));
    }

    private static Session prepareCoreApiKeyCreation(Session session, String identity) {
        String suffix = java.util.UUID.randomUUID().toString();
        String apiKey = java.util.UUID.randomUUID().toString();
        return session
                .set("roleName", DEFAULT_ROLE_NAME)
                .set("keyName", "mcp-mixed-" + identity + "-" + suffix)
                .set("keyValue", apiKey)
                .set("project", "mcp-mixed-project-" + suffix)
                .set("displayName", "MCP Mixed " + identity + " " + suffix);
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
                // Cover all prompt permissions and the invitation acceptance limit.
                .exec(Requests.shareResource("Share - Create Invitation (A)", Configs.DIAL_CORE_API_HEADERS,
                        "#{shareUrlA}", "\"READ\", \"WRITE\", \"SHARE\"", 1, "invitationLink"))
                // owner-side invitation views
                .exec(Requests.getInvitations(Configs.DIAL_CORE_API_HEADERS))
                .exec(Requests.getInvitation(Configs.DIAL_CORE_API_HEADERS, "#{invitationLink}"))
                // owner lists resources shared by them
                .exec(Requests.getSharedResources("Share - List (shared by me)", Configs.DIAL_CORE_API_HEADERS,
                        "\"PROMPT\"", "others"))
                // receiver (Api-Key #2) accepts the invitation and lists resources shared with them
                .exec(Requests.getSharedResources("Share - List (shared with me)", Configs.DIAL_CORE_API_HEADERS_2,
                        "\"PROMPT\"", "me"))
                .exec(Requests.getSharedResources("Share - List (accepted, shared by me)", Configs.DIAL_CORE_API_HEADERS,
                        "\"PROMPT\"", "others"))
                // copy the access of resource A onto resource B
                .exec(Requests.copySharedResources(Configs.DIAL_CORE_API_HEADERS, "#{shareUrlA}", "#{shareUrlB}"))
                // receiver discards resource B shared with them
                .exec(Requests.getSharedResources("Share - List (after copy)", Configs.DIAL_CORE_API_HEADERS_2,
                        "\"PROMPT\"", "me"))
                .exec(Requests.discardSharedResources(Configs.DIAL_CORE_API_HEADERS_2, "#{shareUrlB}"))
                .exec(Requests.getSharedResources("Share - List (after discard)", Configs.DIAL_CORE_API_HEADERS_2,
                        "\"PROMPT\"", "me"))
                // owner revokes all shared access to resource A
                .exec(Requests.revokeSharedResources(Configs.DIAL_CORE_API_HEADERS, "#{shareUrlA}"))
                .exec(Requests.getSharedResources("Share - List (after revoke)", Configs.DIAL_CORE_API_HEADERS,
                        "\"PROMPT\"", "others"))
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
     * Api-Key returns 403. Configure {@code dialCorePerRequestApiKey} and
     * {@code shareReceiverDeployment} accordingly for a green result.
     */
    public static ChainBuilder perRequestPermissionsChain() {
        return exec(Requests.getBucket(Configs.DIAL_CORE_PER_REQUEST_API_HEADERS, "ownerBucket"))
                .exec(session -> {
                    String name = "perf-prp-" + System.currentTimeMillis() + "-"
                            + ThreadLocalRandom.current().nextInt(1_000_000);
                    return session
                            .set("prpResourceName", name)
                            .set("prpUrl", "prompts/" + session.getString("ownerBucket") + "/" + name);
                })
                // Grant permissions only for an existing resource, as in the Core API tests.
                .exec(Requests.createSharePrompt(Configs.DIAL_CORE_PER_REQUEST_API_HEADERS,
                        "#{ownerBucket}", "#{prpResourceName}"))
                .exec(Requests.grantPerRequestPermissions(Configs.DIAL_CORE_PER_REQUEST_API_HEADERS,
                        "#{prpUrl}", "\"READ\", \"WRITE\"", PropertiesHolder.shareReceiverDeployment))
                // The API has different response schemas for permissions granted by and to this deployment.
                .exec(Requests.getPerRequestPermissions(Configs.DIAL_CORE_PER_REQUEST_API_HEADERS, "others"))
                .exec(Requests.getPerRequestPermissions(Configs.DIAL_CORE_PER_REQUEST_API_HEADERS, "me"))
                .exec(Requests.revokePerRequestPermissions(Configs.DIAL_CORE_PER_REQUEST_API_HEADERS,
                        "#{prpUrl}", "\"READ\", \"WRITE\"", PropertiesHolder.shareReceiverDeployment))
                .exec(Requests.getPerRequestPermissions(Configs.DIAL_CORE_PER_REQUEST_API_HEADERS, "others"))
                .exec(Requests.deletePrompt(Configs.DIAL_CORE_PER_REQUEST_API_HEADERS,
                        "#{ownerBucket}", "#{prpResourceName}"));
    }

    public static ScenarioBuilder perRequestPermissionsScenario() {
        return scenario("Per-request permissions")
                .exec(perRequestPermissionsChain());
    }

    public static ChainBuilder publicationRequestsChain() {
        return exec(session -> PropertiesHolder.publicationAdminBearerToken.isEmpty()
                        ? session
                        : session.set("dialAdminAccessToken", PropertiesHolder.publicationAdminBearerToken))
                .doIf(session -> PropertiesHolder.publicationAdminBearerToken.isEmpty()
                        && (!session.contains("dialAdminAccessToken")
                        || session.getString("dialAdminAccessToken").isBlank())).then(
                        exec(aiDialAdminAuth0UIAuthChain())
                )
                .exitHereIfFailed()
                .exec(Requests.getPublicationBucket("publicationOwnerBucket"))
                .exitHereIfFailed()
                .exec(session -> {
                    String base = "perf-publication-" + System.currentTimeMillis() + "-"
                            + ThreadLocalRandom.current().nextInt(1_000_000);
                    String bucket = session.getString("publicationOwnerBucket");
                    String targetFolderName = base + "-public";
                    String promptName = base + "-prompt";
                    return session
                            .set("publicationName", base)
                            .set("publicationPromptName", promptName)
                            .set("publicationSourceUrl", "prompts/" + bucket + "/" + promptName)
                            .set("publicationTargetFolder", "public/" + targetFolderName + "/")
                            .set("publicationTargetPath", targetFolderName + "/" + promptName)
                            .set("publicationTargetUrl", "prompts/public/" + targetFolderName + "/" + promptName)
                            .set("publicationOwnerFolder", "publications/" + bucket + "/")
                            .set("publicationRejectFolder", "public/" + base + "-reject/")
                            .set("publicationDeleteFolder", "public/" + base + "-delete/");
                })
                // Publish workflow: create -> get/list -> admin update/approve -> inspect.
                .exec(Requests.createPublicationPrompt("#{publicationOwnerBucket}", "#{publicationPromptName}"))
                .exitHereIfFailed()
                .exec(Requests.createPublication("Publication - Create Publish Request",
                        "#{publicationName}", "#{publicationTargetFolder}", "#{publicationSourceUrl}",
                        "#{publicationTargetUrl}", "publicationUrl"))
                .exitHereIfFailed()
                .exec(Requests.getPublication(Configs.DIAL_CORE_API_HEADERS, "#{publicationUrl}"))
                .exec(Requests.listPublications("Publication - List (owner)", Configs.DIAL_CORE_API_HEADERS,
                        "#{publicationOwnerFolder}"))
                .exec(Requests.listPublications("Publication - List (admin pending)",
                        Configs.DIAL_CORE_PUBLICATION_ADMIN_HEADERS, "publications/public/"))
                .exec(Requests.updatePublication("#{publicationUrl}", "#{publicationName}-updated",
                        "#{publicationTargetFolder}", "#{publicationSourceUrl}", "#{publicationTargetUrl}"))
                .exec(Requests.approvePublication("Publication - Approve Publish Request", "#{publicationUrl}"))
                .exec(Requests.getPublicationRules(Configs.DIAL_CORE_PUBLICATION_ADMIN_HEADERS,
                        "#{publicationTargetFolder}"))
                .exec(Requests.listPublications("Publication - List (owner after approval)",
                        Configs.DIAL_CORE_API_HEADERS, "#{publicationOwnerFolder}"))
                .exec(Requests.getPublishedPrompt("#{publicationTargetPath}"))
                // Clean up the public copy through the documented unpublish workflow.
                .exec(Requests.createUnpublishPublication("#{publicationTargetFolder}",
                        "#{publicationTargetUrl}", "unpublishPublicationUrl"))
                .exitHereIfFailed()
                .exec(Requests.approvePublication("Publication - Approve Unpublish Request",
                        "#{unpublishPublicationUrl}"))
                .exec(Requests.deletePublicationPrompt("#{publicationOwnerBucket}", "#{publicationPromptName}"))
                // Separate terminal workflows cover reject and owner deletion of pending requests.
                .exec(Requests.createRulesOnlyPublication("Publication - Create Reject Request",
                        "#{publicationName}-reject", "#{publicationRejectFolder}", "rejectPublicationUrl"))
                .exitHereIfFailed()
                .exec(Requests.rejectPublication("#{rejectPublicationUrl}"))
                .exec(Requests.createRulesOnlyPublication("Publication - Create Delete Request",
                        "#{publicationName}-delete", "#{publicationDeleteFolder}", "deletePublicationUrl"))
                .exitHereIfFailed()
                .exec(Requests.deletePublication("#{deletePublicationUrl}"));
    }

    public static ScenarioBuilder publicationRequestsScenario() {
        return scenario("Publication requests")
                .exec(publicationRequestsChain());
    }

    public static ChainBuilder aiDialAdminAuth0UIAuthChain() {
        return exec(feed(csv(PropertiesHolder.aiAdminUsersFile).circular()))
                .exec(Auth0AuthenticationUIRequests.navigateToSignIn())
                .exec(Auth0AuthenticationUIRequests.initiateAuth0SignIn())
                .exec(Auth0AuthenticationUIRequests::extractAuth0LoginParams)
                .exec(Auth0AuthenticationUIRequests::prepareCsrfToken)
                .exec(Auth0AuthenticationUIRequests.usernamePasswordChallenge())
                .exitHereIfFailed()
                .tryMax(3).on(
                        exec(Auth0AuthenticationUIRequests.usernamePasswordLogin())
                                .doIf(Session::isFailed).then(pause(30))
                )
                .exitHereIfFailed()
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
                        // Polling can encounter transient transport failures before a later
                        // request confirms the deployment is running. Recover the session so
                        // the successful precondition can release downstream workflows.
                        return session.markAsSucceeded().set("mcpContainerUrl", mcpUrl);
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

    private static ChainBuilder requestChainWithProbability(String groupName, ChainBuilder requestChain) {
        double probability = PropertiesHolder.mixedRequestProbability;
        return group(groupName).on(
                doIf(session -> probability == 100.0
                        || ThreadLocalRandom.current().nextDouble(100.0) < probability)
                        // A failure in one independent workflow must not make a later
                        // selected workflow exit early through exitHereIfFailed.
                        .then(exec(session -> session.markAsSucceeded()).exec(requestChain)));
    }

    public static ScenarioBuilder adminCoreSystemSetupScenario() {
        return scenario("Admin Core System setup")
                .exec(session -> {
                    MCP_RESOURCES.set(null);
                    return session;
                })
                .exec(aiDialAdminAuth0UIAuthChain())
                .exec(createCoreApiKeysChain())
                .exitHereIfFailed()
                // Cleanup must stay disabled because the workload population reuses this container.
                .exec(runMcpContainerChain(
                        PropertiesHolder.mcpBuildMaxAttempts, PropertiesHolder.mcpBuildPollDuration,
                        PropertiesHolder.mcpStatusMaxAttempts, PropertiesHolder.mcpStatusPollDuration,
                        false))
                .exitHereIfFailed()
                .exec(session -> {
                    if (!session.getBoolean("mcpContainerRunning") || !session.contains("mcpContainerUrl")) {
                        logger.error("MCP precondition failed: no running container endpoint is available");
                        return session.markAsFailed();
                    }
                    McpRunResources resources = new McpRunResources(
                            session.getString("DIAL_CORE_API_KEY"),
                            session.getString("DIAL_CORE_API_KEY_2"),
                            session.getString("mcpContainerUrl"));
                    MCP_RESOURCES.set(resources);
                    logger.info("MCP setup completed; resources are ready for the workload population");
                    return session;
                })
                .exitHereIfFailed();
    }

    /**
     * Reuses the keys and MCP endpoint created once by {@link #adminCoreSystemSetupScenario()},
     * then independently executes each request workflow with the configured probability.
     */
    public static ScenarioBuilder adminCoreSystemScenario() {
        return scenario("Admin Core System")
                .exec(session -> {
                    McpRunResources resources = MCP_RESOURCES.get();
                    if (resources == null) {
                        logger.error("MCP workload cannot start because setup resources are unavailable");
                        return session.markAsFailed();
                    }
                    return session
                            .set("DIAL_CORE_API_KEY", resources.ownerApiKey())
                            .set("DIAL_CORE_API_KEY_2", resources.receiverApiKey())
                            .set("toolsetEndpoint", resources.endpoint());
                })
                .exitHereIfFailed()
                .exec(requestChainWithProbability("Mixed - Toolset requests", toolsetRequestsChain()))
                .exec(requestChainWithProbability("Mixed - Prompt requests", promptRequestsChain()))
                .exec(requestChainWithProbability("Mixed - File requests", fileRequestsChain()))
                .exec(requestChainWithProbability("Mixed - Deployment listing", deploymentListingChain()))
                .exec(requestChainWithProbability("Mixed - Sharing requests", sharingRequestsChain()))
                .exec(requestChainWithProbability("Mixed - Publication requests", publicationRequestsChain()));
    }
}
