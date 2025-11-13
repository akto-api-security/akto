package com.akto.threat.detection.cache;

import com.akto.dto.ApiCollection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable data class to hold cached account configuration.
 * Contains account ID, redaction settings, and API collections.
 */
public class AccountConfig {
    private final int accountId;
    private final boolean isRedacted;
    private final Map<Integer, Boolean> apiCollections;

    public AccountConfig(int accountId, boolean isRedacted, List<ApiCollection> apiCollections) {
        this.accountId = accountId;
        this.isRedacted = isRedacted;
        this.apiCollections  = new HashMap<>();
        initMapApiCollectionsFromList(apiCollections);
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

    @Override
    public String toString() {
        return "AccountConfig{" +
                "accountId=" + accountId +
                ", isRedacted=" + isRedacted +
                ", apiCollectionsCount=" + (apiCollections != null ? apiCollections.size() : 0) +
                '}';
    }
}
