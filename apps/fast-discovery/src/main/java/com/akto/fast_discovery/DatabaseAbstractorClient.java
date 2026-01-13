package com.akto.fast_discovery;

import com.akto.data_actor.DataActor;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.log.LoggerMaker;

import java.util.ArrayList;
import java.util.List;

/**
 * DatabaseAbstractorClient - Wrapper for database operations using DataActor.
 *
 * Handles all database operations via DataActor (ClientActor for remote, DbActor for local).
 * Supports:
 * - Fetching API IDs for Bloom filter initialization
 * - Bulk writes to single_type_info and api_info collections
 * - Ensuring api_collection entries exist
 */
public class DatabaseAbstractorClient {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DatabaseAbstractorClient.class);

    private final DataActor dataActor;

    public DatabaseAbstractorClient(DataActor dataActor) {
        this.dataActor = dataActor;
        loggerMaker.infoAndAddToDb("DatabaseAbstractorClient initialized with DataActor");
    }

    /**
     * Fetch all API IDs for Bloom filter initialization.
     *
     * @return List of API IDs (apiCollectionId, url, method)
     * @throws Exception if call fails
     */
    public List<ApiInfo.ApiInfoKey> fetchApiIds() throws Exception {
        loggerMaker.infoAndAddToDb("Fetching API IDs");
        List<ApiInfo.ApiInfoKey> apiIds = dataActor.fetchApiIds();
        loggerMaker.infoAndAddToDb("Fetched " + apiIds.size() + " API IDs");
        return apiIds;
    }

    /**
     * Fetch all API collections for pre-population of collection cache.
     *
     * Returns only id and hostName fields (minimal data for cache population).
     *
     * @return List of API collections
     * @throws Exception if call fails
     */
    public List<ApiCollection> fetchAllCollections() throws Exception {
        loggerMaker.infoAndAddToDb("Fetching all collections");
        List<ApiCollection> collections = dataActor.fetchAllApiCollections();
        loggerMaker.infoAndAddToDb("Fetched " + collections.size() + " collections");
        return collections;
    }

    /**
     * Bulk write to single_type_info collection via fast-discovery endpoint.
     *
     * @param writes List of bulk updates
     * @throws Exception if call fails
     */
    public void bulkWriteSti(List<BulkUpdates> writes) throws Exception {
        loggerMaker.infoAndAddToDb("Fast-discovery: Bulk writing " + writes.size() + " entries to single_type_info");
        List<Object> writesForSti = new ArrayList<>(writes);
        dataActor.fastDiscoveryBulkWriteSingleTypeInfo(writesForSti);
    }

    /**
     * Bulk write to api_info collection via fast-discovery endpoint.
     * Uses bulkWriteSingleTypeInfo with a different collection since BulkUpdates format is the same.
     *
     * @param writes List of bulk updates
     * @throws Exception if call fails
     */
    public void bulkWriteApiInfo(List<BulkUpdates> writes) throws Exception {
        loggerMaker.infoAndAddToDb("Fast-discovery: Bulk writing " + writes.size() + " entries to api_info");
        // Convert BulkUpdates to ApiInfo list
        List<ApiInfo> apiInfoList = new ArrayList<>();
        for (BulkUpdates write : writes) {
            try {
                ApiInfo apiInfo = BulkUpdatesToApiInfo.convert(write);
                if (apiInfo != null) {
                    apiInfoList.add(apiInfo);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Failed to convert BulkUpdates to ApiInfo: " + e.getMessage());
            }
        }
        dataActor.fastDiscoveryBulkWriteApiInfo(apiInfoList);
    }

    /**
     * Ensure api_collection entries exist for given collection IDs.
     * Creates missing collections automatically.
     *
     * @param collectionIds List of collection IDs to ensure exist
     */
    public void ensureCollections(List<Integer> collectionIds) throws Exception {
        if (collectionIds == null || collectionIds.isEmpty()) {
            return;
        }
        loggerMaker.infoAndAddToDb("Fast-discovery: Ensuring " + collectionIds.size() + " collections exist");
        dataActor.ensureCollections(collectionIds);
    }
}
