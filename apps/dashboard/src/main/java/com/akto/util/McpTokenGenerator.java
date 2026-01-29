package com.akto.util;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.akto.dao.context.Context;
import com.akto.database_abstractor_authenticator.JwtAuthenticator;

public class McpTokenGenerator {

    public static String generateToken(String username){
        Map<String,Object> claims = new HashMap<>();
        int accountId;
        String contextSource = "api";
        if(Context.accountId.get() != null){
            accountId = Context.accountId.get();
        }else{
            accountId = 1000000;
        }
        if(Context.contextSource.get() != null){
            contextSource = Context.contextSource.get().name();
        }
        claims.put("contextSource", contextSource);
        claims.put("accountId", accountId);
        claims.put("auth_role", "mcp");
        claims.put("username", username);

        try {
            String token = JwtAuthenticator.createJWT(
                claims,
                "Akto",
                "mcp_auth",
                Calendar.MINUTE,
                15
            );
            return token;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
}
