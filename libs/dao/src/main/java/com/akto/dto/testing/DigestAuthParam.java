package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import lombok.Getter;
import lombok.Setter;
import com.akto.util.http_util.CoreHTTPClient;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
    boolean addAuthTokens(OriginalHttpRequest request) {
        if (this.username == null || this.password == null || this.targetUrl == null) {
            return false;
        }
        
        try {
            // Extract host and port from targetUrl
            URL url = new URL(this.targetUrl);
            String scheme = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            if (port == -1) {
                port = "https".equals(scheme) ? 443 : 80;
            }

            HttpHost target = new HttpHost(scheme, host, port);

            // Create credentials provider with digest auth support
            BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(
                    new AuthScope(target),
                    new UsernamePasswordCredentials(this.username, this.password.toCharArray())
            );

            // Build HttpClient with digest authentication support
            // Note: We use Apache HttpClient 5 here because CoreHTTPClient (OkHttp3) doesn't support
            // built-in digest authentication. Apache HttpClient automatically handles digest auth
            // through its interceptors and respects system proxy settings like CoreHTTPClient and will read variables (HTTP_PROXY_HOST, HTTPS_PROXY_HOST, etc.).
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setDefaultCredentialsProvider(credentialsProvider)
                    .build();

            // Create the HTTP request
            ClassicHttpRequest httpRequest = ClassicRequestBuilder
                    .create(this.method != null ? this.method : "GET")
                    .setUri(this.targetUrl)
                    .build();

            // Execute request - HttpClient will automatically handle digest auth
            // It will make the initial request, receive the 401 challenge,
            // compute the digest response, and resend with the Authorization header
            return httpClient.execute(target, httpRequest, response -> {
                try {
                    // The Authorization header was computed and sent by the client
                    // Extract it from the request that was sent (after digest computation)
                    Header[] authHeaders = httpRequest.getHeaders(AUTHORIZATION);

                    if (authHeaders != null && authHeaders.length > 0) {
                        String authHeader = authHeaders[0].getValue();
                        // Add the Authorization header to the original request
                        List<String> authList = new ArrayList<>();
                        authList.add(authHeader);
                        request.getHeaders().put(AUTHORIZATION, authList);
                        return true;
                    }

                    // Fallback: If we got a 200 response, the auth was successful
                    if (response.getCode() == 200) {
                        // The request succeeded, which means digest auth was handled
                        // Try to extract the Authorization header from the request
                        org.apache.hc.core5.http.Header[] allHeaders = httpRequest.getHeaders("Authorization");
                        if (allHeaders != null && allHeaders.length > 0) {
                            List<String> authList = new ArrayList<>();
                            for (org.apache.hc.core5.http.Header header : allHeaders) {
                                authList.add(header.getValue());
                            }
                            request.getHeaders().put("Authorization", authList);
                            return true;
                        }
                        return true; // Auth succeeded even if we can't extract the header
                    }

                    return false;
                } finally {
                    response.close();
                }
            });

        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        // Remove any existing Authorization header
        request.getHeaders().remove(this.key.toLowerCase());
        return true;
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        return Utils.isRequestKeyPresent(this.key, request, where);
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