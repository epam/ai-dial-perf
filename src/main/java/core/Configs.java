package core;

import java.util.Map;
import io.gatling.javaapi.core.Assertion;
import static io.gatling.javaapi.core.CoreDsl.*;

public class Configs {

    public static final Map<String, String> ACCEPT_TEXT_HEADERS = Map.of(
        "Accept", "text/x-component",
        "Content-Type", "text/plain;charset=UTF-8"
    );

    public static final Map<String, String> DIAL_ADMIN_API_HEADERS = Map.of(
        "Authorization", "Bearer #{dialAdminAccessToken}",
        "If-Match", "*",
        "If-None-Match","*",
        "Content-Type", "application/json"
    );

    public static final Map<String, String> DIAL_CORE_API_HEADERS = Map.of(
        "Api-Key", PropertiesHolder.dialCoreApiKey,
        "Content-Type", "application/json",
        "Accept", "application/json"
    );

    // Second DIAL Core identity, used as the invitation receiver in the Sharing workflow.
    public static final Map<String, String> DIAL_CORE_API_HEADERS_2 = Map.of(
        "Api-Key", PropertiesHolder.dialCoreApiKey2,
        "Content-Type", "application/json",
        "Accept", "application/json"
    );

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
}
