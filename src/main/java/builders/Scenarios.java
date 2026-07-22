package builders;

import core.Configs;
import core.PropertiesHolder;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;

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
        String name = PropertiesHolder.toolsetPath;

        return exec(Requests.getBucket())
                .exec(Requests.updateToolset(bucket, toolsetName, name))
                .exec(Requests.toolsetMcpToolsList(bucket, toolsetName))
                .exec(Requests.getToolset(bucket, toolsetName))
                .exec(Requests.getToolsetTools(bucket, toolsetName))
                .exec(Requests.getToolsetAllowedTools(bucket, toolsetName))
                .exec(Requests.getToolsetMetadata(bucket, toolsetName))
                .exec(Requests.deleteToolset(bucket, toolsetName));
    }

    public static ScenarioBuilder toolsetRequestsScenario() {
        return scenario("Toolset requests")
                .exec(toolsetRequestsChain());
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

    public static ChainBuilder publicationRequestsChain() {
        return exec(Requests.getBucket(Configs.DIAL_CORE_API_HEADERS, "ownerBucket"))
                .exec(session -> {
                    long ts = System.currentTimeMillis();
                    int rnd = ThreadLocalRandom.current().nextInt(1_000_000);
                    String name = "perf-conv-" + ts + "-" + rnd;
                    String targetFolder = "perf-" + ts + "-" + rnd;
                    String bucket = session.getString("ownerBucket");
                    return session
                            .set("pubConvFolder", "my/folder")
                            .set("pubConvName", name)
                            .set("pubTargetFolder", "public/" + targetFolder + "/")
                            .set("pubSourceUrl", "conversations/" + bucket + "/my/folder/" + name)
                            .set("pubTargetUrl", "conversations/public/" + targetFolder + "/" + name);
                })
                // create a private conversation to publish
                .exec(Requests.createConversation(Configs.DIAL_CORE_API_HEADERS, "#{ownerBucket}", "#{pubConvFolder}", "#{pubConvName}"))
                // inspect the publication rules of a public folder
                .exec(Requests.getPublicationRules(Configs.DIAL_CORE_API_HEADERS, "public/"))
                // create a publish request (PENDING) and capture its url
                .exec(Requests.createPublication("Publication - Create", Configs.DIAL_CORE_API_HEADERS,
                        "Publication name", "#{pubTargetFolder}", "#{pubSourceUrl}", "#{pubTargetUrl}", "publicationUrl"))
                // list the user's publication requests and fetch the one just created (owner can read their own)
                .exec(Requests.getPublications(Configs.DIAL_CORE_API_HEADERS, "publications/#{ownerBucket}/"))
                .exec(Requests.getPublication(Configs.DIAL_CORE_API_HEADERS, "#{publicationUrl}"))
                // delete the PENDING publication request, then clean up the conversation
                .exec(Requests.deletePublication(Configs.DIAL_CORE_API_HEADERS, "#{publicationUrl}"))
                .exec(Requests.deleteConversation(Configs.DIAL_CORE_API_HEADERS, "#{ownerBucket}", "#{pubConvFolder}", "#{pubConvName}"));
    }

    public static ScenarioBuilder publicationRequestsScenario() {
        return scenario("Publication requests")
                .exec(publicationRequestsChain());
    }

    /**
     * Covers the admin-only publication endpoints (update/approve/reject). NOTE: these
     * require an admin-privileged DIAL Core Api-Key; a regular key returns 403. It creates
     * one publication to update+approve and another to reject, mirroring a real review flow.
     */
    public static ChainBuilder publicationAdminChain() {
        return exec(Requests.getBucket(Configs.DIAL_CORE_API_HEADERS, "ownerBucket"))
                .exec(session -> {
                    long ts = System.currentTimeMillis();
                    int rnd = ThreadLocalRandom.current().nextInt(1_000_000);
                    String bucket = session.getString("ownerBucket");
                    String nameA = "perf-conv-a-" + ts + "-" + rnd;
                    String nameB = "perf-conv-b-" + ts + "-" + rnd;
                    return session
                            .set("pubConvFolder", "my/folder")
                            .set("pubConvNameA", nameA)
                            .set("pubConvNameB", nameB)
                            .set("pubTargetFolderA", "public/perf-a-" + ts + "-" + rnd + "/")
                            .set("pubTargetFolderB", "public/perf-b-" + ts + "-" + rnd + "/")
                            .set("pubSourceUrlA", "conversations/" + bucket + "/my/folder/" + nameA)
                            .set("pubSourceUrlB", "conversations/" + bucket + "/my/folder/" + nameB)
                            .set("pubTargetUrlA", "conversations/public/perf-a-" + ts + "-" + rnd + "/" + nameA)
                            .set("pubTargetUrlB", "conversations/public/perf-b-" + ts + "-" + rnd + "/" + nameB);
                })
                .exec(Requests.createConversation(Configs.DIAL_CORE_API_HEADERS, "#{ownerBucket}", "#{pubConvFolder}", "#{pubConvNameA}"))
                .exec(Requests.createConversation(Configs.DIAL_CORE_API_HEADERS, "#{ownerBucket}", "#{pubConvFolder}", "#{pubConvNameB}"))
                // publication A: create -> update -> approve
                .exec(Requests.createPublication("Publication - Create (A)", Configs.DIAL_CORE_API_HEADERS,
                        "Publication name", "#{pubTargetFolderA}", "#{pubSourceUrlA}", "#{pubTargetUrlA}", "publicationUrlA"))
                .exec(Requests.updatePublication(Configs.DIAL_CORE_API_HEADERS, "#{publicationUrlA}",
                        "#{pubTargetFolderA}", "#{pubSourceUrlA}", "#{pubTargetUrlA}"))
                .exec(Requests.approvePublication(Configs.DIAL_CORE_API_HEADERS, "#{publicationUrlA}"))
                // publication B: create -> reject
                .exec(Requests.createPublication("Publication - Create (B)", Configs.DIAL_CORE_API_HEADERS,
                        "Publication name", "#{pubTargetFolderB}", "#{pubSourceUrlB}", "#{pubTargetUrlB}", "publicationUrlB"))
                .exec(Requests.rejectPublication(Configs.DIAL_CORE_API_HEADERS, "#{publicationUrlB}", "perf-test rejection"));
    }

    public static ScenarioBuilder publicationAdminScenario() {
        return scenario("Publication admin requests")
                .exec(publicationAdminChain());
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
}
