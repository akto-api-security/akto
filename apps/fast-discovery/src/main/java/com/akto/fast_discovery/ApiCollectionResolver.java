package com.akto.fast_discovery;

import com.akto.dto.HttpResponseParams;
import com.akto.log.LoggerMaker;

/**
 * ApiCollectionResolver - Resolves API collection ID from HttpResponseParams.
 *
 * The collection ID is already extracted from the Kafka message's akto_vxlan_id field
 * by FastDiscoveryParser and stored in HttpRequestParams.
 *
 * This class simply retrieves it, with fallback to default collection (ID 0).
 */
public class ApiCollectionResolver {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionResolver.class);

    // Default collection ID when no collection is specified
    private static final int DEFAULT_COLLECTION_ID = 0;

    /**
     * Resolve API collection ID from HttpResponseParams.
     *
     * The collection ID is already set in HttpRequestParams.apiCollectionId by FastDiscoveryParser
     * (extracted from akto_vxlan_id field in Kafka message).
     *
     * @param params Parsed HTTP response parameters
     * @return API collection ID
     */
    public int resolveApiCollectionId(HttpResponseParams params) {
        if (params == null || params.getRequestParams() == null) {
            loggerMaker.errorAndAddToDb("HttpResponseParams or RequestParams is null, using default collection");
            return DEFAULT_COLLECTION_ID;
        }

        int collectionId = params.getRequestParams().getApiCollectionId();

        // If collection ID is not set or is 0, use default
        if (collectionId <= 0) {
            loggerMaker.infoAndAddToDb("Collection ID not set or is 0, using default collection");
            return DEFAULT_COLLECTION_ID;
        }

        return collectionId;
    }
}
