package com.akto.malicious_request;

import com.akto.dto.HttpResponseParams;

import java.util.Collections;
import java.util.List;

public class MaliciousRequestGenerator {

    private MaliciousRequestGenerator() {}

    public static MaliciousRequestGenerator INSTANCE = new MaliciousRequestGenerator();

    // We return a list of malicious requests, since 1 request can qualify as malicious for
    // different reasons.
    // Here we would pass definition of malicious request, which the user defines in the config.
    // But for now we have hardcoded it to be 4xx status code only.
    public List<MaliciousRequest> generateIfAny(HttpResponseParams responseParams) {
        String actor = responseParams.getSourceIP();
        if (actor == null || actor.isEmpty()) {
            return Collections.emptyList();
        }

        int statusCode = responseParams.getStatusCode();
        boolean isMalicious = statusCode >= 400 && statusCode < 500;

        if (!isMalicious) {
            return Collections.emptyList();
        }

        return Collections.singletonList(new MaliciousRequest(actor, responseParams));
    }
}
