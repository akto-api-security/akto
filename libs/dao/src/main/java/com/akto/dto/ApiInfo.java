package com.akto.dto;

import com.akto.dao.context.Context;
import com.akto.dto.type.URLMethods;
import com.akto.util.Util;

import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.*;

public class ApiInfo {
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    // WHENEVER NEW FIELD IS ADDED MAKE SURE TO UPDATE getUpdates METHOD OF AktoPolicy.java AND MERGE METHOD OF
    // ApiInfo.java
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
    private ApiInfoKey id;
    public static final String ID_API_COLLECTION_ID = "_id." + ApiInfoKey.API_COLLECTION_ID;
    public static final String ID_URL = "_id." + ApiInfoKey.URL;
    public static final String ID_METHOD = "_id." + ApiInfoKey.METHOD;

    public static final String ALL_AUTH_TYPES_FOUND = "allAuthTypesFound";
    private Set<Set<AuthType>> allAuthTypesFound;



    // this annotation makes sure that data is not stored in mongo
    @BsonIgnore
    private List<AuthType> actualAuthType;

    public static final String API_ACCESS_TYPES = "apiAccessTypes";
    private Set<ApiAccessType> apiAccessTypes;
    public static final String VIOLATIONS = "violations";
    private Map<String, Integer> violations;
    public static final String LAST_SEEN = "lastSeen";
    public static final String LAST_TESTED = "lastTested";
    public static final String TOTAL_TESTED_COUNT = "totalTestedCount";
    private int lastSeen;
    private int lastTested;
    public static final String IS_SENSITIVE = "isSensitive";
    private boolean isSensitive;
    public static final String SEVERITY_SCORE = "severityScore";
    private float severityScore;
    public static final String COLLECTION_IDS = "collectionIds";
    private List<Integer> collectionIds;

    public static final String RISK_SCORE = "riskScore";
    private float riskScore;

    public static final String LAST_CALCULATED_TIME = "lastCalculatedTime";
    private int lastCalculatedTime;

    private ApiType apiType;
    public static final String API_TYPE = "apiType";

    public static final String RESPONSE_CODES = "responseCodes";
    private List<Integer> responseCodes;

    public static final String DISCOVERED_TIMESTAMP = "discoveredTimestamp";
    private int discoveredTimestamp;

    public static final String SOURCES = "sources";
    Map<String, Object> sources;

    public static final String DESCRIPTION = "description";
    private String description;

    public static final String RATELIMITS = "rateLimits";
    private Map<String, Map<String, Integer>> rateLimits;

    public static final String RATE_LIMIT_CONFIDENCE = "rateLimitConfidence";
    private float rateLimitConfidence;

    public static final String PARENT_MCP_TOOL_NAMES = "parentMcpToolNames";
    private List<String> parentMcpToolNames;

    public enum ApiType {
        REST, GRAPHQL, GRPC, SOAP
    }

    public enum AuthType {
        UNAUTHENTICATED, BASIC, AUTHORIZATION_HEADER, JWT, API_TOKEN, BEARER, CUSTOM, API_KEY, MTLS, SESSION_TOKEN
    }

    public enum ApiAccessType {
        PUBLIC, PRIVATE, PARTNER, THIRD_PARTY
    }

    public static class ApiInfoKey {
        public static final String API_COLLECTION_ID = "apiCollectionId";
        int apiCollectionId;
        public static final String URL = "url";
        public String url;
        public static final String METHOD = "method";
        public URLMethods.Method method;

        public ApiInfoKey() {
        }

        public ApiInfoKey(int apiCollectionId, String url, URLMethods.Method method) {
            this.apiCollectionId = apiCollectionId;
            this.url = url;
            this.method = method;
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

        @Override
        public int hashCode() {
            return Objects.hash(url, method, apiCollectionId);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof ApiInfoKey)) {
                return false;
            }
            ApiInfoKey apiInfoKey= (ApiInfoKey) o;
            return
                    url.equals(apiInfoKey.url) && method.equals(apiInfoKey.method)
                            && apiCollectionId == apiInfoKey.apiCollectionId;
        }


