package core;

import java.util.HashMap;
import java.util.Map;
import io.gatling.javaapi.core.Assertion;
import static io.gatling.javaapi.core.CoreDsl.*;

public final class Configs {

    private static final String JSON_CONTENT_TYPE = "application/json";
    private static final String ADMIN_BEARER_TOKEN = "Bearer #{dialAdminAccessToken}";

    private static final Map<String, String> JSON_HEADERS = Map.of(
        "Content-Type", JSON_CONTENT_TYPE,
        "Accept", JSON_CONTENT_TYPE
    );

    private static final Map<String, String> ADMIN_BEARER_HEADERS = Map.of(
        "Authorization", ADMIN_BEARER_TOKEN,
        "Content-Type", JSON_CONTENT_TYPE
    );

    private static final Map<String, String> ADMIN_BEARER_JSON_HEADERS = withHeaders(
        ADMIN_BEARER_HEADERS,
        "Accept", JSON_CONTENT_TYPE
    );

    private Configs() {
    }

    public static final Map<String, String> ACCEPT_TEXT_HEADERS = Map.of(
        "Accept", "text/x-component",
        "Content-Type", "text/plain;charset=UTF-8"
    );

    public static final Map<String, String> DIAL_ADMIN_API_HEADERS = withHeaders(
        ADMIN_BEARER_HEADERS,
        "If-Match", "*",
        "If-None-Match", "*"
    );

    // Multipart requests must let Gatling generate the Content-Type boundary.
    public static final Map<String, String> DIAL_ADMIN_MULTIPART_HEADERS = Map.of(
        "Authorization", ADMIN_BEARER_TOKEN
    );

    public static final Map<String, String> DIAL_ADMIN_IF_MATCH_HEADERS = withHeaders(
        ADMIN_BEARER_JSON_HEADERS,
        "If-Match", "*"
    );

    public static final Map<String, String> DIAL_ADMIN_IF_NONE_MATCH_HEADERS = withHeaders(
        ADMIN_BEARER_JSON_HEADERS,
        "If-None-Match", "*"
    );

    public static final Map<String, String> DIAL_CORE_CREATE_KEY_WITH_ROLE_HEADERS = withHeaders(
        ADMIN_BEARER_JSON_HEADERS,
        "If-None-Match", "*"
    );

    // Deployment-manager (MCP deploy service) headers. Bearer token is produced by the Auth0
    // auth chain (session key "dialAdminAccessToken"); mirrors scripts/DeployApp/api.py.
    public static final Map<String, String> MCP_DEPLOY_API_HEADERS = withHeaders(
        ADMIN_BEARER_HEADERS,
        "If-None-Match", "*"
    );

    public static final Map<String, String> DIAL_CORE_API_HEADERS = apiKeyHeaders(
        "#{DIAL_CORE_API_KEY}");

    public static final Map<String, String> DIAL_CORE_API_IF_MATCH_ANY_HEADERS = withHeaders(
        DIAL_CORE_API_HEADERS,
        "If-Match", "*"
    );

    // Second DIAL Core identity, used as the invitation receiver in the Sharing workflow.
    public static final Map<String, String> DIAL_CORE_API_HEADERS_2 = apiKeyHeaders(
        "#{DIAL_CORE_API_KEY_2}");

    public static final Map<String, String> DIAL_CORE_PER_REQUEST_API_HEADERS = apiKeyHeaders(
        PropertiesHolder.dialCorePerRequestApiKey);

    // Publication review operations require a Core administrator. The token is
    // populated in the Gatling session by the existing Admin UI authentication chain.
    public static final Map<String, String> DIAL_CORE_PUBLICATION_ADMIN_HEADERS = ADMIN_BEARER_JSON_HEADERS;

    public static final Map<String, String> AAD_BROWSER_HEADERS = Map.of(
        "Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language", "en-US,en;q=0.9",
        "Sec-Ch-Ua", "\"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"",
        "Content-Type", "application/x-www-form-urlencoded",
        "Sec-Ch-Ua-Mobile", "?0",
        "Sec-Ch-Ua-Platform", "\"macOS\"",
        "Upgrade-Insecure-Requests", "1"
    );

    public static final Assertion SUCCESS_RATE_LOAD_ASSERTION = global().successfulRequests().percent().gte(90.0);
    public static final Assertion SUCCESS_RATE_DEBUG_ASSERTION = global().successfulRequests().percent().gte(100.0);

    private static Map<String, String> apiKeyHeaders(String apiKey) {
        return withHeaders(JSON_HEADERS, "Api-Key", apiKey);
    }

    private static Map<String, String> withHeaders(Map<String, String> baseHeaders, String... headers) {
        if (headers.length % 2 != 0) {
            throw new IllegalArgumentException("Headers must be provided as name-value pairs");
        }

        Map<String, String> result = new HashMap<>(baseHeaders);
        for (int index = 0; index < headers.length; index += 2) {
            result.put(headers[index], headers[index + 1]);
        }
        return Map.copyOf(result);
    }
}
