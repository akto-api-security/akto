package com.akto.dto.testing.sources;

import com.akto.dto.testing.AuthMechanism;

import java.util.Map;

public class AuthWithCond {
    AuthMechanism authMechanism;
    Map<String, String> headerKVPairs;

    public AuthWithCond() {
    }

    public AuthWithCond(AuthMechanism authMechanism, Map<String, String> headerKVPairs) {
        this.authMechanism = authMechanism;
        this.headerKVPairs = headerKVPairs;
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
}