        @Override
        public String toString() {
            return apiCollectionId + " " + url + " " + method;
        }

    }


    public ApiInfo() { }

    public ApiInfo(int apiCollectionId, String url, URLMethods.Method method) {
        this(new ApiInfoKey(apiCollectionId, url, method));
    }

    public ApiInfo(ApiInfoKey apiInfoKey) {
        this.id = apiInfoKey;
        this.violations = new HashMap<>();

        // Initialize rate limits with -1 => no limits
        this.rateLimits = new HashMap<>();
        // Initialize with default structure for common time windows
        Map<String, Integer> defaultMetrics = new HashMap<>();
        defaultMetrics.put("p50", -1);
        defaultMetrics.put("p75", -1);
        defaultMetrics.put("p90", -1);
        defaultMetrics.put("max_requests", -1);
        
        // Initialize for 5, 15, and 30 minute windows
        this.rateLimits.put("5", new HashMap<>(defaultMetrics));
        this.rateLimits.put("15", new HashMap<>(defaultMetrics));
        this.rateLimits.put("30", new HashMap<>(defaultMetrics));

        this.apiAccessTypes = new HashSet<>();
        this.allAuthTypesFound = new HashSet<>();
        this.lastSeen = Context.now();
        this.lastTested = 0 ;
        this.isSensitive = false;
        this.severityScore = 0;
        this.riskScore = 0 ;
        this.lastCalculatedTime = 0;
        if(apiInfoKey != null){
            this.collectionIds = Arrays.asList(apiInfoKey.getApiCollectionId());
        }
        this.responseCodes = new ArrayList<>();
    }

    public static boolean isRestContentType(String contentType) {
        return contentType.contains("application/json")
                || contentType.contains("application/x-www-form-urlencoded")
                || contentType.contains("multipart/form-data");
    }

    public static boolean isSoapContentType(String contentType) {
        return contentType.contains("soap") ||  contentType.contains("xml");
    }

    public static boolean isGrpcContentType(String contentType) {
        return contentType.contains("grpc");
    }

    public static ApiType findApiTypeFromResponseParams(HttpResponseParams responseParams) {
        if (HttpResponseParams.isGraphql(responseParams)) return ApiType.GRAPHQL;

        HttpRequestParams requestParams = responseParams.requestParams;
        Map<String, List<String>> requestHeaders = requestParams.getHeaders();
        List<String> contentTypes = requestHeaders.getOrDefault("content-type", new ArrayList<>());
        for (String contentType: contentTypes) {
            if (isRestContentType(contentType)) return ApiType.REST;
            if (isSoapContentType(contentType)) return ApiType.SOAP;
            if (isGrpcContentType(contentType)) return ApiType.GRPC;
        }

        return ApiType.REST;

        // String requestBody = requestParams.getPayload();
        // String responseBody = responseParams.getPayload();
        // boolean requestBodyIsJson = (requestBody.startsWith("{") && requestBody.endsWith("}")) || (requestBody.startsWith("[") && requestBody.endsWith("]"));
        // boolean responseBodyIsJson = (responseBody.startsWith("{") && responseBody.endsWith("}")) || (responseBody.startsWith("[") && responseBody.endsWith("]"));

        // if (requestBodyIsJson || responseBodyIsJson) return ApiType.REST;
        // return null;
    }

    public ApiInfo(HttpResponseParams httpResponseParams) {
        this(
                httpResponseParams.getRequestParams().getApiCollectionId(),
                httpResponseParams.getRequestParams().getURL(),
                URLMethods.Method.fromString(httpResponseParams.getRequestParams().getMethod())
        );
    }

    public Map<String, Integer> getViolations() {
        return violations;
    }

    public void updateCustomFields(Map<String, Integer> updatedFields) {
        for (String key: updatedFields.keySet()) {
            this.violations.put(key, updatedFields.get(key));
        }
    }

    public void merge(ApiInfo that) {
        // never merge id
        if (that.lastSeen > this.lastSeen) {
            this.lastSeen = that.lastSeen;
        }

        if((that.lastTested != 0) && that.lastTested > this.lastTested){
            this.lastTested = that.lastTested ;
        }
        this.isSensitive = that.isSensitive || this.isSensitive;
        this.severityScore = this.severityScore + that.severityScore;
        this.riskScore = Math.max(this.riskScore, that.riskScore);

        if (that.lastCalculatedTime > this.lastCalculatedTime) {
            this.lastCalculatedTime = that.lastCalculatedTime;
        }
        

        for (String k: that.violations.keySet()) {
            if (this.violations.get(k) == null || that.violations.get(k) > this.violations.get(k)) {
                this.violations.put(k,that.violations.get(k));
            }
        }

        this.allAuthTypesFound.addAll(that.allAuthTypesFound);

        this.apiAccessTypes.addAll(that.getApiAccessTypes());

        // Merge rateLimits - for each time window, take the maximum value for each metric
        for (String timeWindow: that.rateLimits.keySet()) {
            Map<String, Integer> thatMetrics = that.rateLimits.get(timeWindow);
            Map<String, Integer> thisMetrics = this.rateLimits.get(timeWindow);
            
            if (thisMetrics == null) {
                this.rateLimits.put(timeWindow, new HashMap<>(thatMetrics));
            } else {
                for (String metric: thatMetrics.keySet()) {
                    Integer thatValue = thatMetrics.get(metric);
                    Integer thisValue = thisMetrics.get(metric);
                    if (thisValue == null || thatValue > thisValue) {
                        thisMetrics.put(metric, thatValue);
                    }
                }
            }
        }

        // Merge rateLimitConfidence - take the maximum confidence
        this.rateLimitConfidence = Math.max(this.rateLimitConfidence, that.rateLimitConfidence);

    }

    public void setViolations(Map<String, Integer> violations) {
        this.violations = violations;
    }

    private static String mainKey(String url, URLMethods.Method method, int apiCollectionId) {
        return url + "," + method + "," + apiCollectionId;
    }

    public String key() {
        return mainKey(this.id.url, this.id.method, this.id.apiCollectionId);
    }

    public static String keyFromHttpResponseParams(HttpResponseParams httpResponseParams) {
        return mainKey(
                httpResponseParams.getRequestParams().getURL(),
                URLMethods.Method.fromString(httpResponseParams.getRequestParams().getMethod()),
                httpResponseParams.getRequestParams().getApiCollectionId()
        );
    }

    public void calculateActualAuth() {
        List<AuthType> result = new ArrayList<>();
        Set<AuthType> uniqueAuths = new HashSet<>();
        for (Set<AuthType> authTypes: this.allAuthTypesFound) {
            if (authTypes.contains(AuthType.UNAUTHENTICATED)) {
                this.actualAuthType = Collections.singletonList(AuthType.UNAUTHENTICATED);
                uniqueAuths.add(AuthType.UNAUTHENTICATED);
                return;
            }
            if (authTypes.size() > 0) {
                uniqueAuths.addAll(authTypes);
            }
        }

        for (AuthType authType: uniqueAuths) {
            result.add(authType);
        }

        this.actualAuthType = result;

    }

    public ApiAccessType findActualAccessType() {
        if (apiAccessTypes == null || apiAccessTypes.isEmpty()) return null;
        if (this.apiAccessTypes.contains(ApiAccessType.PUBLIC)) return ApiAccessType.PUBLIC;
        if (this.apiAccessTypes.contains(ApiAccessType.PARTNER)) return ApiAccessType.PARTNER;
        if (this.apiAccessTypes.contains(ApiAccessType.THIRD_PARTY)) return ApiAccessType.THIRD_PARTY;
        return new ArrayList<>(apiAccessTypes).get(0);
    }

    public void addStats(ApiStats apiStats) {
        this.calculateActualAuth();
        List<ApiInfo.AuthType> actualAuthTypes = this.actualAuthType;
        if (actualAuthTypes != null && !actualAuthTypes.isEmpty()) apiStats.addAuthType(actualAuthTypes.get(0));

        apiStats.addRiskScore(Math.round(this.riskScore));

        apiStats.addAccessType(this.findActualAccessType());

        apiStats.addApiType(this.apiType);
    }

    public String findSeverity() {
        if (this.severityScore >= 1000) return "CRITICAL";
        if (this.severityScore >= 100) return "HIGH";
        if (this.severityScore >= 10) return "MEDIUM";
        if (this.severityScore > 1) return "LOW";

        return null;
    }

    public static ApiInfoKey getApiInfoKeyFromString(String key) {
        String[] parts = key.split(" ");
        return new ApiInfoKey(Integer.parseInt(parts[0]), parts[1], URLMethods.Method.valueOf(parts[2]));
    }

    @Override
    public String toString() {
        return "{" +
                " id='" + getId() + "'" +
                ", allAuthTypesFound='" + getAllAuthTypesFound() + "'" +
                ", lastSeen='" + getLastSeen() + "'" +
                ", lastTested='" + getLastTested() + "'" +
                ", violations='" + getViolations() + "'" +
                ", accessTypes='" + getApiAccessTypes() + "'" +
                ", isSensitive='" + getIsSensitive() + "'" +
                ", severityScore='" + getSeverityScore() + "'" +
                ", riskScore='" + getRiskScore() + "'" +
                ", lastCalculatedTime='" + getLastCalculatedTime() + "'" +
                "}";
    }


    public ApiInfoKey getId() {
        return id;
    }

    public void setId(ApiInfoKey id) {
        this.collectionIds = Util.replaceElementInList(this.collectionIds, 
        id == null ? null : (Integer) id.getApiCollectionId(),
        this.id == null ? null : (Integer) this.id.getApiCollectionId());
        this.id = id;
    }

    public Set<Set<AuthType>> getAllAuthTypesFound() {
        return allAuthTypesFound;
    }

    public void setAllAuthTypesFound(Set<Set<AuthType>> allAuthTypesFound) {
        this.allAuthTypesFound = allAuthTypesFound;
    }

    public Set<ApiAccessType> getApiAccessTypes() {
        return apiAccessTypes;
    }

    public void setApiAccessTypes(Set<ApiAccessType> apiAccessTypes) {
        this.apiAccessTypes = apiAccessTypes;
    }

    public List<AuthType> getActualAuthType() {
        return actualAuthType;
    }

    public void setActualAuthType(List<AuthType> actualAuthType) {
        this.actualAuthType = actualAuthType;
    }

    public int getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(int lastSeen) {
        this.lastSeen = lastSeen;
    }

     public int getLastTested() {
        return lastTested;
    }

    public void setLastTested(int lastTested) {
        this.lastTested = lastTested;
    }

    public boolean getIsSensitive() {
        return isSensitive;
    }

    public void setIsSensitive(boolean isSensitive) {
        this.isSensitive = isSensitive;
    }

    public float getSeverityScore() {
        return severityScore;
    }

    public void setSeverityScore(float severityScore) {
        this.severityScore = severityScore;
    }
    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }

    public float getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(float riskScore) {
        this.riskScore = riskScore;
    }

    public int getLastCalculatedTime() {
        return lastCalculatedTime;
    }

    public void setLastCalculatedTime(int lastCalculatedTime) {
        this.lastCalculatedTime = lastCalculatedTime;
    }

    public ApiType getApiType() {
        return apiType;
    }

    public void setApiType(ApiType apiType) {
        this.apiType = apiType;
    }

    public List<Integer> getResponseCodes() {
        return responseCodes;
    }

    public void setResponseCodes(List<Integer> responseCodes) {
        this.responseCodes = responseCodes;
    }

    public int getDiscoveredTimestamp() {
        return discoveredTimestamp;
    }

    public void setDiscoveredTimestamp(int discoveredTimestamp) {
        this.discoveredTimestamp = discoveredTimestamp;
    }

    public Map<String, Object> getSources() {
        return this.sources;
    }

    public void setSources(Map<String, Object> sources) {
        this.sources = sources;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Map<String, Integer>> getRateLimits() {
        return rateLimits;
    }

    public void setRateLimits(Map<String, Map<String, Integer>> rateLimits) {
        this.rateLimits = rateLimits;
    }

    public float getRateLimitConfidence() {
        return rateLimitConfidence;
    }

    public void setRateLimitConfidence(float rateLimitConfidence) {
        this.rateLimitConfidence = rateLimitConfidence;
    }

    public List<String> getParentMcpToolNames() {
        return parentMcpToolNames;
    }

    public void setParentMcpToolNames(List<String> parentMcpToolNames) {
        this.parentMcpToolNames = parentMcpToolNames;
    }
}
