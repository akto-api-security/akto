package com.akto.threat.detection.dto;

import com.akto.malicious_request.MaliciousRequest;

import java.util.List;

public class ResponseWrapper {
    private final List<MaliciousRequest> maliciousRequests;

    public ResponseWrapper(Builder builder) {
        this.maliciousRequests = builder.maliciousRequests;
    }

    public List<MaliciousRequest> getMaliciousRequests() {
        return maliciousRequests;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private List<MaliciousRequest> maliciousRequests;

        public Builder withMaliciousRequests(List<MaliciousRequest> maliciousRequests) {
            this.maliciousRequests = maliciousRequests;
            return this;
        }

        public ResponseWrapper build() {
            return new ResponseWrapper(this);
        }
    }
}
