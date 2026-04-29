package com.akto.behaviour_modelling.core;

import com.akto.dto.HttpResponseParams;

/**
 * Extracts a string user identity from an HTTP record.
 * Implement this to switch between identity strategies (IP, session token, etc.)
 * without touching anything else.
 */
public interface UserIdentifier {
    String extractUserId(HttpResponseParams record);
}
