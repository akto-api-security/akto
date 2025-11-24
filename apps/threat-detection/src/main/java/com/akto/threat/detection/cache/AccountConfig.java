package com.akto.threat.detection.cache;

import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.URLTemplate;

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
    private final Set<String> apiInfoKeys;

    public AccountConfig(int accountId, boolean isRedacted, List<ApiCollection> apiCollections,
                         List<ApiInfo> apiInfos, Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates,
                         Set<String> apiInfoKeys) {
        this.accountId = accountId;
        this.isRedacted = isRedacted;
        this.apiCollections  = new HashMap<>();
        initMapApiCollectionsFromList(apiCollections);
        this.apiInfos = apiInfos;
        this.apiCollectionUrlTemplates = apiCollectionUrlTemplates;
        this.apiInfoKeys = apiInfoKeys;
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
     * Get all API info keys
     */
    public Set<String> getApiInfoKeys() {
        return apiInfoKeys;
    }

    @Override
    public String toString() {
        return "AccountConfig{" +
                "accountId=" + accountId +
                ", isRedacted=" + isRedacted +
                ", apiCollectionsCount=" + (apiCollections != null ? apiCollections.size() : 0) +
                ", apiInfosCount=" + (apiInfos != null ? apiInfos.size() : 0) +
                ", apiInfoKeysCount=" + (apiInfoKeys != null ? apiInfoKeys.size() : 0) +
                '}';
    }
}
