package com.akto.dto;

import com.akto.dto.type.URLMethods;

import java.util.List;
import java.util.Set;

public class EndpointInfoView {

    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;

    public static final String URL = "url";
    private String url;

    public static final String METHOD = "method";
    private URLMethods.Method method;

    public static final String AUTH_TYPES = "authTypes";
    private Set<String> authTypes;

    public static final String API_ACCESS_TYPES = "apiAccessTypes";
    private Set<ApiInfo.ApiAccessType> apiAccessTypes;

    public static final String API_TYPE = "apiType";
    private ApiInfo.ApiType apiType;

    public static final String RISK_SCORE = "riskScore";
    private float riskScore;

    public static final String SEVERITY_SCORE = "severityScore";
    private float severityScore;

    public static final String LAST_TESTED = "lastTested";
    private int lastTested;

    public static final String LAST_SEEN = "lastSeen";
    private int lastSeen;

    public static final String DISCOVERED_TIMESTAMP = "discoveredTimestamp";
    private int discoveredTimestamp;

    public static final String IS_OUT_OF_TESTING_SCOPE = "isOutOfTestingScope";
    private boolean isOutOfTestingScope;

    public static final String SENSITIVE_SUB_TYPES = "sensitiveSubTypes";
    private List<String> sensitiveSubTypes;

    public static final String PARAM_COUNT = "paramCount";
    private int paramCount;

    public static final String ACTUAL_AUTH_TYPE = "actualAuthType";
    private List<String> actualAuthType;

    public static final String ACTUAL_ACCESS_TYPE = "actualAccessType";
    private List<String> actualAccessType;

    public static final String SEVERITY = "severity";
    private String severity;

    public EndpointInfoView() {
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public URLMethods.Method getMethod() {
        return method;
    }

    public void setMethod(URLMethods.Method method) {
        this.method = method;
    }

    public Set<String> getAuthTypes() {
        return authTypes;
    }

    public void setAuthTypes(Set<String> authTypes) {
        this.authTypes = authTypes;
    }

    public Set<ApiInfo.ApiAccessType> getApiAccessTypes() {
        return apiAccessTypes;
    }

    public void setApiAccessTypes(Set<ApiInfo.ApiAccessType> apiAccessTypes) {
        this.apiAccessTypes = apiAccessTypes;
    }

    public ApiInfo.ApiType getApiType() {
        return apiType;
    }

    public void setApiType(ApiInfo.ApiType apiType) {
        this.apiType = apiType;
    }

    public float getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(float riskScore) {
        this.riskScore = riskScore;
    }

    public float getSeverityScore() {
        return severityScore;
    }

    public void setSeverityScore(float severityScore) {
        this.severityScore = severityScore;
    }

    public int getLastTested() {
        return lastTested;
    }

    public void setLastTested(int lastTested) {
        this.lastTested = lastTested;
    }

    public int getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(int lastSeen) {
        this.lastSeen = lastSeen;
    }

    public int getDiscoveredTimestamp() {
        return discoveredTimestamp;
    }

    public void setDiscoveredTimestamp(int discoveredTimestamp) {
        this.discoveredTimestamp = discoveredTimestamp;
    }

    public boolean getIsOutOfTestingScope() {
        return isOutOfTestingScope;
    }

    public void setIsOutOfTestingScope(boolean isOutOfTestingScope) {
        this.isOutOfTestingScope = isOutOfTestingScope;
    }

    public List<String> getSensitiveSubTypes() {
        return sensitiveSubTypes;
    }

    public void setSensitiveSubTypes(List<String> sensitiveSubTypes) {
        this.sensitiveSubTypes = sensitiveSubTypes;
    }

    public int getParamCount() {
        return paramCount;
    }

    public void setParamCount(int paramCount) {
        this.paramCount = paramCount;
    }

    public String findSeverity() {
        if (this.severityScore >= 1000) return "CRITICAL";
        if (this.severityScore >= 100) return "HIGH";
        if (this.severityScore >= 10) return "MEDIUM";
        if (this.severityScore > 1) return "LOW";
        return null;
    }

    public ApiInfo.ApiAccessType findActualAccessType() {
        if (apiAccessTypes == null || apiAccessTypes.isEmpty()) return null;
        if (apiAccessTypes.contains(ApiInfo.ApiAccessType.PUBLIC)) return ApiInfo.ApiAccessType.PUBLIC;
        if (apiAccessTypes.contains(ApiInfo.ApiAccessType.PARTNER)) return ApiInfo.ApiAccessType.PARTNER;
        if (apiAccessTypes.contains(ApiInfo.ApiAccessType.THIRD_PARTY)) return ApiInfo.ApiAccessType.THIRD_PARTY;
        return apiAccessTypes.iterator().next();
    }

    public String findActualAuthType() {
        if (authTypes == null || authTypes.isEmpty()) return null;
        if (authTypes.contains(ApiInfo.AuthType.UNAUTHENTICATED)) return ApiInfo.AuthType.UNAUTHENTICATED;
        String first = authTypes.iterator().next();
        return isStandardAuthType(first) ? first : ApiInfo.AuthType.CUSTOM;
    }

    private boolean isStandardAuthType(String authType) {
        return authType.equals(ApiInfo.AuthType.UNAUTHENTICATED) ||
               authType.equals(ApiInfo.AuthType.BASIC) ||
               authType.equals(ApiInfo.AuthType.AUTHORIZATION_HEADER) ||
               authType.equals(ApiInfo.AuthType.JWT) ||
               authType.equals(ApiInfo.AuthType.API_TOKEN) ||
               authType.equals(ApiInfo.AuthType.BEARER) ||
               authType.equals(ApiInfo.AuthType.API_KEY) ||
               authType.equals(ApiInfo.AuthType.MTLS) ||
               authType.equals(ApiInfo.AuthType.SESSION_TOKEN);
    }

    public void addStats(ApiStats apiStats) {
        String authType = findActualAuthType();
        if (authType != null) {
            apiStats.addAuthType(authType);
        }
        apiStats.addRiskScore(Math.round(this.riskScore));
        apiStats.addAccessType(findActualAccessType());
        apiStats.addApiType(this.apiType);
    }
}
