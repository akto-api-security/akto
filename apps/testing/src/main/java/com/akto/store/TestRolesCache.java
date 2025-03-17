package com.akto.store;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.akto.dao.context.Context;
import com.akto.dto.testing.AuthParam;
import com.akto.util.Pair;

public class TestRolesCache {
    private static final ConcurrentHashMap<String, Pair<List<AuthParam>, Integer>> mapRoleToTokensAndFetchEpoch = new ConcurrentHashMap<>();
    private static final int DEFAULT_EXPIRY_TIME = 30 * 60; // 30 minutes
    private static final ConcurrentHashMap<String, Integer> mapRoleToExpiryEpoch = new ConcurrentHashMap<>();

    // in case of multiple headers, parse this token string, storing the token as json string

    public static void putToken(String roleKey, List<AuthParam> tokens, int fetchTime) {
        mapRoleToTokensAndFetchEpoch.put(roleKey, new Pair<List<AuthParam>,Integer>(tokens, fetchTime));
    }

    public static void addTokenExpiry(String roleKey, int expiryTime) {
        mapRoleToExpiryEpoch.put(roleKey, expiryTime);        
    }

    public static List<AuthParam> getTokenForRole(String roleKey, int updatedTs) {
        if(mapRoleToTokensAndFetchEpoch.isEmpty()){
            return null;
        }
        Pair<List<AuthParam>, Integer> tokenPair = mapRoleToTokensAndFetchEpoch.get(roleKey);
        if (tokenPair == null) {
            return null;
        }
        if(tokenPair.getSecond() < updatedTs) {
            mapRoleToTokensAndFetchEpoch.remove(roleKey);
            return null;
        }
        int expiryTime = mapRoleToExpiryEpoch.getOrDefault(roleKey, 0);
        if(expiryTime != 0){
            if(expiryTime < Context.now()){
                mapRoleToTokensAndFetchEpoch.remove(roleKey);
                return null;
            }
        }

        if (Context.now() - tokenPair.getSecond() > DEFAULT_EXPIRY_TIME) {
            mapRoleToTokensAndFetchEpoch.remove(roleKey);
            return null;
        }

        return tokenPair.getFirst();
    }

}
