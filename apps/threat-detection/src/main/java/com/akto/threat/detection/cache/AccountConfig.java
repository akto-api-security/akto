package com.akto.threat.detection.cache;

import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.URLMethods;
import com.akto.dto.type.URLTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable data class to hold cached account configuration.
 * Contains account ID, redaction settings, API collections, and API info metadata.
 */
public class AccountConfig {
    private final int accountId;
    private final boolean isRedacted;
    private final Map<Integer, Boolean> apiCollections;
    private final List<ApiInfo> apiInfos;
    private final Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates;
    private final Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods;

    public AccountConfig(int accountId, boolean isRedacted, List<ApiCollection> apiCollections,
                         List<ApiInfo> apiInfos, Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates,
                         Map<String, Set<URLMethods.Method>> apiInfoUrlToMethods) {
        this.accountId = accountId;
        this.isRedacted = isRedacted;
        this.apiCollections  = new HashMap<>();
        initMapApiCollectionsFromList(apiCollections);
        // Guarantee non-null collections - use empty collections if null
        this.apiInfos = apiInfos != null ? apiInfos : Collections.emptyList();
        this.apiCollectionUrlTemplates = apiCollectionUrlTemplates != null ? apiCollectionUrlTemplates : Collections.emptyMap();
        this.apiInfoUrlToMethods = apiInfoUrlToMethods != null ? apiInfoUrlToMethods : Collections.emptyMap();
    }

    private void initMapApiCollectionsFromList(List<ApiCollection> apiCollections){
        for(ApiCollection apiCollection: apiCollections){
            this.apiCollections.put(apiCollection.getId(), apiCollection.getRedact());
        }
    }

    /**
     * Get the account ID
     */
    public int getAccountId() {
        return accountId;
    }

    /**
     * Check if payload redaction is enabled
     */
    public boolean isRedacted() {
        return isRedacted;
    }

    /**
     * Get all API collections mapped by collection ID
     */
    public Map<Integer, Boolean> getApiCollections() {
        return apiCollections;
    }

    /**
     * Get specific API collection by ID
     *
     * @param collectionId The API collection ID
     * @return ApiCollection or null if not found
     */
    public Boolean isApiCollectionRedacted(int collectionId) {
        return apiCollections != null ? apiCollections.get(collectionId) : null;
    }

    /**
     * Get all API infos
     */
    public List<ApiInfo> getApiInfos() {
        return apiInfos;
    }

    /**
     * Get API collection URL templates mapped by collection ID
     */
    public Map<Integer, List<URLTemplate>> getApiCollectionUrlTemplates() {
        return apiCollectionUrlTemplates;
    }

    /**
     * Get API info URL to methods mapping
     * Key format: "apiCollectionId:url"
     * Value: Set of HTTP methods for that URL
     */
    public Map<String, Set<URLMethods.Method>> getApiInfoUrlToMethods() {
        return apiInfoUrlToMethods;
    }

    @Override
    public String toString() {
        return "AccountConfig{" +
                "accountId=" + accountId +
                ", isRedacted=" + isRedacted +
                ", apiCollectionsCount=" + (apiCollections != null ? apiCollections.size() : 0) +
                ", apiInfosCount=" + (apiInfos != null ? apiInfos.size() : 0) +
                ", apiInfoUrlToMethodsCount=" + (apiInfoUrlToMethods != null ? apiInfoUrlToMethods.size() : 0) +
                '}';
    }
}
