package com.akto.util;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;

public class McpTokenGenerator {

    public static final int ACCESS_TOKEN_MINUTES = 60;
    private static final int DEFAULT_TOKEN_MINUTES = 15;

    private static final String CLAIM_CONTEXT_SOURCE = "contextSource";
    private static final String CLAIM_ACCOUNT_ID = "accountId";
    private static final String CLAIM_AUTH_ROLE = "auth_role";
    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_AUDIENCE = "aud";
    private static final String CLAIM_CLIENT_ID = "client_id";

    // Short-lived Ask Akto token for the current account and context
    public static String generateToken(String username){
        String contextSource = "api";
        int accountId;
        if(Context.accountId.get() != null){
            accountId = Context.accountId.get();
        }else{
            accountId = 1000000;
        }
        if(Context.contextSource.get() != null){
            contextSource = Context.contextSource.get().name();
        }
        return build(username, accountId, contextSource, new HashMap<>(), DEFAULT_TOKEN_MINUTES);
    }

    // OAuth access token: same claims plus audience and client id
    public static String generateOAuthToken(String username, int accountId, String contextSource, String audience, String clientId) {
        Map<String,Object> extraClaims = new HashMap<>();
        if (audience != null && !audience.isEmpty()) {
            extraClaims.put(CLAIM_AUDIENCE, audience);
        }
        extraClaims.put(CLAIM_CLIENT_ID, clientId);
        return build(username, accountId, contextSource, extraClaims, ACCESS_TOKEN_MINUTES);
    }

    private static String build(String username, int accountId, String contextSource, Map<String,Object> extraClaims, int minutes) {
        Map<String,Object> claims = new HashMap<>(extraClaims);
        claims.put(CLAIM_CONTEXT_SOURCE, contextSource);
        claims.put(CLAIM_ACCOUNT_ID, accountId);
        claims.put(CLAIM_AUTH_ROLE, "mcp");
        claims.put(CLAIM_USERNAME, username);

        try {
            return JwtAuthenticator.createJWT(
                claims,
                "Akto",
                "mcp_auth",
                Calendar.MINUTE,
                minutes
            );
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
