package builders;

import core.PropertiesHolder;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ScenarioBuilder;
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
