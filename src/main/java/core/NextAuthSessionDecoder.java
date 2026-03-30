package core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gatling.http.cookie.CookieSupport;
import io.gatling.http.client.uri.Uri;
import io.gatling.http.response.Response;
import io.gatling.javaapi.core.Session;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.cookie.Cookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.Iterator;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * NextAuth.js <code>__Secure-next-auth.session-token</code>: read the JWE from {@code Set-Cookie} or Gatling’s cookie
 * jar, derive the AES key with the same HKDF as NextAuth / jwcrypto, decrypt ({@code dir} + {@code A256GCM}).
 */
public final class NextAuthSessionDecoder {

    private static final Logger log = LoggerFactory.getLogger(NextAuthSessionDecoder.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final byte[] HKDF_INFO = "NextAuth.js Generated Encryption Key".getBytes(StandardCharsets.UTF_8);

    private static final String SESSION_COOKIE_PREFIX = "__Secure-next-auth.session-token";
    private static final String SESSION_COOKIE_PREFIX_DOT = SESSION_COOKIE_PREFIX + ".";

    private static final String[] ACCESS_TOKEN_KEYS = {"access_token", "accessToken"};

    /** Lazily cached AES-256 key derived via HKDF from the static NEXTAUTH_SECRET. */
    private static volatile byte[] cachedEncryptionKey;
    private static volatile String cachedEncryptionKeySecret;

    public static final String SYNTH_JWE_HEADER = "X-Gatling-NextAuth-Jwe";
    public static final String SYNTH_JSON_HEADER = "X-Gatling-NextAuth-Json";

    public static final String ATTR_SESSION_JWE = "nextAuthSessionTokenJwe";
    public static final String ATTR_SESSION_JSON = "nextAuthDecodedSessionJson";

    private NextAuthSessionDecoder() {
    }

    public static String decryptSessionJsonFromJwe(String compactJwe, String secret) {
        if (isBlank(compactJwe) || isBlank(secret)) {
            return "";
        }
        try {
            return decryptJwe(compactJwe.trim(), getOrDeriveEncryptionKey(secret));
        } catch (Exception e) {
            log.warn("NextAuth JWE decrypt failed: {}", e.getMessage());
            return "";
        }
    }

    public static String decryptSessionJsonFromJwe(String compactJwe) {
        return decryptSessionJsonFromJwe(compactJwe, PropertiesHolder.NEXTAUTH_SECRET);
    }


    public static Response injectDecodedSessionIntoResponse(Response response, Session session) {
        String origin = stripTrailingSlash(PropertiesHolder.aiAdminBaseUrl);
        String jwe = extractJweFromSetCookieHeaders(setCookieHeaderLines(response));
        if (jwe.isEmpty()) {
            jwe = extractJweFromCookieJar(session, origin);
        }

        HttpHeaders newHeaders = copyHeaders(response.headers());

        if (jwe.isEmpty()) {
            // One log line per invocation: N virtual users ⇒ N messages; redirect hops may also lack cookie until final URL.
            int status = response.status().code();
            if (status >= 300 && status < 400) {
                log.debug("No __Secure-next-auth.session-token on {} redirect (cookie often appears on a later hop or in jar after follow)", status);
            } else {
                log.warn("No __Secure-next-auth.session-token in this response or cookie jar");
            }
        } else {
            newHeaders.add(SYNTH_JWE_HEADER, Base64.getEncoder().encodeToString(jwe.getBytes(StandardCharsets.UTF_8)));
        }

        String secret = PropertiesHolder.NEXTAUTH_SECRET;
        if (!jwe.isEmpty() && !isBlank(secret)) {
            String json = decryptSessionJsonFromJwe(jwe, secret);
            if (!json.isEmpty()) {
                newHeaders.add(
                        SYNTH_JSON_HEADER,
                        Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
            }
        } else if (!jwe.isEmpty()) {
            log.warn("nextAuthSecret is not set; JWE header added but session JSON not decrypted");
        }

        return response.copy(
                response.request(),
                response.startTimestamp(),
                response.endTimestamp(),
                response.status(),
                newHeaders,
                response.body(),
                response.checksums(),
                response.isHttp2());
    }

    /** Base64 (standard) UTF-8 string — used for Gatling checks on synthetic headers. */
    public static String decodeB64Utf8(String base64) {
        if (isBlank(base64)) {
            return "";
        }
        try {
            return new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid base64 for NextAuth synthetic header: {}", e.getMessage());
            return "";
        }
    }

    /** First present string field among OAuth / NextAuth shapes. */
    public static String extractAccessTokenFromDecodedSession(String decryptedSessionJson) {
        if (isBlank(decryptedSessionJson)) {
            return "";
        }
        try {
            JsonNode root = JSON.readTree(decryptedSessionJson);
            for (String key : ACCESS_TOKEN_KEYS) {
                JsonNode n = root.get(key);
                if (n != null && n.isTextual()) {
                    return n.asText();
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse access_token from decoded session: {}", e.getMessage());
        }
        return "";
    }

    static List<String> setCookieHeaderLines(Response response) {
        scala.collection.immutable.Seq<String> seq = response.headers("Set-Cookie");
        ArrayList<String> lines = new ArrayList<>();
        Iterator<String> it = seq.iterator();
        while (it.hasNext()) {
            lines.add(it.next());
        }
        return lines;
    }

    static HttpHeaders copyHeaders(HttpHeaders original) {
        DefaultHttpHeaders copy = new DefaultHttpHeaders();
        copy.add(original);
        return copy;
    }

    static String extractJweFromSetCookieHeaders(List<String> setCookieHeaderValues) {
        List<Chunk> chunks = new ArrayList<>();
        for (String line : setCookieHeaderValues) {
            if (isBlank(line)) {
                continue;
            }
            // Use indexOf instead of split to avoid array allocation
            int semi = line.indexOf(';');
            String firstPair = (semi >= 0 ? line.substring(0, semi) : line).trim();
            int eq = firstPair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = firstPair.substring(0, eq).trim();
            String rawValue = firstPair.substring(eq + 1).trim();
            int ord = chunkOrder(name);
            if (ord >= 0) {
                chunks.add(new Chunk(ord, urlDecode(rawValue)));
            }
        }
        return concatenateChunks(chunks);
    }

    static String extractJweFromCookieJar(Session session, String originBaseUrl) {
        List<Chunk> chunks = new ArrayList<>();
        Iterator<Cookie> it = CookieSupport.getStoredCookies(session.asScala(), Uri.create(originBaseUrl)).iterator();
        while (it.hasNext()) {
            Cookie c = it.next();
            int ord = chunkOrder(c.name());
            if (ord >= 0) {
                chunks.add(new Chunk(ord, urlDecode(c.value())));
            }
        }
        return concatenateChunks(chunks);
    }

    static byte[] deriveNextAuthEncryptionKey(byte[] secret) throws Exception {
        return hkdfExpand(hkdfExtract(secret), HKDF_INFO, 32);
    }

    /**
     * Returns a cached AES key if the secret hasn't changed, otherwise derives and caches a new one.
     * Safe under concurrent Gatling virtual users thanks to volatile + local-copy pattern.
     */
    private static byte[] getOrDeriveEncryptionKey(String secret) throws Exception {
        byte[] key = cachedEncryptionKey;
        if (key != null && secret.equals(cachedEncryptionKeySecret)) {
            return key;
        }
        key = deriveNextAuthEncryptionKey(secret.getBytes(StandardCharsets.UTF_8));
        cachedEncryptionKey = key;
        cachedEncryptionKeySecret = secret;
        return key;
    }

    /** Compact JWE: {@code dir} + {@code A256GCM}; AAD = US-ASCII bytes of part 0 (RFC 7516, matches jwcrypto). */
    static String decryptJwe(String compactJwe, byte[] cek) throws Exception {
        String[] parts = compactJwe.split("\\.", -1);
        if (parts.length != 5) {
            throw new IllegalStateException("Expected 5 JWE parts, got " + parts.length);
        }

        byte[] protectedHeaderBytes = b64UrlDecode(parts[0]);
        assertDirA256GcmHeader(new String(protectedHeaderBytes, StandardCharsets.UTF_8));

        if (b64UrlDecode(parts[1]).length != 0) {
            throw new IllegalStateException("Expected empty encrypted key for alg=dir");
        }
        if (cek.length != 32) {
            throw new IllegalStateException("CEK must be 256 bits for A256GCM");
        }

        byte[] iv = b64UrlDecode(parts[2]);
        if (iv.length != 12) {
            throw new IllegalStateException("Expected 12-byte IV for A256GCM, got " + iv.length);
        }
        byte[] ciphertext = b64UrlDecode(parts[3]);
        byte[] authTag = b64UrlDecode(parts[4]);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(parts[0].getBytes(StandardCharsets.US_ASCII));

        byte[] ciphertextAndTag = new byte[ciphertext.length + authTag.length];
        System.arraycopy(ciphertext, 0, ciphertextAndTag, 0, ciphertext.length);
        System.arraycopy(authTag, 0, ciphertextAndTag, ciphertext.length, authTag.length);

        return new String(cipher.doFinal(ciphertextAndTag), StandardCharsets.UTF_8);
    }

    private static void assertDirA256GcmHeader(String headerJson) throws Exception {
        JsonNode h = JSON.readTree(headerJson);
        String alg = text(h, "alg");
        String enc = text(h, "enc");
        if (!"dir".equals(alg) || !"A256GCM".equals(enc)) {
            throw new IllegalStateException("JWE must use alg=dir and enc=A256GCM, header was: " + headerJson);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode n = node.get(field);
        return n != null && n.isTextual() ? n.asText() : null;
    }

    private static String concatenateChunks(List<Chunk> chunks) {
        chunks.sort(Comparator.comparingInt(Chunk::order));
        StringBuilder sb = new StringBuilder();
        for (Chunk c : chunks) {
            sb.append(c.value);
        }
        return sb.toString();
    }

    private static int chunkOrder(String name) {
        if (SESSION_COOKIE_PREFIX.equals(name)) {
            return 0;
        }
        if (name.startsWith(SESSION_COOKIE_PREFIX_DOT)) {
            try {
                return Integer.parseInt(name.substring(SESSION_COOKIE_PREFIX_DOT.length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String urlDecode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return raw;
        }
    }

    /** RFC 5869 HKDF-Expand (HMAC-SHA256); {@code salt} empty → HashLen zeros (matches Python cryptography). */
    private static byte[] hkdfExtract(byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(new byte[32], "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        final int hashLen = 32;
        int n = (length + hashLen - 1) / hashLen;
        if (n > 255) {
            throw new IllegalArgumentException("HKDF length too large");
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        byte[] okm = new byte[length];
        byte[] t = new byte[0];
        int offset = 0;
        for (int i = 1; i <= n; i++) {
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            mac.update(t);
            mac.update(info);
            mac.update((byte) i);
            t = mac.doFinal();
            int len = Math.min(hashLen, length - offset);
            System.arraycopy(t, 0, okm, offset, len);
            offset += len;
        }
        return okm;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String stripTrailingSlash(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static byte[] b64UrlDecode(String s) {
        if (s == null || s.isEmpty()) {
            return new byte[0];
        }
        return Base64.getUrlDecoder().decode(s);
    }

    private record Chunk(int order, String value) {
        private Chunk {
            value = value != null ? value : "";
        }
    }
}
