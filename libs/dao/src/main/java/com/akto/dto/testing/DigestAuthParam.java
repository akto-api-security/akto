package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DigestAuthParam extends AuthParam {

    // Constants for field names
    public static final String USERNAME_KEY = "username";
    public static final String PASSWORD_KEY = "password";
    public static final String TARGET_URL_KEY = "targetUrl";
    public static final String METHOD_KEY = "method";
    public static final String ALGORITHM_KEY = "algorithm";
    public static final String AUTHORIZATION = "Authorization";


    // Digest-specific fields
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

    // Default constructor required for MongoDB deserialization
    public DigestAuthParam() {
        // Set defaults to prevent null issues
        this.where = Location.HEADER;
        this.key = AUTHORIZATION;
        this.value = "";
        this.showHeader = true;
        this.method = "GET";
    }

    // Constructor with algorithm parameter
    public DigestAuthParam(String username, String password, String targetUrl, String method, String algorithm) {
        this(); // Call default constructor to set defaults
        this.username = username;
        this.password = password;
        this.targetUrl = targetUrl;
        this.method = method != null ? method : "GET";
        this.algorithm = algorithm != null ? algorithm : "SHA-256"; // Default to SHA-256
    }


    @Override
    public boolean addAuthTokens(OriginalHttpRequest request) {
        return false;
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        return false;
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        return false;
    }

    // Standard AuthParam interface implementations
    @Override
    public String getValue() {
        return value;
    }

    @Override
    public String getKey() {
        return key;
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
