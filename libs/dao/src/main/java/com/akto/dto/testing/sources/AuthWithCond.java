package com.akto.dto.testing.sources;

import com.akto.dto.RecordedLoginFlowInput;
import com.akto.dto.testing.AuthMechanism;

import java.util.Map;

public class AuthWithCond {
    AuthMechanism authMechanism;
    RecordedLoginFlowInput recordedLoginFlowInput;
    Map<String, String> headerKVPairs;

    public AuthWithCond() {
    }

    public AuthWithCond(AuthMechanism authMechanism, Map<String, String> headerKVPairs, RecordedLoginFlowInput recordedLoginFlowInput) {
        if (headerKVPairs != null) {
            headerKVPairs.remove("");
            headerKVPairs.remove(null);
        }
        this.authMechanism = authMechanism;
        this.headerKVPairs = headerKVPairs;
        this.recordedLoginFlowInput = recordedLoginFlowInput;
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
}
