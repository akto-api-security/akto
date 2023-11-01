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
    private int lastSeen;
    private List<Integer> collectionIds;
    private int lastTested;
    public static final String IS_SENSITIVE = "isSensitive";
    private boolean isSensitive;
    public static final String SEVERITY_SCORE = "severityScore";
    private float severityScore;

    public enum AuthType {
        UNAUTHENTICATED, BASIC, AUTHORIZATION_HEADER, JWT, API_TOKEN, BEARER, CUSTOM
    }

    public enum ApiAccessType {
        PUBLIC, PRIVATE
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

        public static ApiInfoKey generateFromHttpResponseParams(HttpResponseParams httpResponseParams) {
            int apiCollectionId = httpResponseParams.getRequestParams().getApiCollectionId();
            String url = httpResponseParams.getRequestParams().getURL();
            url = url.split("\\?")[0];
            String methodStr = httpResponseParams.getRequestParams().getMethod();
            URLMethods.Method method = URLMethods.Method.fromString(methodStr);
            return new ApiInfo.ApiInfoKey(apiCollectionId, url, method);
        }

    }


    public ApiInfo() { }

    public ApiInfo(int apiCollectionId, String url, URLMethods.Method method) {
        this(new ApiInfoKey(apiCollectionId, url, method));
    }

    public ApiInfo(ApiInfoKey apiInfoKey) {
        this.id = apiInfoKey;
        this.violations = new HashMap<>();
        this.apiAccessTypes = new HashSet<>();
        this.allAuthTypesFound = new HashSet<>();
        this.lastSeen = Context.now();
        if(apiInfoKey != null){
            this.collectionIds = Arrays.asList(apiInfoKey.getApiCollectionId());
        }
        this.lastTested = 0 ;
        this.isSensitive = false;
        this.severityScore = 0;
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

        for (String k: that.violations.keySet()) {
            if (this.violations.get(k) == null || that.violations.get(k) > this.violations.get(k)) {
                this.violations.put(k,that.violations.get(k));
            }
        }

        this.allAuthTypesFound.addAll(that.allAuthTypesFound);

        this.apiAccessTypes.addAll(that.getApiAccessTypes());

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
                "}";
    }


    public ApiInfoKey getId() {
        return id;
    }

    public void setId(ApiInfoKey id) {
        this.collectionIds = Util.replaceElementInList(this.collectionIds, 
        id == null ? null : id.getApiCollectionId(),
        this.id == null ? null : this.id.getApiCollectionId());
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

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
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
}
