package com.akto.dto;

import java.util.HashMap;
import java.util.Map;

public class ApiStats {
    private int timestamp;
    private Map<Integer, Integer> riskScoreMap = new HashMap<>();
    private Map<ApiInfo.ApiType, Integer> apiTypeMap = new HashMap<>();
    private Map<ApiInfo.AuthType, Integer> authTypeMap = new HashMap<>();
    private Map<ApiInfo.ApiAccessType, Integer> accessTypeMap = new HashMap<>();
    private Map<String, Integer> criticalMap = new HashMap<>();
    private int totalAPIs = 0;
    private int apisTestedInLookBackPeriod = 0;
    private float totalRiskScore = 0;
    private int totalInScopeForTestingApis = 0;

    public ApiStats(int timestamp, Map<Integer, Integer> riskScoreMap, Map<ApiInfo.ApiType, Integer> apiTypeMap,
                    Map<ApiInfo.AuthType, Integer> authTypeMap, Map<ApiInfo.ApiAccessType, Integer> accessTypeMap,
                    int totalAPIs, int apisTestedInLookBackPeriod, float totalRiskScore, Map<String, Integer> criticalMap) {
        this.riskScoreMap = riskScoreMap;
        this.apiTypeMap = apiTypeMap;
        this.authTypeMap = authTypeMap;
        this.accessTypeMap = accessTypeMap;
        this.totalAPIs = totalAPIs;
        this.apisTestedInLookBackPeriod = apisTestedInLookBackPeriod;
        this.totalRiskScore = totalRiskScore;
        this.criticalMap = criticalMap;
    }

    public ApiStats(int timestamp) {
        this.timestamp = timestamp;
        this.riskScoreMap = new HashMap<>();
        this.apiTypeMap = new HashMap<>();
        this.authTypeMap = new HashMap<>();
        this.accessTypeMap = new HashMap<>();
    }

    public ApiStats() {
        this.timestamp = 0;
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

    public void addSeverityCount(String severity) {
        Integer val = this.criticalMap.getOrDefault(severity, 0);
        val += 1;
        criticalMap.put(severity, val);
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

    public void setCriticalMap(Map<String, Integer> criticalMap) {
        this.criticalMap = criticalMap;
    }

    public Map<String, Integer> getCriticalMap() {
        return criticalMap;
    }

    public void setAccessTypeMap(Map<ApiInfo.ApiAccessType, Integer> accessTypeMap) {
        this.accessTypeMap = accessTypeMap;
    }

    public int getTotalAPIs() {
        return totalAPIs;
    }

    public void setTotalAPIs(int totalAPIs) {
        this.totalAPIs = totalAPIs;
    }

    public int getApisTestedInLookBackPeriod() {
        return apisTestedInLookBackPeriod;
    }

    public void setApisTestedInLookBackPeriod(int apisTestedInLookBackPeriod) {
        this.apisTestedInLookBackPeriod = apisTestedInLookBackPeriod;
    }


    public float getTotalRiskScore() {
        return totalRiskScore;
    }

    public void setTotalRiskScore(float totalRiskScore) {
        this.totalRiskScore = totalRiskScore;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getTotalInScopeForTestingApis() {
        return totalInScopeForTestingApis;
    }

    public void setTotalInScopeForTestingApis(int totalInScopeForTestingApis) {
        this.totalInScopeForTestingApis = totalInScopeForTestingApis;
    }
}