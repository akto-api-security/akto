package com.akto.fast_discovery;

import com.akto.data_actor.ClientActor;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.log.LoggerMaker;
import com.akto.runtime.parser.SampleParser;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import java.util.*;

/**
 * FastDiscoveryConsumer - Main processing component for fast API discovery.
 *
 * Implements two-stage detection pipeline:
 * 1. Parse and Bloom filter check (99% filter rate)
 * 2. Send to Database-Abstractor → Kafka → MongoDB (upsert handles duplicates)
 *
 * Achieves <500ms latency for new API discovery.
 */
public class FastDiscoveryConsumer {

    private static final LoggerMaker loggerMaker = new LoggerMaker(FastDiscoveryConsumer.class);

    private final BloomFilterManager bloomFilter;
    private final ApiCollectionResolver collectionResolver;
    private final DatabaseAbstractorClient dbAbstractorClient;

    public FastDiscoveryConsumer(
            BloomFilterManager bloomFilter,
            ApiCollectionResolver collectionResolver,
            DatabaseAbstractorClient dbAbstractorClient
    ) {
        this.bloomFilter = bloomFilter;
        this.collectionResolver = collectionResolver;
        this.dbAbstractorClient = dbAbstractorClient;
    }

    /**
     * Process batch of Kafka messages using three-stage detection pipeline.
     *
     * @param records Kafka consumer records
     */
    public void processBatch(ConsumerRecords<String, String> records) {
        if (records.isEmpty()) {
            return;
        }

        loggerMaker.infoAndAddToDb("Processing batch of " + records.count() + " messages");
        long startTime = System.currentTimeMillis();

        // Stage 1: Parse and Bloom filter check
        Map<String, HttpResponseParams> candidates = new HashMap<>();
        int parsedCount = 0;
        int parseErrors = 0;

        for (ConsumerRecord<String, String> record : records) {
            try {
                // Parse message
                HttpResponseParams params = SampleParser.parseSampleMessage(record.value());
                if (params == null) {
                    parseErrors++;
                    continue;
                }
                parsedCount++;

                // Resolve API collection ID
                int apiCollectionId = collectionResolver.resolveApiCollectionId(params);
                params.getRequestParams().setApiCollectionId(apiCollectionId);

                // Normalize URL (strip query params) and build API key
                String url = normalizeUrl(params.getRequestParams().getURL());
                String method = params.getRequestParams().getMethod();
                String apiKey = apiCollectionId + " " + url + " " + method;

                // Bloom filter check
                if (!bloomFilter.mightContain(apiKey)) {
                    candidates.put(apiKey, params);
                }

            } catch (Exception e) {
                parseErrors++;
                loggerMaker.errorAndAddToDb("Failed to parse message: " + e.getMessage());
            }
        }

        loggerMaker.infoAndAddToDb("Stage 1: Parsed " + parsedCount + " messages (" + parseErrors + " errors), " +
                candidates.size() + " candidates after Bloom filter");

        if (candidates.isEmpty()) {
            loggerMaker.infoAndAddToDb("No new APIs detected in this batch");
            return;
        }

        // Stage 2: Send to Database-Abstractor → Kafka → MongoDB (upsert handles duplicates atomically)
        sendToDbAbstractor(candidates);

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        loggerMaker.infoAndAddToDb("Batch processing completed in " + durationMs + "ms, discovered " +
                candidates.size() + " new APIs");
    }

    /**
     * Stage 3: Send new APIs to database-abstractor for insertion.
     */
    private void sendToDbAbstractor(Map<String, HttpResponseParams> newApis) {
        // Convert to BulkUpdates format for single_type_info (host header)
        List<BulkUpdates> stiWrites = buildStiWrites(newApis);

        // Convert to BulkUpdates format for api_info
        List<BulkUpdates> apiInfoWrites = buildApiInfoWrites(newApis);

        // Extract unique collection IDs from new APIs
        List<Integer> collectionIds = new ArrayList<>();
        for (HttpResponseParams params : newApis.values()) {
            int collectionId = params.getRequestParams().getApiCollectionId();
            if (collectionId > 0 && !collectionIds.contains(collectionId)) {
                collectionIds.add(collectionId);
            }
        }

        try {
            // Ensure api_collection entries exist first
            if (!collectionIds.isEmpty()) {
                dbAbstractorClient.ensureCollections(collectionIds);
            }

            // Call database-abstractor HTTP API to write APIs
            dbAbstractorClient.bulkWriteSti(stiWrites);
            dbAbstractorClient.bulkWriteApiInfo(apiInfoWrites);

            // Update Bloom filter (optimistic - assume request succeeded)
            newApis.keySet().forEach(bloomFilter::add);

            loggerMaker.infoAndAddToDb("Successfully sent " + newApis.size() + " new APIs to database-abstractor");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to send APIs to database-abstractor: " + e.getMessage());
            e.printStackTrace();
            // Don't update Bloom filter on failure - will retry on next batch
        }
    }

