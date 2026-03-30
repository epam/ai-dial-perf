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

    public static ScenarioBuilder aiDialAdminCreateModelAPIScenario(int maxAttempts, int pauseDuration) {
        return scenario("AI Dial Admin - AD Auth + Create Model")
                .exec(aiDialAdminADAPIAuthChain())
                .exec(aiDialAdminCreateModelAPIChain(maxAttempts, pauseDuration));
    }

    public static ChainBuilder aiDialAdminADAPIAuthChain() {
        return exec(feed(csv(PropertiesHolder.aiAdminUsersFile).circular()))
                .exec(Requests.dialAdminADAPIAuth());
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
