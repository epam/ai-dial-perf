package core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class PropertiesHolder {
    public static final Properties properties = new Properties();
    public static final int duration = Utils.resolveDuration("duration", 600);
    public static final int durationRampUp = Utils.resolveDuration("durationRampUp", 20);
    public static final int durationRampDown = Utils.resolveDuration("durationRampDown", 20);
    public static final int users = System.getProperty("users") != null ? Integer.parseInt(System.getProperty("users")) : 1;
    public static final int usersRampUpFrom = System.getProperty("usersRampUpFrom") != null ? Integer.parseInt(System.getProperty("usersRampUpFrom")) : 0;
    public static final int usersRampDownTo = System.getProperty("usersRampDownTo") != null ? Integer.parseInt(System.getProperty("usersRampDownTo")) : 0;
    public static final String aiAdminBaseUrl = System.getProperty("aiAdminBaseUrl") != null ? System.getProperty("aiAdminBaseUrl") : "https://ai-dial-admin.example.com/";
    public static final String aiAdminUsersFile = System.getProperty("aiAdminUsersFile") != null ? System.getProperty("aiAdminUsersFile") : "data/azure-users.csv";
    public static final String azureTenantId = System.getProperty("azureTenantId") != null ? System.getProperty("azureTenantId") : "b41b72d0-4e9f-4c26-8a69-f949f367c91d";
    public static final String auth0Tenant = System.getProperty("auth0Tenant") != null ? System.getProperty("auth0Tenant") : "aidial";
    public static final String auth0Connection = System.getProperty("auth0Connection") != null ? System.getProperty("auth0Connection") : "test-gke-dial";
    public static final String auth0AudienceProp = System.getProperty("auth0Audience") != null ? System.getProperty("auth0Audience") : "test_gke_dial";
    public static final int modelSyncAttempts = System.getProperty("modelSyncAttempts") != null ? Integer.parseInt(System.getProperty("modelSyncAttempts")) : 50;
    public static final int modelSyncPauseDuration = System.getProperty("modelSyncPauseDuration") != null ? Integer.parseInt(System.getProperty("modelSyncPauseDuration")) : 5;
    public static final String modelEndpoint = System.getProperty("modelEndpoint") != null ? System.getProperty("modelEndpoint") : "https://api.openai.com/v1gpt-4-turbo/chat/completions/";
    public static final String scenarioName = System.getProperty("scenarioName") != null ? System.getProperty("scenarioName") : "aiDialAdminCreateModelAPI";
    public static final boolean openModel = Boolean.parseBoolean(System.getProperty("openModel", "true"));

    // DIAL Core admin (Api-Key authenticated) endpoints
    public static final String dialCoreBaseUrl = System.getProperty("dialCoreBaseUrl") != null ? System.getProperty("dialCoreBaseUrl") : "";
    public static final String dialCoreApiKey = System.getProperty("dialCoreApiKey") != null ? System.getProperty("dialCoreApiKey") : "";
    // Second identity (invitation receiver) for the Sharing workflow scenario.
    public static final String dialCoreApiKey2 = System.getProperty("dialCoreApiKey2") != null ? System.getProperty("dialCoreApiKey2") : "";
    // Receiving deployment id for the per-request-permissions scenario.
    public static final String shareReceiverDeployment = System.getProperty("shareReceiverDeployment") != null ? System.getProperty("shareReceiverDeployment") : "";
    public static final String appBucket = System.getProperty("appBucket") != null ? System.getProperty("appBucket") : "";
    public static final String appName = System.getProperty("appName") != null ? System.getProperty("appName") : "";
    public static final String applicationSchemaId = System.getProperty("applicationSchemaId") != null ? System.getProperty("applicationSchemaId") : "";
    public static final String toolsetBucket = System.getProperty("toolsetBucket") != null ? System.getProperty("toolsetBucket") : "";
    public static final String toolsetName = System.getProperty("toolsetName") != null ? System.getProperty("toolsetName") : "";
    public static final String toolsetPath = System.getProperty("toolsetPath") != null ? System.getProperty("toolsetPath") : "";
    public static final String toolsetEndpoint = System.getProperty("toolsetEndpoint") != null ? System.getProperty("toolsetEndpoint") : "";
    public static final String promptBucket = System.getProperty("promptBucket") != null ? System.getProperty("promptBucket") : "";
    public static final String promptName = System.getProperty("promptName") != null ? System.getProperty("promptName") : "";
    public static final String promptDisplayName = System.getProperty("promptDisplayName") != null ? System.getProperty("promptDisplayName") : "";
    public static final String fileBucket = System.getProperty("fileBucket") != null ? System.getProperty("fileBucket") : "";
    public static final String fileName = System.getProperty("fileName") != null ? System.getProperty("fileName") : "";

    // MCP deployment-manager (deploy service) workflow — mirrors scripts/DeployApp/run_mcp_container.py.
    // Base URL of the deployment-manager API (Python: URL_DEPLOY_SERVICE / url_depl).
    public static final String urlDeployService = firstNonBlankProp("", "urlDeployService", "url_depl", "URL_DEPLOY_SERVICE");
    public static final String mcpDockerImage = System.getProperty("mcpDockerImage") != null ? System.getProperty("mcpDockerImage") : "mcp/everything:latest";
    public static final int mcpBuildMaxAttempts = System.getProperty("mcpBuildMaxAttempts") != null ? Integer.parseInt(System.getProperty("mcpBuildMaxAttempts")) : 18;
    public static final int mcpBuildPollDuration = System.getProperty("mcpBuildPollDuration") != null ? Integer.parseInt(System.getProperty("mcpBuildPollDuration")) : 10;
    public static final int mcpStatusMaxAttempts = System.getProperty("mcpStatusMaxAttempts") != null ? Integer.parseInt(System.getProperty("mcpStatusMaxAttempts")) : 28;
    public static final int mcpStatusPollDuration = System.getProperty("mcpStatusPollDuration") != null ? Integer.parseInt(System.getProperty("mcpStatusPollDuration")) : 10;
    public static final boolean mcpCleanup = Boolean.parseBoolean(System.getProperty("mcpCleanup", "false"));

    public static final String DIAL_ADMIN_SCOPE;
    public static final String DIAL_ADMIN_AUTH0_DOMAIN;
    public static final String DIAL_ADMIN_CLIENT_ID;
    public static final String NEXTAUTH_SECRET;
    public static final String DIAL_ADMIN_AUTH0_CLIENT_INFO_CHALLENGE;
    public static final String DIAL_ADMIN_AUTH0_CLIENT_INFO_LOGIN;


    static {
        Properties env = new Properties();
        Path envFile = Paths.get(".env");
        try {
            if (Files.exists(envFile)) {
                env.load(Files.newInputStream(envFile));
            }
        } catch (IOException e) {
            System.err.println("Error reading .env file: " + e.getMessage());
        }

        NEXTAUTH_SECRET = resolve(env, "nextAuthSecret", "NEXTAUTH_SECRET", "NEXTAUTH_SECRET", "nextAuthSecret");
        DIAL_ADMIN_SCOPE = resolve(env, "dialAdminScope", "DIAL_ADMIN_SCOPE", "DIAL_ADMIN_SCOPE", "scope");
        DIAL_ADMIN_AUTH0_DOMAIN = resolve(env, "dialAdminAuth0Domain", "DIAL_ADMIN_AUTH0_DOMAIN", "DIAL_ADMIN_AUTH0_DOMAIN", "AUTH_0_DOMAIN", "AUTH0_DOMAIN");
        DIAL_ADMIN_CLIENT_ID = resolve(env, "dialAdminClientId", "DIAL_ADMIN_CLIENT_ID", "DIAL_ADMIN_CLIENT_ID", "client_id", "clientId", "auth0ClientId");
        DIAL_ADMIN_AUTH0_CLIENT_INFO_CHALLENGE = resolve(env, "dialAdminAuth0ClientInfoChallenge", "DIAL_ADMIN_AUTH0_CLIENT_INFO_CHALLENGE", "DIAL_ADMIN_AUTH0_CLIENT_INFO_CHALLENGE", "AUTH0_CLIENT_INFO_CHALLENGE");
        DIAL_ADMIN_AUTH0_CLIENT_INFO_LOGIN = resolve(env, "dialAdminAuth0ClientInfoLogin", "DIAL_ADMIN_AUTH0_CLIENT_INFO_LOGIN", "DIAL_ADMIN_AUTH0_CLIENT_INFO_LOGIN", "AUTH0_CLIENT_INFO_LOGIN");
    }

    /**
     * Resolves a config value by checking .env file keys, then a system property, then an env variable.
     */
    private static String resolve(Properties env, String sysProp, String envVar, String... envFileKeys) {
        String value = firstNonBlank(env, envFileKeys);
        if (value.isEmpty()) {
            value = trimToEmpty(System.getProperty(sysProp));
        }
        if (value.isEmpty()) {
            value = trimToEmpty(System.getenv(envVar));
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

    private static String firstNonBlank(Properties props, String... keys) {
        for (String key : keys) {
            String value = trimToEmpty(props.getProperty(key));
            if (!value.isEmpty()) return value;
        }
        return "";
    }
}
