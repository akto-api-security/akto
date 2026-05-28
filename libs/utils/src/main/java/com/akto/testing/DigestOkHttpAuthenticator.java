package com.akto.testing;

import com.akto.log.LoggerMaker;
import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DigestOkHttpAuthenticator implements Authenticator {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DigestOkHttpAuthenticator.class, LoggerMaker.LogDb.TESTING);

    private final String username;
    private final String password;
    private volatile String lastAuthorizationHeader;

    public DigestOkHttpAuthenticator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public String getLastAuthorizationHeader() {
        return lastAuthorizationHeader;
    }

    private static int responseCount(Response response) {
        int count = 1;
        while ((response = response.priorResponse()) != null) count++;
        return count;
    }

    @Override
    public Request authenticate(Route route, Response response) {
        if (responseCount(response) >= 2) return null;

        String wwwAuth = response.header("WWW-Authenticate");
        if (wwwAuth == null || !wwwAuth.startsWith("Digest ")) return null;

        Map<String, String> params = parseChallenge(wwwAuth);
        String realm = params.get("realm");
        String nonce = params.get("nonce");
        String qop   = params.get("qop");
        String algorithm = params.getOrDefault("algorithm", "MD5");

        if (realm == null || nonce == null) return null;

        String uri = response.request().url().encodedPath();
        String query = response.request().url().encodedQuery();
        if (query != null && !query.isEmpty()) uri += "?" + query;

        String method = response.request().method();
        String cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String nc     = "00000001";

        try {
            String ha1 = digestHex(username + ":" + realm + ":" + password, algorithm);
            String ha2 = digestHex(method + ":" + uri, algorithm);

            String responseHash;
            if ("auth".equals(qop) || "auth-int".equals(qop)) {
                responseHash = digestHex(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2, algorithm);
            } else {
                responseHash = digestHex(ha1 + ":" + nonce + ":" + ha2, algorithm);
            }

            StringBuilder authHeader = new StringBuilder("Digest ");
            authHeader.append("username=\"").append(username).append("\", ");
            authHeader.append("realm=\"").append(realm).append("\", ");
            authHeader.append("nonce=\"").append(nonce).append("\", ");
            authHeader.append("uri=\"").append(uri).append("\", ");
            authHeader.append("response=\"").append(responseHash).append("\"");
            if (qop != null) {
                authHeader.append(", qop=").append(qop);
                authHeader.append(", nc=").append(nc);
                authHeader.append(", cnonce=\"").append(cnonce).append("\"");
            }
            authHeader.append(", algorithm=").append(algorithm);

            loggerMaker.info("Digest auth computed for user=" + username + " url=" + response.request().url());
            this.lastAuthorizationHeader = authHeader.toString();
            return response.request().newBuilder()
                    .header("Authorization", authHeader.toString())
                    .build();

        } catch (Exception e) {
            loggerMaker.info("Digest auth failed: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, String> parseChallenge(String headerValue) {
        Map<String, String> params = new HashMap<>();
        String rest = headerValue.substring("Digest ".length()).trim();
        String[] parts = rest.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
        for (String part : parts) {
            String p = part.trim();
            int eq = p.indexOf('=');
            if (eq < 0) continue;
            String k = p.substring(0, eq).trim();
            String v = p.substring(eq + 1).trim();
            if (v.startsWith("\"") && v.endsWith("\"")) {
                v = v.substring(1, v.length() - 1);
            }
            params.put(k, v);
        }
        return params;
    }

    private static String digestHex(String input, String algorithm) throws Exception {
        String algo = algorithm != null && algorithm.toUpperCase().contains("SHA-256") ? "SHA-256" : "MD5";
        MessageDigest md = MessageDigest.getInstance(algo);
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
