package com.akto.behaviour_modelling.impl;

import com.akto.behaviour_modelling.core.UserIdentifier;
import com.akto.dto.HttpResponseParams;

/**
 * Identifies users by their source IP address.
 */
public class IpBasedIdentifier implements UserIdentifier {

    private static final String UNKNOWN = "unknown";

    @Override
    public String extractUserId(HttpResponseParams record) {
        String ip = record.getSourceIP();
        return (ip != null && !ip.isEmpty()) ? ip : UNKNOWN;
    }
}
