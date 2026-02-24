package builders;

import core.Configs;
import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import io.gatling.javaapi.http.HttpRequestActionBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static core.PropertiesHolder.aiAdminBaseUrl;
import static core.PropertiesHolder.azureTenantId;
import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class AzureADAuthenticationUIRequests {

    private static final String AZURE_LOGIN_HOST = "https://login.microsoftonline.com";
    private static final String DIAL_ADMIN_HOST = aiAdminBaseUrl.replaceAll("/$", "");
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    public static HttpRequestActionBuilder navigateToSignIn() {
        return http("Navigate to Application")
                .get("/")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .check(status().is(200))
                .check(regex("name=\"csrfToken\"[^>]*value=\"([^\"]+)\"").saveAs("csrfToken"));
    }

    public static HttpRequestActionBuilder initiateAzureADSignIn() {
        return http("Initiate Azure AD Sign In")
                .post("/api/auth/signin/azure-ad")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .header("Origin", DIAL_ADMIN_HOST)
                .header("Referer", DIAL_ADMIN_HOST + "/api/auth/signin/azure-ad?callbackUrl=%2F")
                .formParam("csrfToken", "#{csrfToken}")
                .formParam("callbackUrl", "/")
                .disableFollowRedirect()
                .check(status().in(301, 302, 303, 307, 308))
                .check(header("Location").saveAs("aadRedirectUrl"));
    }

    public static HttpRequestActionBuilder followAzureADRedirect() {
        return http("Follow Azure AD Redirect")
                .get("#{aadRedirectUrlWithSso}")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .header("Referer", DIAL_ADMIN_HOST + "/api/auth/signin/azure-ad?callbackUrl=%2F")
                .check(status().is(200))
                .check(azureAdTokenChecks());
    }

    public static HttpRequestActionBuilder getCredentialType() {
        return http("GetCredentialType")
                .post(AZURE_LOGIN_HOST + "/common/GetCredentialType")
                .queryParam("mkt", "en-US")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=UTF-8")
                .header("canary", "#{apiCanary}")
                .header("client-request-id", "#{correlationId}")
                .header("hpgact", "#{hpgact}")
                .header("hpgid", "#{hpgid}")
                .header("hpgrequestid", "#{hpgrequestid}")
                .header("Origin", AZURE_LOGIN_HOST)
                .header("Referer", "#{aadUrlPost}")
                .body(StringBody(AzureADAuthenticationUIRequests::buildCredentialTypePayload))
                .asJson()
                .check(jsonPath("$.FlowToken").saveAs("flowToken"));
    }

    public static HttpRequestActionBuilder submitPassword() {
        return http("Submit Password")
                .post(AZURE_LOGIN_HOST + "/" + azureTenantId + "/login")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .header("Origin", AZURE_LOGIN_HOST)
                .header("Referer", "#{aadUrlPostWithSso}")
                .formParam("i13", "0")
                .formParam("login", "#{username}")
                .formParam("loginfmt", "#{username}")
                .formParam("type", "11")
                .formParam("LoginOptions", "3")
                .formParam("lrt", "")
                .formParam("lrtPartition", "")
                .formParam("hisRegion", "")
                .formParam("hisScaleUnit", "")
                .formParam("passwd", "#{password}")
                .formParam("ps", "2")
                .formParam("psRNGCDefaultType", "")
                .formParam("psRNGCEntropy", "")
                .formParam("psRNGCSLK", "")
                .formParam("canary", "#{canary}")
                .formParam("ctx", "#{ctx}")
                .formParam("hpgrequestid", "#{hpgrequestid}")
                .formParam("flowToken", "#{flowToken}")
                .formParam("PPSX", "")
                .formParam("NewUser", "1")
                .formParam("FoundMSAs", "")
                .formParam("fspost", "0")
                .formParam("i21", "0")
                .formParam("CookieDisclosure", "0")
                .formParam("IsFidoSupported", "1")
                .formParam("isSignupPost", "0")
                .formParam("DfpArtifact", "")
                .formParam("i19", 37388)
                .check(bssoInterruptChecks());
    }

    public static HttpRequestActionBuilder bssoResubmit() {
        return http("BssoInterrupt Resubmit")
                .post("#{bssoFullUrl}")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .header("Origin", AZURE_LOGIN_HOST)
                .header("Referer", "#{aadUrlPostWithSso}")
                .formParam("i13", "0")
                .formParam("login", "#{username}")
                .formParam("loginfmt", "#{username}")
                .formParam("type", "11")
                .formParam("LoginOptions", "3")
                .formParam("lrt", "")
                .formParam("lrtPartition", "")
                .formParam("hisRegion", "")
                .formParam("hisScaleUnit", "")
                .formParam("passwd", "#{password}")
                .formParam("ps", "2")
                .formParam("psRNGCDefaultType", "")
                .formParam("psRNGCEntropy", "")
                .formParam("psRNGCSLK", "")
                .formParam("canary", "#{bssoCanary}")
                .formParam("ctx", "#{bssoCtx}")
                .formParam("hpgrequestid", "#{hpgrequestid}")
                .formParam("flowToken", "#{bssoFlowToken}")
                .formParam("PPSX", "")
                .formParam("NewUser", "1")
                .formParam("FoundMSAs", "")
                .formParam("fspost", "0")
                .formParam("i21", "0")
                .formParam("CookieDisclosure", "0")
                .formParam("IsFidoSupported", "1")
                .formParam("isSignupPost", "0")
                .formParam("DfpArtifact", "")
                .formParam("i19", "#{bssoI19}")
                .check(status().in(200, 301, 302, 303, 307, 308))
                .check(substring("AADSTS").notExists())
                .check(header("Location").optional().saveAs("aadLoginRedirect"));
    }

    public static HttpRequestActionBuilder oauthCallback() {
        return http("OAuth Callback")
                .get("#{aadLoginRedirect}")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .check(status().in(200, 301, 302, 303, 307, 308));
    }

    public static HttpRequestActionBuilder oauthCallbackManual() {
        return http("OAuth Callback (manual)")
                .get(DIAL_ADMIN_HOST + "/api/auth/callback/azure-ad")
                .queryParam("code", "#{aadCode}")
                .queryParam("state", "#{aadState}")
                .queryParam("session_state", "#{aadSessionState}")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .check(status().in(200, 301, 302, 303, 307, 308));
    }

    public static HttpRequestActionBuilder verifyAuthentication() {
        return http("Access Home Page")
                .get("/en/home")
                .headers(Configs.AAD_BROWSER_HEADERS)
                .check(substring("Access Management").exists());
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

    public static Session prepareRedirectUrls(Session session) {
        String redirectUrl = session.getString("aadRedirectUrl");
        return session
                .set("aadRedirectUrlWithSso", appendSsoReload(redirectUrl))
                .set("aadUrlPost", normalizeLoginUrl(toAadPath(redirectUrl)));
    }

    public static Session preparePasswordSubmitUrl(Session session) {
        return session.set("aadUrlPostWithSso",
                appendSsoReload(normalizeLoginUrl(session.getString("aadUrlPost"))));
    }

    public static Session prepareBssoUrl(Session session) {
        String urlPost = decodeJsUnicode(session.getString("bssoUrlPost"));
        return session.set("bssoFullUrl", AZURE_LOGIN_HOST + urlPost);
    }

    public static Session extractOAuthParams(Session session) {
        String location = session.getString("aadLoginRedirect");
        if (location != null && !location.isBlank()) {
            return session
                    .set("aadCode", extractQueryParam(location, "code"))
                    .set("aadState", extractQueryParam(location, "state"))
                    .set("aadSessionState", extractQueryParam(location, "session_state"));
        }
        return session;
    }

    // ---- Condition checks for doIf ----

    public static boolean isBssoInterrupt(Session session) {
        return hasValue(session, "isBssoInterrupt") && hasValue(session, "bssoUrlPost");
    }

    public static boolean hasLoginRedirect(Session session) {
        return hasValue(session, "aadLoginRedirect");
    }

    public static boolean needsManualCallback(Session session) {
        return !hasValue(session, "aadLoginRedirect")
                && hasValue(session, "aadCode")
                && hasValue(session, "aadState")
                && hasValue(session, "aadSessionState");
    }

    // ---- Private helpers ----

    private static CheckBuilder[] azureAdTokenChecks() {
        return new CheckBuilder[]{
                regex("\"sFT\":\"([^\"]+)\"").optional().saveAs("flowToken"),
                regex("\"sCtx\":\"([^\"]+)\"").optional().saveAs("ctx"),
                regex("\"i19\"\\s*:\\s*\"?(\\d+)\"?").optional().saveAs("i19"),
                regex("\"apiCanary\":\"([^\"]+)\"").optional().saveAs("apiCanary"),
                regex("\"canary\":\"([^\"]+)\"").optional().saveAs("canary"),
                regex("\"hpgact\":(\\d+)").optional().saveAs("hpgact"),
                regex("\"hpgid\":(\\d+)").optional().saveAs("hpgid"),
                regex("\"correlationId\":\"([^\"]+)\"").optional().saveAs("correlationId")
        };
    }

    private static CheckBuilder[] bssoInterruptChecks() {
        return new CheckBuilder[]{
                status().in(200, 301, 302, 303, 307, 308),
                substring("AADSTS").notExists(),
                header("Location").optional().saveAs("aadLoginRedirect"),
                regex("PageID\"\\s*content=\"BssoInterrupt\"").optional().saveAs("isBssoInterrupt"),
                regex("\"urlPost\":\"([^\"]+)\"").optional().saveAs("bssoUrlPost"),
                regex("\"flowToken\":\"([^\"]+)\"").optional().saveAs("bssoFlowToken"),
                regex("\"canary\":\"([^\"]+)\"").optional().saveAs("bssoCanary"),
                regex("\"ctx\":\"([^\"]+)\"").optional().saveAs("bssoCtx"),
                regex("\"i19\":\"?(\\d+)\"?").optional().saveAs("bssoI19")
        };
    }

    private static String buildCredentialTypePayload(Session session) {
        String username = session.getString("username");
        String flowToken = session.getString("flowToken");
        String ctx = session.getString("ctx");
        return """
            {
                "username": "%s",
                "isOtherIdpSupported": true,
                "checkPhones": false,
                "isRemoteNGCSupported": true,
                "isCookieBannerShown": false,
                "isFidoSupported": true,
                "originalRequest": "%s",
                "country": "UA",
                "forceotclogin": false,
                "isExternalFederationDisallowed": false,
                "isRemoteConnectSupported": false,
                "federationFlags": 0,
                "isSignup": false,
                "flowToken": "%s",
                "isAccessPassSupported": true,
                "isQrCodePinSupported": true
            }
            """.formatted(
                username != null ? username : "",
                ctx != null ? ctx : "",
                flowToken != null ? flowToken : ""
        );
    }

    private static boolean hasValue(Session session, String key) {
        try {
            String v = session.getString(key);
            return v != null && !v.isBlank();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String appendSsoReload(String url) {
        if (url == null || url.isEmpty()) return url;
        String base = url.contains("#") ? url.substring(0, url.indexOf('#')) : url;
        if (base.contains("sso_reload=")) return base;
        return base + (base.contains("?") ? "&" : "?") + "sso_reload=true";
    }

    private static String decodeJsUnicode(String s) {
        if (s == null || !s.contains("\\u")) return s;
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            if (i + 5 < s.length() && s.charAt(i) == '\\' && s.charAt(i + 1) == 'u') {
                try {
                    sb.append((char) Integer.parseInt(s.substring(i + 2, i + 6), 16));
                    i += 6;
                    continue;
                } catch (NumberFormatException ignored) { }
            }
            sb.append(s.charAt(i++));
        }
        return sb.toString();
    }

    private static String normalizeLoginUrl(String url) {
        if (url == null || url.isEmpty()) return url;
        String value = url.trim();
        String doubleHost = AZURE_LOGIN_HOST + AZURE_LOGIN_HOST;
        if (value.startsWith(doubleHost)) {
            value = value.substring(AZURE_LOGIN_HOST.length());
        }
        return value.startsWith("/") ? AZURE_LOGIN_HOST + value : value;
    }

    private static String toAadPath(String url) {
        if (url == null || url.isEmpty()) return url;
        String base = url.trim();
        if (base.startsWith("http://") || base.startsWith("https://")) {
            return normalizeLoginUrl(base);
        }
        return base.startsWith("/") ? AZURE_LOGIN_HOST + base : normalizeLoginUrl(base);
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
}
