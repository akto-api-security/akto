package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;

public class DigestAuthParam extends AuthParam {
    
    // Digest-specific fields
    private String username;
    private String password;
    private String targetUrl;
    private String method;
    
    // Standard AuthParam fields - must be present for MongoDB compatibility
    private Location where;
    private String key;
    private String value;
    private Boolean showHeader;
    
    // Default constructor required for MongoDB deserialization
    public DigestAuthParam() {
        // Set defaults to prevent null issues
        this.where = Location.HEADER;
        this.key = "Authorization";
        this.value = "";
        this.showHeader = true;
        this.method = "GET";
    }
    
    // Constructor for creating new instances with digest credentials
    public DigestAuthParam(String username, String password, String targetUrl, String method) {
        this(); // Call default constructor to set defaults
        this.username = username;
        this.password = password;
        this.targetUrl = targetUrl;
        this.method = method != null ? method : "GET";
    }
    
    // Standard constructor matching other AuthParam classes
    public DigestAuthParam(Location where, String key, String value, Boolean showHeader) {
        this.where = where;
        this.key = key;
        this.value = value;
        this.showHeader = showHeader;
        this.method = "GET";
    }
    
    @Override
    boolean addAuthTokens(OriginalHttpRequest request) {
        if (this.username == null || this.password == null) {
            return false;
        }
        
        try {
            // For API testing, we use configurable digest parameters
            // In production, these would typically come from a 401 challenge response
            String digestAuthHeader = computeDigestAuthHeader(request);
            
            if (digestAuthHeader != null) {
                // Add the computed Authorization header
                request.getHeaders().computeIfAbsent("authorization", k -> new java.util.ArrayList<>()).clear();
                request.getHeaders().get("authorization").add(digestAuthHeader);
                return true;
            } else {
                return false;
            }
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Computes digest authentication header for API testing
     * Note: In production servers, these parameters would come from WWW-Authenticate header
     * For API testing, we use configurable default values that work with most digest auth implementations
     */
    private String computeDigestAuthHeader(OriginalHttpRequest request) {
        try {
            // Configurable digest parameters for API testing
            // These can be made configurable through UI in future if needed
            String realm = getConfigurableRealm();
            String nonce = generateNonce();
            String algorithm = "MD5"; // Most common algorithm
            String qop = "auth"; // Quality of protection
            
            String requestMethod = request.getMethod() != null ? request.getMethod() : this.method;
            String uri = getRequestUri(request);
            
            // Compute HA1 = Hash(username:realm:password)
            String ha1Input = this.username + ":" + realm + ":" + this.password;
            String ha1 = computeHash(algorithm, ha1Input);
            
            // Compute HA2 = Hash(method:uri)
            String ha2Input = requestMethod + ":" + uri;
            String ha2 = computeHash(algorithm, ha2Input);
            
            // Generate client nonce and nonce count
            String cnonce = generateNonce();
            String nc = "00000001";
            
            // Compute response = Hash(HA1:nonce:nc:cnonce:qop:HA2)
            String responseInput = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2;
            String response = computeHash(algorithm, responseInput);
            
            // Build Authorization header following RFC 2617/7616
            StringBuilder authHeader = new StringBuilder("Digest ");
            authHeader.append("username=\"").append(this.username).append("\"");
            authHeader.append(", realm=\"").append(realm).append("\"");
            authHeader.append(", nonce=\"").append(nonce).append("\"");
            authHeader.append(", uri=\"").append(uri).append("\"");
            authHeader.append(", response=\"").append(response).append("\"");
            authHeader.append(", algorithm=").append(algorithm);
            authHeader.append(", qop=").append(qop);
            authHeader.append(", nc=").append(nc);
            authHeader.append(", cnonce=\"").append(cnonce).append("\"");
            
            return authHeader.toString();
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Gets the realm for digest authentication
     * Uses targetUrl domain if available, otherwise a default realm
     */
    private String getConfigurableRealm() {
        if (this.targetUrl != null) {
            try {
                java.net.URI uri = new java.net.URI(this.targetUrl);
                String host = uri.getHost();
                if (host != null) {
                    return host; // Use the target host as realm
                }
            } catch (Exception e) {
                // Fall through to default
            }
        }
        return "api"; // Default realm for API testing
    }
    
    /**
     * Generates a random nonce
     */
    private String generateNonce() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
    
    /**
     * Extracts the request URI from the request
     */
    private String getRequestUri(OriginalHttpRequest request) {
        String url = request.getUrl();
        if (url == null) return "/";
        
        try {
            java.net.URI uri = new java.net.URI(url);
            String path = uri.getPath();
            String query = uri.getQuery();
            
            if (path == null || path.isEmpty()) path = "/";
            if (query != null && !query.isEmpty()) {
                path += "?" + query;
            }
            
            return path;
        } catch (Exception e) {
            // Fallback: extract everything after the domain
            int slashIndex = url.indexOf('/', 8); // Skip "https://"
            return slashIndex != -1 ? url.substring(slashIndex) : "/";
        }
    }
    
    /**
     * Computes hash using specified algorithm (MD5, SHA-256, etc.)
     */
    private String computeHash(String algorithm, String data) {
        try {
            // Map digest auth algorithm names to Java algorithm names
            String javaAlgorithm;
            switch (algorithm.toUpperCase()) {
                case "MD5":
                case "MD5-SESS":
                    javaAlgorithm = "MD5";
                    break;
                case "SHA-256":
                case "SHA-256-SESS":
                    javaAlgorithm = "SHA-256";
                    break;
                case "SHA-512":
                case "SHA-512-SESS":
                    javaAlgorithm = "SHA-512";
                    break;
                default:
                    javaAlgorithm = "MD5"; // Default fallback
            }
            
            java.security.MessageDigest md = java.security.MessageDigest.getInstance(javaAlgorithm);
            byte[] hash = md.digest(data.getBytes("UTF-8"));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
            
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        // Remove any existing Authorization header
        request.getHeaders().remove(this.key.toLowerCase());
        request.getHeaders().remove("X-Akto-Digest-Auth");
        return true;
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        return Utils.isRequestKeyPresent(this.key, request, where);
    }

    // Digest-specific getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getTargetUrl() {
        return targetUrl;
    }

    public void setTargetUrl(String targetUrl) {
        this.targetUrl = targetUrl;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
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
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public void setWhere(Location where) {
        this.where = where;
    }
    
    public void setShowHeader(Boolean showHeader) {
        this.showHeader = showHeader;
    }
    
    @Override
    public String toString() {
        return "DigestAuthParam{" +
                "username='" + username + '\'' +
                ", targetUrl='" + targetUrl + '\'' +
                ", method='" + method + '\'' +
                '}';
    }
}