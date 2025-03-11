package com.akto.store;

import java.util.concurrent.ConcurrentHashMap;

import com.akto.dao.context.Context;
import com.akto.util.Pair;

public class TestRolesCache {
    private static final ConcurrentHashMap<String, Pair<String, Integer>> tokensMap = new ConcurrentHashMap<>();
    private static final int DEFAULT_EXPIRY_TIME = 30 * 60; // 30 minutes
    private static final ConcurrentHashMap<String, Integer> tokenExpiryMap = new ConcurrentHashMap<>();

    // in case of multiple headers, parse this token string, storing the token as json string

    public static void putToken(String roleName, String token, int fetchTime) {
        tokensMap.put(roleName, new Pair<>(token, fetchTime));
    }

    public static void addTokenExpiry(String roleName, int expiryTime) {
        tokenExpiryMap.put(roleName, expiryTime);
    }

    public static String getTokenForRole(String roleName, int updatedTs) {
        if(tokensMap.isEmpty()){
            return null;
        }
        Pair<String, Integer> tokenPair = tokensMap.get(roleName);
        if (tokenPair == null) {
            return null;
        }
        if(tokenPair.getSecond() < updatedTs) {
            tokensMap.remove(roleName);
            return null;
        }
        int expiryTime = tokenExpiryMap.getOrDefault(roleName, 0);
        if(expiryTime != 0){
            if(expiryTime < Context.now()){
                tokensMap.remove(roleName);
                return null;
            }
        }
        if (Context.now() - tokenPair.getSecond() > DEFAULT_EXPIRY_TIME) {
            tokensMap.remove(roleName);
            return null;
        }
        return tokenPair.getFirst();
    }

}
