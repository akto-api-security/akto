package com.akto.dto;

import java.util.HashMap;
import java.util.Map;

public class ApiStats {
    private Map<Integer, Integer> riskScoreMap = new HashMap<>();
    private Map<ApiInfo.ApiType, Integer> apiTypeMap = new HashMap<>();
    private Map<ApiInfo.AuthType, Integer> authTypeMap = new HashMap<>();
    private Map<ApiInfo.ApiAccessType, Integer> accessTypeMap = new HashMap<>();

    public ApiStats(Map<Integer, Integer> riskScoreMap, Map<ApiInfo.ApiType, Integer> apiTypeMap, Map<ApiInfo.AuthType, Integer> authTypeMap, Map<ApiInfo.ApiAccessType, Integer> accessTypeMap) {
        this.riskScoreMap = riskScoreMap;
        this.apiTypeMap = apiTypeMap;
        this.authTypeMap = authTypeMap;
        this.accessTypeMap = accessTypeMap;
    }

    public ApiStats() {
        this.riskScoreMap = new HashMap<>();
        this.apiTypeMap = new HashMap<>();
        this.authTypeMap = new HashMap<>();
        this.accessTypeMap = new HashMap<>();
    }

    public void addRiskScore(Integer riskScore) {
        Integer val = this.riskScoreMap.getOrDefault(riskScore, 0);
        val += 1;
        riskScoreMap.put(riskScore, val);
    }

    public void addApiType(ApiInfo.ApiType apiType) {
        Integer val = this.apiTypeMap.getOrDefault(apiType, 0);
        val += 1;
        apiTypeMap.put(apiType, val);
    }

    public void addAuthType(ApiInfo.AuthType authType) {
        Integer val = this.authTypeMap.getOrDefault(authType, 0);
        val += 1;
        authTypeMap.put(authType, val);
    }

    public void addAccessType(ApiInfo.ApiAccessType apiAccessType) {
        Integer val = this.accessTypeMap.getOrDefault(apiAccessType, 0);
        val += 1;
        accessTypeMap.put(apiAccessType, val);
    }

    public Map<Integer, Integer> getRiskScoreMap() {
        return riskScoreMap;
    }

    public void setRiskScoreMap(Map<Integer, Integer> riskScoreMap) {
        this.riskScoreMap = riskScoreMap;
    }

    public Map<ApiInfo.ApiType, Integer> getApiTypeMap() {
        return apiTypeMap;
    }

    public void setApiTypeMap(Map<ApiInfo.ApiType, Integer> apiTypeMap) {
        this.apiTypeMap = apiTypeMap;
    }

    public Map<ApiInfo.AuthType, Integer> getAuthTypeMap() {
        return authTypeMap;
    }

    public void setAuthTypeMap(Map<ApiInfo.AuthType, Integer> authTypeMap) {
        this.authTypeMap = authTypeMap;
    }

    public Map<ApiInfo.ApiAccessType, Integer> getAccessTypeMap() {
        return accessTypeMap;
    }

    public void setAccessTypeMap(Map<ApiInfo.ApiAccessType, Integer> accessTypeMap) {
        this.accessTypeMap = accessTypeMap;
    }
}