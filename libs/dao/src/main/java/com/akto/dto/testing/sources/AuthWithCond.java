package com.akto.dto.testing.sources;

import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.testing.AuthMechanism;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.Map;
import java.util.regex.Pattern;

public class AuthWithCond {
    AuthMechanism authMechanism;
    RecordedLoginFlowInput recordedLoginFlowInput;
    Map<String, String> headerKVPairs;
    String urlRegex;
    @BsonIgnore
    private transient volatile Pattern compiledUrlRegexPattern;

    public AuthWithCond() {
    }

    public AuthWithCond(AuthMechanism authMechanism, Map<String, String> headerKVPairs, RecordedLoginFlowInput recordedLoginFlowInput) {
        this(authMechanism, headerKVPairs, recordedLoginFlowInput, null);
    }

    public AuthWithCond(AuthMechanism authMechanism, Map<String, String> headerKVPairs, RecordedLoginFlowInput recordedLoginFlowInput, String urlRegex) {
        if (headerKVPairs != null) {
            headerKVPairs.remove("");
            headerKVPairs.remove(null);
        }
        this.authMechanism = authMechanism;
        this.headerKVPairs = headerKVPairs;
        this.recordedLoginFlowInput = recordedLoginFlowInput;
        this.urlRegex = (urlRegex != null && !urlRegex.isEmpty()) ? urlRegex.trim() : null;
    }

    public AuthMechanism getAuthMechanism() {
        return authMechanism;
    }

    public void setAuthMechanism(AuthMechanism authMechanism) {
        this.authMechanism = authMechanism;
    }

    public Map<String, String> getHeaderKVPairs() {
        return headerKVPairs;
    }

    public void setHeaderKVPairs(Map<String, String> headerKVPairs) {
        this.headerKVPairs = headerKVPairs;
    }

    public RecordedLoginFlowInput getRecordedLoginFlowInput() {
        return this.recordedLoginFlowInput;
    }

    public void setRecordedLoginFlowInput(RecordedLoginFlowInput recordedLoginFlowInput) {
        this.recordedLoginFlowInput = recordedLoginFlowInput;
    }

    public String getUrlRegex() {
        return urlRegex;
    }

    public void setUrlRegex(String urlRegex) {
        this.urlRegex = (urlRegex != null && !urlRegex.isEmpty()) ? urlRegex.trim() : null;
        this.compiledUrlRegexPattern = null;
    }

    /**
     * Returns compiled URL regex pattern, or null if urlRegex is empty or does not compile (contingency).
     * Cached after first successful compile.
     */
    public Pattern getCompiledUrlRegexPattern() {
        String regex = this.urlRegex;
        if (regex == null || regex.isEmpty()) {
            return null;
        }
        Pattern p = compiledUrlRegexPattern;
        if (p != null) {
            return p;
        }
        synchronized (this) {
            p = compiledUrlRegexPattern;
            if (p != null) {
                return p;
            }
            try {
                p = Pattern.compile(regex);
                compiledUrlRegexPattern = p;
                return p;
            } catch (Exception e) {
                return null;
            }
        }
    }
}
