package com.akto.fast_discovery;

import com.akto.data_actor.ClientActor;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.runtime.RuntimeUtil;
import com.akto.hybrid_parsers.HttpCallParser;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ApiCollectionResolver - Resolves API collection ID using the same logic as mini-runtime.
 *
 * Uses hostname-based collections for MIRRORING traffic, vxlanId-based collections otherwise.
 * Maintains a Set of created collections to avoid redundant HTTP calls.
 *
 * This ensures fast-discovery and mini-runtime write to the same collections, preventing
 * data inconsistency and race conditions.
 */
public class ApiCollectionResolver {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionResolver.class);

    // Track which collections we've already created (to avoid redundant HTTP calls)
    // Uses Set instead of Bloom filter because false positives would cause orphaned APIs
    private final Set<String> createdCollections = ConcurrentHashMap.newKeySet();

    // ClientActor for collection creation (reuses existing database-abstractor APIs)
    private final ClientActor clientActor;

    public ApiCollectionResolver(ClientActor clientActor) {
        this.clientActor = clientActor;
    }

    /**
     * Pre-populate createdCollections from existing collections in DB.
     * Called on startup to avoid redundant creation attempts.
     *
     * @param existingCollections List of existing API collections from database
     */
    public void prePopulateCollections(List<ApiCollection> existingCollections) {
        for (ApiCollection col : existingCollections) {
            String key = buildCollectionKey(col);
            createdCollections.add(key);
        }

        int sizeKB = createdCollections.size() * 40 / 1024;
        loggerMaker.infoAndAddToDb(String.format(
            "Pre-populated %d collections in cache (~%d KB)",
            createdCollections.size(), sizeKB));
    }

    /**
     * Resolve API collection ID from HttpResponseParams.
     * Matches mini-runtime's HttpCallParser.createApiCollectionId() logic exactly.
     *
     * Flow:
     * 1. Extract hostname from "host" header
     * 2. Check if hostname-based collection should be used (MIRRORING + valid hostname)
     * 3. If hostname mode: collectionId = hostname.hashCode()
     *    Else: collectionId = vxlanId
     * 4. Create collection if not in cache
     * 5. Return collectionId
     *
     * @param httpResponseParam Parsed HTTP response parameters
     * @return API collection ID
     */
    public int resolveApiCollectionId(HttpResponseParams httpResponseParam) {
        // Extract hostname using shared utility from HttpCallParser
        String hostName = HttpCallParser.getHostnameForCollection(httpResponseParam);

        // Handle special characters in hostname
        if (hostName != null && RuntimeUtil.hasSpecialCharacters(hostName)) {
            hostName = "Special_Char_Host";
        }

        int vxlanId = httpResponseParam.getRequestParams().getApiCollectionId();
        String vpcId = System.getenv("VPC_ID");
        List<CollectionTags> tagList = CollectionTags.convertTagsFormat(httpResponseParam.getTags());

        int apiCollectionId;

        // Check if we should use hostname-based collection
        if (HttpCallParser.useHostCondition(hostName, httpResponseParam.getSource())) {
            // Mode A: Hostname-based collection
            hostName = hostName.toLowerCase().trim();
            String key = "host:" + hostName;

            if (createdCollections.contains(key)) {
                // Collection already created in this session
                apiCollectionId = hostName.hashCode();
            } else {
                // Create new hostname-based collection
                int id = hostName.hashCode();

                try {
                    // Create collection with retry logic (tries id, id+1, id+2, ... up to 100 attempts)
                    apiCollectionId = createCollectionBasedOnHostName(id, hostName, vpcId, tagList);
                    createdCollections.add(key);  // Cache it

                    loggerMaker.infoAndAddToDb(String.format(
                        "Created hostname-based collection %d for '%s'", apiCollectionId, hostName));
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Failed to create collection for host: " + hostName);
                    // Fallback to vxlanId
                    createCollectionSimpleForVpc(vxlanId, vpcId, tagList);
                    createdCollections.add("vxlan:" + vxlanId);
                    apiCollectionId = vxlanId;
                }
            }
        } else {
            // Mode B: VxlanId-based collection
            String key = "vxlan:" + vxlanId;

            if (!createdCollections.contains(key)) {
                // Create vxlan-based collection
                createCollectionSimpleForVpc(vxlanId, vpcId, tagList);
                createdCollections.add(key);  // Cache it
            }

            apiCollectionId = vxlanId;
        }

        return apiCollectionId;
    }

    /**
     * Build cache key from ApiCollection.
     *
     * @param col API collection from database
     * @return Cache key (either "host:hostname" or "vxlan:id")
     */
    private String buildCollectionKey(ApiCollection col) {
        if (col.getHostName() != null && !col.getHostName().isEmpty()) {
            return "host:" + col.getHostName();
        } else {
            return "vxlan:" + col.getId();
        }
    }

    /**
     * Get current cache size (for monitoring).
     *
     * @return Number of collections in cache
     */
    public int getCacheSize() {
        return createdCollections.size();
    }

    /**
     * Create hostname-based collection with retry logic.
     * Tries id, id+1, id+2, ... up to 100 attempts to handle hash collisions.
     *
     * @param id Base collection ID (typically hostname.hashCode())
     * @param host Hostname for the collection
     * @param vpcId VPC identifier
     * @param tags Collection tags
     * @return Created collection ID
     * @throws Exception if all 100 attempts fail
     */
    private int createCollectionBasedOnHostName(int id, String host, String vpcId, List<CollectionTags> tags) throws Exception {
        Exception lastException = null;

        for (int i = 0; i < 100; i++) {
            int collectionId = id + i;
            try {
                clientActor.createCollectionForHostAndVpc(host, collectionId, vpcId, tags);
                return collectionId;
            } catch (Exception e) {
                lastException = e;
                // Try next ID on collision
            }
        }

        throw new Exception("Failed to create collection after 100 attempts for host: " + host, lastException);
    }

    /**
     * Create vxlan-based collection.
     * Thin wrapper around clientActor for consistency.
     *
     * @param vxlanId VXLAN identifier
     * @param vpcId VPC identifier
     * @param tags Collection tags
     */
    private void createCollectionSimpleForVpc(int vxlanId, String vpcId, List<CollectionTags> tags) {
        clientActor.createCollectionSimpleForVpc(vxlanId, vpcId, tags);
    }
}
