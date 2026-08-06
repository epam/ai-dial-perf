package core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class PropertiesHolder {
    public static final Properties properties = loadDotEnv();
    public static final int duration = Utils.resolveDuration("duration", 600);
    public static final int durationRampUp = Utils.resolveDuration("durationRampUp", 20);
    public static final int durationRampDown = Utils.resolveDuration("durationRampDown", 20);
    public static final int users = intSystemProperty("users", 1);
    public static final int usersRampUpFrom = intSystemProperty("usersRampUpFrom", 0);
    public static final int usersRampDownTo = intSystemProperty("usersRampDownTo", 0);
    public static final String aiAdminBaseUrl = defaultIfBlank(
            resolveSystemThenEnv("aiAdminBaseUrl", "URL_ADMIN", "aiAdminBaseUrl"),
            "https://ai-dial-admin.example.com/");
    public static final String aiAdminUsersFile = stringSystemProperty("aiAdminUsersFile", "data/azure-users.csv");
    public static final String azureTenantId = stringSystemProperty("azureTenantId", "b41b72d0-4e9f-4c26-8a69-f949f367c91d");
    public static final String auth0Tenant = stringSystemProperty("auth0Tenant", "aidial");
    public static final String auth0Connection = stringSystemProperty("auth0Connection", "test-gke-dial");
    public static final String auth0AudienceProp = stringSystemProperty("auth0Audience", "test_gke_dial");
    public static final int modelSyncAttempts = intSystemProperty("modelSyncAttempts", 50);
    public static final int modelSyncPauseDuration = intSystemProperty("modelSyncPauseDuration", 5);
    public static final String modelEndpoint = stringSystemProperty("modelEndpoint", "https://api.openai.com/v1gpt-4-turbo/chat/completions/");
    public static final String scenarioName = stringSystemProperty("scenarioName", "aiDialAdminCreateModelAPI");
    public static final boolean openModel = Boolean.parseBoolean(System.getProperty("openModel", "true"));

    // DIAL Core admin (Api-Key authenticated) endpoints
    public static final String dialCoreBaseUrl = resolveSystemThenEnv("dialCoreBaseUrl", "DIAL_CORE_BASE_URL", "dialCoreBaseUrl");
    public static final String dialCoreApiKey = resolveSystemThenEnv("dialCoreApiKey", "DIAL_CORE_API_KEY", "dialCoreApiKey");
    // Per-request key issued by DIAL Core to a deployment. Falls back to dialCoreApiKey
    // for backwards compatibility with existing perRequestPermissions invocations.
    public static final String dialCorePerRequestApiKey = defaultIfBlank(
            resolveSystemThenEnv("dialCorePerRequestApiKey", "DIAL_CORE_PER_REQUEST_API_KEY", "dialCorePerRequestApiKey"),
            dialCoreApiKey);
    // Second identity (invitation receiver) for the Sharing workflow scenario.
    public static final String dialCoreApiKey2 = resolveSystemThenEnv("dialCoreApiKey2", "DIAL_CORE_API_KEY_2", "dialCoreApiKey2");
    // Receiving deployment id for the per-request-permissions scenario.
    public static final String shareReceiverDeployment = resolveSystemThenEnv("shareReceiverDeployment", "SHARE_RECEIVER_DEPLOYMENT", "shareReceiverDeployment");
    // Optional shortcut for Publications admin operations when UI authentication is unavailable.
    public static final String publicationAdminBearerToken = resolveSystemThenEnv(
            "publicationAdminBearerToken", "PUBLICATION_ADMIN_BEARER_TOKEN", "publicationAdminBearerToken");
    public static final String appName = resolveSystemThenEnv("appName", "APP_NAME", "appName");
    public static final String toolsetEndpoint = defaultIfBlank(
            resolveSystemThenEnv("toolsetEndpoint", "TOOLSET_ENDPOINT", "toolsetEndpoint"),
            "https://test.com/mcp");
    public static final String fileName = resolveSystemThenEnv(
            "fileName", "FILE_NAME", "fileName");

    // MCP deployment-manager (deploy service) workflow — mirrors scripts/DeployApp/run_mcp_container.py.
    // Base URL of the deployment-manager API (Python: URL_DEPLOY_SERVICE / url_depl).
    public static final String urlDeployService = defaultIfBlank(
            firstNonBlankProp("", "urlDeployService", "url_depl", "URL_DEPLOY_SERVICE"),
            resolveSystemThenEnv("urlDeployService", "URL_DEPLOY_SERVICE", "urlDeployService", "url_depl"));
    public static final String mcpDockerImage = stringSystemProperty("mcpDockerImage", "mcp/everything:latest");
    public static final int mcpBuildMaxAttempts = intSystemProperty("mcpBuildMaxAttempts", 18);
    public static final int mcpBuildPollDuration = intSystemProperty("mcpBuildPollDuration", 10);
    public static final int mcpStatusMaxAttempts = intSystemProperty("mcpStatusMaxAttempts", 28);
    public static final int mcpStatusPollDuration = intSystemProperty("mcpStatusPollDuration", 10);
    public static final boolean mcpCleanup = Boolean.parseBoolean(System.getProperty("mcpCleanup", "false"));
    // Shared independent execution probability for every request workflow in
    // mcpContainerMixedRequests. At 100, all six in-scope workflows run in each iteration.
    public static final double mixedRequestProbability = doubleSystemProperty("mixedRequestProbability", 100.0);

    public static final String NEXTAUTH_SECRET = resolveDotEnvFirst(
            "nextAuthSecret", "NEXTAUTH_SECRET", "nextAuthSecret");
    public static final String DIAL_ADMIN_AUTH0_DOMAIN = resolveDotEnvFirst(
            "dialAdminAuth0Domain", "DIAL_ADMIN_AUTH0_DOMAIN", "AUTH_0_DOMAIN", "AUTH0_DOMAIN");
    public static final String DIAL_ADMIN_CLIENT_ID = resolveDotEnvFirst(
            "dialAdminClientId", "DIAL_ADMIN_CLIENT_ID", "client_id", "clientId", "auth0ClientId");
    public static final String DIAL_ADMIN_AUTH0_CLIENT_INFO_CHALLENGE = resolveDotEnvFirst(
            "dialAdminAuth0ClientInfoChallenge", "DIAL_ADMIN_AUTH0_CLIENT_INFO_CHALLENGE",
            "AUTH0_CLIENT_INFO_CHALLENGE");
    public static final String DIAL_ADMIN_AUTH0_CLIENT_INFO_LOGIN = resolveDotEnvFirst(
            "dialAdminAuth0ClientInfoLogin", "DIAL_ADMIN_AUTH0_CLIENT_INFO_LOGIN",
            "AUTH0_CLIENT_INFO_LOGIN");

    /**
     * Resolves a config value by checking .env file keys, then a system property, then an env variable.
     */
    private static String resolveDotEnvFirst(String sysProp, String envVar, String... envFileKeys) {
        String value = trimToEmpty(properties.getProperty(envVar));
        if (value.isEmpty()) {
            value = firstNonBlank(properties, envFileKeys);
        }
        if (value.isEmpty()) {
            value = trimToEmpty(System.getProperty(sysProp));
        }
        if (value.isEmpty()) {
            value = trimToEmpty(System.getenv(envVar));
        }
        return value;
    }

    private static String resolveSystemThenEnv(String sysProp, String envVar, String... envFileKeys) {
        String value = trimToEmpty(System.getProperty(sysProp));
        if (value.isEmpty()) {
            value = trimToEmpty(System.getenv(envVar));
        }
        if (value.isEmpty()) {
            value = firstNonBlank(properties, envFileKeys);
        }
        return value.isEmpty() ? trimToEmpty(properties.getProperty(envVar)) : value;
    }

    private static String stringSystemProperty(String key, String defaultValue) {
        return System.getProperty(key, defaultValue);
    }

    private static int intSystemProperty(String key, int defaultValue) {
        return Integer.parseInt(System.getProperty(key, Integer.toString(defaultValue)));
    }

    private static double doubleSystemProperty(String key, double defaultValue) {
        double value = Double.parseDouble(System.getProperty(key, Double.toString(defaultValue)));
        if (value < 0.0 || value > 100.0) {
            throw new IllegalArgumentException(key + " must be between 0 and 100, got " + value);
        }
        return value;
    }

    /**
     * Returns the first non-blank {@code System.getProperty} value among {@code keys}, else {@code defaultValue}.
     * Mirrors the alias-resolution used by scripts/DeployApp/config.py (e.g. urlDeployService / url_depl).
     */
    private static String firstNonBlankProp(String defaultValue, String... keys) {
        for (String key : keys) {
            String value = trimToEmpty(System.getProperty(key));
            if (!value.isEmpty()) return value;
        }
        return defaultValue;
    }

    private static String trimToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value.isEmpty() ? defaultValue : value;
    }

    private static String firstNonBlank(Properties props, String... keys) {
        for (String key : keys) {
            String value = trimToEmpty(props.getProperty(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }

    private static Properties loadDotEnv() {
        Properties env = new Properties();
        Path envFile = Paths.get(".env");
        try {
            if (Files.exists(envFile)) {
                env.load(Files.newInputStream(envFile));
            }
        } catch (IOException e) {
            System.err.println("Error reading .env file: " + e.getMessage());
        }
        return env;
    }
}