    /**
     * Build BulkUpdates for single_type_info collection (host header entry).
     */
    private List<BulkUpdates> buildStiWrites(Map<String, HttpResponseParams> newApis) {
        List<BulkUpdates> writes = new ArrayList<>();
        long timestamp = System.currentTimeMillis() / 1000;

        for (HttpResponseParams params : newApis.values()) {
            int apiCollectionId = params.getRequestParams().getApiCollectionId();
            String url = normalizeUrl(params.getRequestParams().getURL());
            String method = params.getRequestParams().getMethod();

            // Build filters for single_type_info host header entry (flat structure, not nested under _id)
            Map<String, Object> filters = new HashMap<>();
            filters.put("apiCollectionId", apiCollectionId);
            filters.put("url", url);
            filters.put("method", method);
            filters.put("responseCode", -1);
            filters.put("isHeader", true);
            filters.put("param", "host");
            filters.put("subType", "GENERIC");
            filters.put("isUrlParam", false);

            // Build updates
            ArrayList<String> updates = new ArrayList<>();
            updates.add(String.format("{\"field\": \"timestamp\", \"val\": %d, \"op\": \"set\"}", timestamp));
            updates.add(String.format("{\"field\": \"collectionIds\", \"val\": [%d], \"op\": \"set\"}", apiCollectionId));
            updates.add("{\"field\": \"count\", \"val\": 1, \"op\": \"set\"}");

            BulkUpdates bulkUpdate = new BulkUpdates(filters, updates);
            writes.add(bulkUpdate);
        }

        return writes;
    }

    /**
     * Build BulkUpdates for api_info collection.
     * Uses setOnInsert for most fields to avoid overwriting mini-runtime's enriched data.
     * Only lastSeen uses set to track most recent occurrence.
     */
    private List<BulkUpdates> buildApiInfoWrites(Map<String, HttpResponseParams> newApis) {
        List<BulkUpdates> writes = new ArrayList<>();
        long timestamp = System.currentTimeMillis() / 1000;

        for (HttpResponseParams params : newApis.values()) {
            int apiCollectionId = params.getRequestParams().getApiCollectionId();
            String url = normalizeUrl(params.getRequestParams().getURL());
            String method = params.getRequestParams().getMethod();

            // Build filters for api_info
            Map<String, Object> filters = new HashMap<>();
            Map<String, Object> id = new HashMap<>();
            id.put("apiCollectionId", apiCollectionId);
            id.put("url", url);
            id.put("method", method);
            filters.put("_id", id);

            // Build updates
            // Use setOnInsert for most fields to avoid overwriting mini-runtime's richer data
            // Only lastSeen uses set to update timestamp on every occurrence
            ArrayList<String> updates = new ArrayList<>();
            updates.add(String.format("{\"field\": \"lastSeen\", \"val\": %d, \"op\": \"set\"}", timestamp));
            updates.add(String.format("{\"field\": \"discoveredTimestamp\", \"val\": %d, \"op\": \"setOnInsert\"}", timestamp));
            updates.add(String.format("{\"field\": \"collectionIds\", \"val\": [%d], \"op\": \"setOnInsert\"}", apiCollectionId));
            updates.add("{\"field\": \"allAuthTypesFound\", \"val\": [], \"op\": \"setOnInsert\"}");
            updates.add("{\"field\": \"apiAccessTypes\", \"val\": [], \"op\": \"setOnInsert\"}");
            updates.add("{\"field\": \"apiType\", \"val\": \"REST\", \"op\": \"setOnInsert\"}");

            BulkUpdates bulkUpdate = new BulkUpdates(filters, updates);
            writes.add(bulkUpdate);
        }

        return writes;
    }

    /**
     * Normalize URL by stripping query parameters.
     * Example: /api/users?id=123 -> /api/users
     */
    private String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        int queryIndex = url.indexOf('?');
        if (queryIndex != -1) {
            return url.substring(0, queryIndex);
        }
        return url;
    }
}
