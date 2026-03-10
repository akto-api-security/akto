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
        this.key = "Authorization";
        this.value = "";
        this.showHeader = true;
        this.method = "GET";
    }
    
    // Constructor for creating new instances with digest credentials
    public DigestAuthParam(String username, String password, String targetUrl, String method) {
        this(username, password, targetUrl, method, "SHA-256"); // Default to SHA-256
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
        if (this.username == null || this.password == null || this.targetUrl == null) {
            return false;
        }
        
        try {
            // Step 1: Get 401 challenge from server (following your JS implementation)
            DigestChallenge challenge = getDigestChallenge();
            
            if (challenge == null) {
                return false;
            }
            
            // Step 2: Build digest header using server's challenge parameters
            String digestAuthHeader = buildDigestHeader(request, challenge);
            
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
     * Challenge parameters from server's WWW-Authenticate header
     */
    private static class DigestChallenge {
        String realm;
        String nonce;
        String algorithm;
        String qop;
        String opaque;
        
        DigestChallenge(String realm, String nonce, String algorithm, String qop, String opaque) {
            this.realm = realm;
            this.nonce = nonce;
            this.algorithm = algorithm != null ? algorithm.toUpperCase() : "SHA-256";
            this.qop = qop != null ? qop.toLowerCase() : null;
            this.opaque = opaque;
        }
    }
    
    /**
     * Step 1: Get digest challenge from server (like your JS probe request)
     */
    private DigestChallenge getDigestChallenge() {
        try {
            // Make initial request to get 401 challenge (following your JS code)
            java.net.URL url = new java.net.URL(this.targetUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod(this.method != null ? this.method : "GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            // Get response (should be 401)
            int responseCode = conn.getResponseCode();
            
            if (responseCode == 401) {
                // Parse WWW-Authenticate header (like your parseWWWAuthenticate function)
                String wwwAuth = conn.getHeaderField("WWW-Authenticate");
                if (wwwAuth != null) {
                    return parseWWWAuthenticate(wwwAuth);
                }
            }
            
            return null;
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Parse WWW-Authenticate header (following your JS parseWWWAuthenticate function)
     */
    private DigestChallenge parseWWWAuthenticate(String authHeader) {
        if (!authHeader.toLowerCase().startsWith("digest ")) {
            return null;
        }
        
        String realm = null;
        String nonce = null;
        String algorithm = null;
        String qop = null;
        String opaque = null;
        
        // Parse parameters (following your JS regex approach)
        String params = authHeader.substring(7); // Remove "Digest "
        
        // Split by comma but respect quoted strings
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:[^\\s,\"]+|\"(?:\\\\.|[^\"])*\")+");
        java.util.regex.Matcher matcher = pattern.matcher(params);
        
        while (matcher.find()) {
            String part = matcher.group().trim();
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                String key = kv[0].trim();
                String value = kv[1].trim().replaceAll("^\"|\"$", ""); // Remove quotes
                
                switch (key.toLowerCase()) {
                    case "realm": realm = value; break;
                    case "nonce": nonce = value; break;
                    case "algorithm": algorithm = value; break;
                    case "qop": qop = value; break;
                    case "opaque": opaque = value; break;
                }
            }
        }
        
        if (realm != null && nonce != null) {
            return new DigestChallenge(realm, nonce, algorithm, qop, opaque);
        }
        
        return null;
    }
    
    /**
     * Step 2: Build digest header using server challenge (like your buildDigestHeader function)
     */
    private String buildDigestHeader(OriginalHttpRequest request, DigestChallenge challenge) {
        try {
            String method = request.getMethod() != null ? request.getMethod() : this.method;
            String uri = getRequestUri(request);
            String nc = "00000001";
            String cnonce = makeCnonce(8);
            
            // Determine QOP (following your JS logic)
            String qop = null;
            if (challenge.qop != null) {
                String[] qops = challenge.qop.split(",");
                for (String q : qops) {
                    q = q.trim();
                    if ("auth".equals(q)) {
                        qop = "auth";
                        break;
                    } else if ("auth-int".equals(q)) {
                        qop = "auth-int";
                        break;
                    }
                }
            }
            
            // Use user-specified algorithm, with fallback to challenge algorithm
            String hashAlgo = getEffectiveAlgorithm(challenge.algorithm);
            String ha1 = computeHash(hashAlgo, this.username + ":" + challenge.realm + ":" + this.password);
            String ha2 = computeHash(hashAlgo, method + ":" + uri);
            
            // Compute response (following your JS logic)
            String response;
            if (qop != null) {
                response = computeHash(hashAlgo, ha1 + ":" + challenge.nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
            } else {
                response = computeHash(hashAlgo, ha1 + ":" + challenge.nonce + ":" + ha2);
            }
            
            // Build header
            java.util.List<String> kv = new java.util.ArrayList<>();
            kv.add("username=\"" + this.username + "\"");
            kv.add("realm=\"" + challenge.realm + "\"");
            kv.add("nonce=\"" + challenge.nonce + "\"");
            kv.add("uri=\"" + uri + "\"");
            kv.add("response=\"" + response + "\"");
            kv.add("algorithm=\"" + challenge.algorithm + "\"");
            
            if (challenge.opaque != null) {
                kv.add("opaque=\"" + challenge.opaque + "\"");
            }
            
            if (qop != null) {
                kv.add("qop=\"" + qop + "\"");
                kv.add("nc=" + nc);
                kv.add("cnonce=\"" + cnonce + "\"");
            }
            
            return "Digest " + String.join(", ", kv);
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get effective algorithm to use for digest computation
     * Priority: user-specified algorithm > server challenge algorithm > default
     */
    private String getEffectiveAlgorithm(String challengeAlgorithm) {
        // Use user-specified algorithm if available and valid
        if (this.algorithm != null && isValidAlgorithm(this.algorithm)) {
            return this.algorithm;
        }
        // Fall back to challenge algorithm if valid
        if (challengeAlgorithm != null && isValidAlgorithm(challengeAlgorithm)) {
            return challengeAlgorithm;
        }
        // Default to SHA-256 (more secure than MD5)
        return "SHA-256";
    }
    
    /**
     * Check if algorithm is supported (MD5 or SHA-256 as per PR comment)
     */
    private boolean isValidAlgorithm(String algorithm) {
        if (algorithm == null) return false;
        String upperAlgo = algorithm.toUpperCase();
        return "MD5".equals(upperAlgo) || "SHA-256".equals(upperAlgo) || 
               "MD5-SESS".equals(upperAlgo) || "SHA-256-SESS".equals(upperAlgo);
    }

    /**
     * Generate cnonce (like your JS makeCnonce function)
     */
    private String makeCnonce(int len) {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[len];
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
                    javaAlgorithm = "SHA-256"; // Default fallback
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
    
    // setKey, setWhere, setShowHeader are generated by @Setter Lombok annotation
    
    @Override
    public String toString() {
        return "DigestAuthParam{" +
                "username='" + username + '\'' +
                ", targetUrl='" + targetUrl + '\'' +
                ", method='" + method + '\'' +
                '}';
    }
}