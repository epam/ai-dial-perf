package builders;

import core.Configs;
import core.NextAuthSessionDecoder;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static core.PropertiesHolder.*;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class Auth0AuthenticationUIRequests {

    private static final Logger log = LoggerFactory.getLogger(Auth0AuthenticationUIRequests.class);

    private static final String AUTH0_HOST = "https://" + DIAL_ADMIN_AUTH0_DOMAIN;
    private static final String DIAL_ADMIN_HOST = aiAdminBaseUrl.replaceAll("/$", "");
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36";

    private static final Pattern CSRF_PATTERN = Pattern.compile("\"_csrf\"\\s*:\\s*\"([^\"]+)\"");

    public static HttpRequestActionBuilder navigateToSignIn() {
        return http("Navigate to Application")
                .get("/")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .check(status().is(200))
                .check(regex("name=\"csrfToken\"[^>]*value=\"([^\"]+)\"").saveAs("csrfToken"));
    }

    public static HttpRequestActionBuilder initiateAuth0SignIn() {
        return http("Initiate Auth0 Sign In")
                .post("/api/auth/signin/auth0")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .header("Origin", DIAL_ADMIN_HOST)
                .header("Referer", DIAL_ADMIN_HOST + "/api/auth/signin?callbackUrl=%2F")
                .formParam("csrfToken", "#{csrfToken}")
                .formParam("callbackUrl", "/")
                .check(status().is(200))
                .check(currentLocation().saveAs("auth0LoginPageUrl"))
                .check(auth0LoginPageChecks());
    }

    public static HttpRequestActionBuilder usernamePasswordChallenge() {
        return http("Username Password Challenge")
                .post("#{auth0Host}/usernamepassword/challenge")
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .header("auth0-client", DIAL_ADMIN_AUTH0_CLIENT_INFO_CHALLENGE)
                .header("Origin", "#{auth0Host}")
                .header("Referer", "#{auth0LoginPageUrl}")
                .body(StringBody(Auth0AuthenticationUIRequests::buildChallengePayload))
                .asJson()
                .check(status().is(200));
    }

    public static HttpRequestActionBuilder usernamePasswordLogin() {
        return http("Username Password Login")
                .post("#{auth0Host}/usernamepassword/login")
                .header("Accept", "*/*")
                .header("Content-Type", "application/json")
                .header("auth0-client", DIAL_ADMIN_AUTH0_CLIENT_INFO_LOGIN)
                .header("Origin", "#{auth0Host}")
                .header("Referer", "#{auth0LoginPageUrl}")
                .body(StringBody(Auth0AuthenticationUIRequests::buildLoginPayload))
                .asJson()
                .check(status().is(200))
                .check(loginFormChecks());
    }

    public static ChainBuilder loginCallback() {
        return exec(
                        http("Auth0 Login Callback")
                                .post("#{auth0Host}/login/callback")
                                .headers(Configs.AAD_BROWSER_HEADERS)
                                .header("Origin", "#{auth0Host}")
                                .header("Referer", "#{auth0LoginPageUrl}")
                                .formParam("wa", "#{auth0Wa}")
                                .formParam("wresult", "#{auth0Wresult}")
                                .formParam("wctx", "#{auth0Wctx}")
                                .transformResponse(NextAuthSessionDecoder::injectDecodedSessionIntoResponse)
                                .check(header(NextAuthSessionDecoder.SYNTH_JWE_HEADER).find(0)
                                        .transform(NextAuthSessionDecoder::decodeB64Utf8)
                                        .optional()
                                        .saveAs(NextAuthSessionDecoder.ATTR_SESSION_JWE))
                                .check(header(NextAuthSessionDecoder.SYNTH_JSON_HEADER).find(0)
                                        .transform(NextAuthSessionDecoder::decodeB64Utf8)
                                        .optional()
                                        .saveAs(NextAuthSessionDecoder.ATTR_SESSION_JSON)))
                .exec(Auth0AuthenticationUIRequests::saveDialAdminAccessTokenFromDecodedNextAuthJson);
    }

    public static HttpProtocolBuilder httpProtocol() {
        return http.baseUrl(DIAL_ADMIN_HOST)
                .acceptHeader("*/*")
                .acceptEncodingHeader("gzip, deflate, br")
                .acceptLanguageHeader("en-US,en;q=0.9")
                .userAgentHeader(USER_AGENT)
                .disableCaching()
                .silentResources()
                .maxRedirects(10);
    }

    // ---- Session helpers ----

    public static Session saveDialAdminAccessTokenFromDecodedNextAuthJson(Session session) {
        String jwe = getSessionValue(session, NextAuthSessionDecoder.ATTR_SESSION_JWE);
        String json = getSessionValue(session, NextAuthSessionDecoder.ATTR_SESSION_JSON);
        String token = NextAuthSessionDecoder.extractAccessTokenFromDecodedSession(json != null ? json : "");
        session = session.set("dialAdminAccessToken", token);
        boolean hasJwe = jwe != null && !jwe.isBlank();
        boolean hasJson = json != null && !json.isBlank();
        if (hasJwe && !hasJson) {
            log.warn(
                    "NextAuth JWE is in session but decrypted JSON is missing; dialAdminAccessToken empty. Check NEXTAUTH_SECRET and NextAuthSessionDecoder logs.");
        } else if (hasJson && token.isBlank()) {
            log.warn("Decrypted NextAuth session has no access_token or accessToken field; dialAdminAccessToken is empty.");
        }
        return session;
    }

    public static Session extractAuth0LoginParams(Session session) {
        String loginPageUrl = session.getString("auth0LoginPageUrl");
        String auth0Host = extractOrigin(loginPageUrl);
        if (auth0Host == null || auth0Host.isBlank()) {
            auth0Host = AUTH0_HOST;
            log.warn("Could not derive Auth0 host from '{}'; falling back to configured domain '{}'", loginPageUrl, AUTH0_HOST);
        }
        session = session.set("auth0Host", auth0Host);

        String state = extractQueryParam(loginPageUrl, "state");
        String redirectUri = extractQueryParam(loginPageUrl, "redirect_uri");
        String audience = extractQueryParam(loginPageUrl, "audience");
        String codeChallenge = extractQueryParam(loginPageUrl, "code_challenge");
        String codeChallengeMethod = extractQueryParam(loginPageUrl, "code_challenge_method");
        String scope = extractQueryParam(loginPageUrl, "scope");

        if (state != null) session = session.set("auth0State", state);
        if (redirectUri != null) session = session.set("auth0RedirectUri", redirectUri);
        if (audience != null) session = session.set("auth0Audience", audience);
        if (codeChallenge != null) session = session.set("auth0CodeChallenge", codeChallenge);
        if (codeChallengeMethod != null) session = session.set("auth0CodeChallengeMethod", codeChallengeMethod);
        if (scope != null) session = session.set("auth0Scope", scope);

        return session;
    }

    public static Session prepareCsrfToken(Session session) {
        if (hasValue(session, "auth0Csrf")) return session;
        if (hasValue(session, "auth0ConfigBase64")) {
            String csrf = extractCsrfFromBase64Config(session.getString("auth0ConfigBase64"));
            if (csrf != null && !csrf.isBlank()) {
                return session.set("auth0Csrf", csrf);
            }
        }
        return session;
    }

    // ---- Private helpers ----

    private static CheckBuilder[] auth0LoginPageChecks() {
        return new CheckBuilder[]{
                regex("data-config=\"([^\"]+)\"").optional().saveAs("auth0ConfigBase64"),
                regex("\"_csrf\"\\s*:\\s*\"([^\"]+)\"").optional().saveAs("auth0Csrf"),
                regex("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").optional().saveAs("auth0CsrfInput"),
        };
    }

    private static CheckBuilder[] loginFormChecks() {
        return new CheckBuilder[]{
                css("input[name='wa']", "value").saveAs("auth0Wa"),
                css("input[name='wresult']", "value").saveAs("auth0Wresult"),
                css("input[name='wctx']", "value").saveAs("auth0Wctx"),
        };
    }

    private static String buildChallengePayload(Session session) {
        String state = session.getString("auth0State");
        return "{\"state\":\"%s\"}".formatted(jsonEscape(state));
    }

    private static String buildLoginPayload(Session session) {
        String redirectUri = getSessionOrDefault(session, "auth0RedirectUri",
                DIAL_ADMIN_HOST + "/api/auth/callback/auth0");
        String scope = getSessionOrDefault(session, "auth0Scope",
                "openid email profile offline_access");
        String state = session.getString("auth0State");
        String username = session.getString("username");
        String password = session.getString("password");
        String csrf = resolveAuth0Csrf(session);
        String audience = getSessionOrDefault(session, "auth0Audience", auth0AudienceProp);
        String codeChallenge = getSessionOrDefault(session, "auth0CodeChallenge", "");
        String codeChallengeMethod = getSessionOrDefault(session, "auth0CodeChallengeMethod", "S256");

        return """
            {
                "client_id": "%s",
                "redirect_uri": "%s",
                "tenant": "%s",
                "response_type": "code",
                "scope": "%s",
                "state": "%s",
                "connection": "%s",
                "username": "%s",
                "password": "%s",
                "popup_options": {},
                "sso": true,
                "_intstate": "deprecated",
                "_csrf": "%s",
                "audience": "%s",
                "code_challenge_method": "%s",
                "code_challenge": "%s",
                "protocol": "oauth2"
            }
            """.formatted(
                jsonEscape(DIAL_ADMIN_CLIENT_ID),
                jsonEscape(redirectUri),
                jsonEscape(auth0Tenant),
                jsonEscape(scope),
                jsonEscape(state),
                jsonEscape(auth0Connection),
                jsonEscape(username),
                jsonEscape(password),
                jsonEscape(csrf),
                jsonEscape(audience),
                jsonEscape(codeChallengeMethod),
                jsonEscape(codeChallenge)
        );
    }

    private static String resolveAuth0Csrf(Session session) {
        String csrf = getSessionValue(session, "auth0Csrf");
        if (csrf != null) return csrf;
        csrf = getSessionValue(session, "auth0CsrfInput");
        if (csrf != null) return csrf;
        String configBase64 = getSessionValue(session, "auth0ConfigBase64");
        if (configBase64 != null) {
            return extractCsrfFromBase64Config(configBase64);
        }
        return "";
    }

    private static String extractCsrfFromBase64Config(String base64Config) {
        try {
            String decoded = new String(Base64.getDecoder().decode(base64Config), StandardCharsets.UTF_8);
            Matcher m = CSRF_PATTERN.matcher(decoded);
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getSessionValue(Session session, String key) {
        try {
            String v = session.getString(key);
            return (v != null && !v.isBlank()) ? v : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getSessionOrDefault(Session session, String key, String defaultValue) {
        String value = getSessionValue(session, key);
        return value != null ? value : (defaultValue != null ? defaultValue : "");
    }

    private static boolean hasValue(Session session, String key) {
        return getSessionValue(session, key) != null;
    }

    private static String extractOrigin(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) return null;
            String origin = scheme + "://" + host;
            if (uri.getPort() != -1) origin += ":" + uri.getPort();
            return origin;
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractQueryParam(String url, String paramName) {
        if (url == null || paramName == null) return null;
        int queryStart = url.indexOf('?');
        if (queryStart < 0 || queryStart == url.length() - 1) return null;
        for (String part : url.substring(queryStart + 1).split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(paramName)) {
                return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /**
     * Escapes a string for safe interpolation inside a JSON double-quoted value.
     * Returns an empty string for {@code null} input.
     */
    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
