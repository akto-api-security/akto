package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DigestAuthParam extends AuthParam {

    public static final String USERNAME_KEY = "username";
    public static final String PASSWORD_KEY = "password";
    public static final String TARGET_URL_KEY = "targetUrl";
    public static final String METHOD_KEY = "method";
    public static final String ALGORITHM_KEY = "algorithm";
    public static final String AUTHORIZATION = "Authorization";

    private String username;
    private String password;
    private String targetUrl;
    private String method;
    private String algorithm;

    // Standard AuthParam fields - must be present for MongoDB compatibility
    private Location where;
    private String key;
    private String value;
    private Boolean showHeader;

    public DigestAuthParam() {
        this.where = Location.HEADER;
        this.key = AUTHORIZATION;
        this.value = "";
        this.showHeader = true;
        this.method = "GET";
    }

    public DigestAuthParam(String username, String password, String targetUrl, String method, String algorithm) {
        this();
        this.username = username;
        this.password = password;
        this.targetUrl = targetUrl;
        this.method = method != null ? method : "GET";
        this.algorithm = algorithm != null ? algorithm : "MD5";
    }

    @Override
    boolean addAuthTokens(OriginalHttpRequest request) {
        if (this.username == null || this.password == null) return false;
        // Store credentials as transient fields — not in headers, so they don't affect
        // request equality checks in the executor. ApiExecutor reads them at send time
        // and attaches a DigestOkHttpAuthenticator to handle the 401 challenge.
        request.setDigestAuthUsername(this.username);
        request.setDigestAuthPassword(this.password);
        return true;
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        if (request.getHeaders() != null) {
            request.getHeaders().remove(this.key.toLowerCase());
        }
        request.setDigestAuthUsername(null);
        request.setDigestAuthPassword(null);
        return true;
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        // Credentials are "present" whenever they are configured — the Authorization header
        // is computed dynamically at send time from the server's WWW-Authenticate challenge,
        // so there is nothing to look for in the request headers beforehand.
        return this.username != null && this.password != null;
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public Boolean getShowHeader() {
        return showHeader;
    }

    @Override
    public Location getWhere() {
        return where;
    }

    @Override
    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "DigestAuthParam{" +
                "username='" + username + '\'' +
                ", targetUrl='" + targetUrl + '\'' +
                ", method='" + method + '\'' +
                ", algorithm='" + algorithm + '\'' +
                '}';
    }
}
