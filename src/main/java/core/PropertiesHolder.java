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
    public static final String aiAdminBaseUrl = System.getProperty("aiAdminBaseUrl") != null ? System.getProperty("aiAdminBaseUrl") : "https://ai-dial-admin-test.imf-eid.projects.epam.com/";
    public static final String aiAdminUsersFile = System.getProperty("aiAdminUsersFile") != null ? System.getProperty("aiAdminUsersFile") : "data/azure-users.csv";
    public static final String azureTenantId = System.getProperty("azureTenantId") != null ? System.getProperty("azureTenantId") : "b41b72d0-4e9f-4c26-8a69-f949f367c91d";
    public static final int modelSyncAttempts = System.getProperty("modelSyncAttempts") != null ? Integer.parseInt(System.getProperty("modelSyncAttempts")) : 50;
    public static final int modelSyncPauseDuration = System.getProperty("modelSyncPauseDuration") != null ? Integer.parseInt(System.getProperty("modelSyncPauseDuration")) : 5;
    public static final String modelEndpoint = System.getProperty("modelEndpoint") != null ? System.getProperty("modelEndpoint") : "https://api.openai.com/v1gpt-4-turbo/chat/completions/";
    public static final String scenarioName = System.getProperty("scenarioName") != null ? System.getProperty("scenarioName") : "aiDialAdminCreateModelAPI";
    public static final boolean openModel = Boolean.parseBoolean(System.getProperty("openModel", "true"));

    public static final String DIAL_ADMIN_SCOPE;
    public static final String DIAL_ADMIN_CLIENT_ID;
    public static final String DIAL_ADMIN_CLIENT_SECRET;

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

        DIAL_ADMIN_SCOPE = resolve(env, "dialAdminScope", "DIAL_ADMIN_SCOPE", "DIAL_ADMIN_SCOPE", "scope");
        DIAL_ADMIN_CLIENT_ID = resolve(env, "dialAdminClientId", "DIAL_ADMIN_CLIENT_ID", "DIAL_ADMIN_CLIENT_ID", "client_id", "clientId");
        DIAL_ADMIN_CLIENT_SECRET = resolve(env, "dialAdminClientSecret", "DIAL_ADMIN_CLIENT_SECRET", "DIAL_ADMIN_CLIENT_SECRET", "client_secret", "azure_client_secret");
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
