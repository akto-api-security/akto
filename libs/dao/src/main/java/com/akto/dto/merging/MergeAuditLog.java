package com.akto.dto.merging;

import org.bson.types.ObjectId;

import java.util.List;

public class MergeAuditLog {

    public static final String API_COLLECTION_ID = "apiCollectionId";
    public static final String MERGE_TYPE = "mergeType";
    public static final String TEMPLATE_URL = "templateUrl";
    public static final String METHOD = "method";
    public static final String DISCOVERED_AT = "discoveredAt";
    public static final String STI_DELETED = "stiDeleted";
    public static final String API_INFO_DELETED = "apiInfoDeleted";
    public static final String SAMPLE_DATA_DELETED = "sampleDataDeleted";

    private ObjectId id;
    private int apiCollectionId;
    private String mergeType;       // "TEMPLATE_TO_STATIC" or "STATIC_TO_STATIC"
    private String templateUrl;     // e.g. "/api/INTEGER/users"
    private String method;          // e.g. "GET"
    private List<String> matchedStaticUrls;
    private int discoveredAt;
    private boolean stiDeleted;
    private boolean apiInfoDeleted;
    private boolean sampleDataDeleted;

    public MergeAuditLog() {}

    public MergeAuditLog(int apiCollectionId, String mergeType, String templateUrl,
                         String method, List<String> matchedStaticUrls, int discoveredAt) {
        this.apiCollectionId = apiCollectionId;
        this.mergeType = mergeType;
        this.templateUrl = templateUrl;
        this.method = method;
        this.matchedStaticUrls = matchedStaticUrls;
        this.discoveredAt = discoveredAt;
    }

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }

    public int getApiCollectionId() { return apiCollectionId; }
    public void setApiCollectionId(int apiCollectionId) { this.apiCollectionId = apiCollectionId; }

    public String getMergeType() { return mergeType; }
    public void setMergeType(String mergeType) { this.mergeType = mergeType; }

    public String getTemplateUrl() { return templateUrl; }
    public void setTemplateUrl(String templateUrl) { this.templateUrl = templateUrl; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public List<String> getMatchedStaticUrls() { return matchedStaticUrls; }
    public void setMatchedStaticUrls(List<String> matchedStaticUrls) { this.matchedStaticUrls = matchedStaticUrls; }

    public int getDiscoveredAt() { return discoveredAt; }
    public void setDiscoveredAt(int discoveredAt) { this.discoveredAt = discoveredAt; }

    public boolean isStiDeleted() { return stiDeleted; }
    public void setStiDeleted(boolean stiDeleted) { this.stiDeleted = stiDeleted; }

    public boolean isApiInfoDeleted() { return apiInfoDeleted; }
    public void setApiInfoDeleted(boolean apiInfoDeleted) { this.apiInfoDeleted = apiInfoDeleted; }

    public boolean isSampleDataDeleted() { return sampleDataDeleted; }
    public void setSampleDataDeleted(boolean sampleDataDeleted) { this.sampleDataDeleted = sampleDataDeleted; }
}
