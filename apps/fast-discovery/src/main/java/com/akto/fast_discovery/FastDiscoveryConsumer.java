package com.akto.fast_discovery;

import com.akto.dto.HttpResponseParams;
import com.akto.fast_discovery.dto.ApiExistenceResult;
import com.akto.fast_discovery.dto.ApiId;
import com.akto.fast_discovery.dto.BulkUpdates;
import com.akto.log.LoggerMaker;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

import java.util.*;

/**
 * FastDiscoveryConsumer - Main processing component for fast API discovery.
 *
 * Implements three-stage detection pipeline:
 * 1. Parse and Bloom filter check (99% filter rate)
 * 2. Batch database verification
 * 3. HTTP POST to Database-Abstractor
 *
 * Achieves <1 second latency for new API discovery.
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
                HttpResponseParams params = FastDiscoveryParser.parseKafkaMessage(record.value());
                if (params == null) {
                    parseErrors++;
                    continue;
                }
                parsedCount++;

                // Resolve API collection ID
                int apiCollectionId = collectionResolver.resolveApiCollectionId(params);
                params.getRequestParams().setApiCollectionId(apiCollectionId);

                // Normalize URL (strip query params)
                String url = FastDiscoveryParser.normalizeUrl(params.getRequestParams().getURL());
                String method = params.getRequestParams().getMethod();
                String apiKey = FastDiscoveryParser.buildApiKey(apiCollectionId, url, method);

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

        // Stage 2: Batch database verification
        Set<String> existingInDB = checkBatchExistsInDB(candidates.keySet());
        candidates.keySet().removeAll(existingInDB);

        loggerMaker.infoAndAddToDb("Stage 2: " + existingInDB.size() + " APIs exist in DB, " +
                candidates.size() + " new APIs to insert");

        if (candidates.isEmpty()) {
            loggerMaker.infoAndAddToDb("All candidates already exist in database");
            return;
        }

        // Stage 3: Send to Database-Abstractor via HTTP POST
        sendToDbAbstractor(candidates);

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        loggerMaker.infoAndAddToDb("Batch processing completed in " + durationMs + "ms, discovered " +
                candidates.size() + " new APIs");
    }

    /**
     * Stage 2: Check which APIs exist in database via database-abstractor HTTP API.
     */
    private Set<String> checkBatchExistsInDB(Set<String> apiKeys) {
        // Convert API keys to API IDs for batch check
        List<ApiId> apiIdsToCheck = new ArrayList<>();
        for (String apiKey : apiKeys) {
            String[] parts = apiKey.split(" ");
            if (parts.length != 3) {
                loggerMaker.errorAndAddToDb("Invalid API key format: " + apiKey);
                continue;
            }

            try {
                int apiCollectionId = Integer.parseInt(parts[0]);
                String url = parts[1];
                String method = parts[2];
                apiIdsToCheck.add(new ApiId(apiCollectionId, url, method));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Failed to parse API key: " + apiKey + " - " + e.getMessage());
            }
        }

        // Call database-abstractor HTTP API to check which APIs exist
        try {
            List<ApiExistenceResult> results = dbAbstractorClient.checkApisExist(apiIdsToCheck);

            Set<String> existing = new HashSet<>();
            for (ApiExistenceResult result : results) {
                if (result.isExists()) {
                    String apiKey = FastDiscoveryParser.buildApiKey(
                            result.getApiCollectionId(),
                            result.getUrl(),
                            result.getMethod()
                    );
                    existing.add(apiKey);
                }
            }

            return existing;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Failed to check API existence: " + e.getMessage());
            // Return all as existing on error to avoid inserting duplicates
            // Better to skip insertion than risk duplicate data
            return apiKeys;
        }
    }

    /**
     * Stage 3: Send new APIs to database-abstractor for insertion.
     */
    private void sendToDbAbstractor(Map<String, HttpResponseParams> newApis) {
        // Convert to BulkUpdates format for single_type_info (host header)
        List<BulkUpdates> stiWrites = buildStiWrites(newApis);

        // Convert to BulkUpdates format for api_info
        List<BulkUpdates> apiInfoWrites = buildApiInfoWrites(newApis);

        try {
            // Call database-abstractor HTTP API
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
            String url = FastDiscoveryParser.normalizeUrl(params.getRequestParams().getURL());
            String method = params.getRequestParams().getMethod();

            // Build filters for single_type_info host header entry
            Map<String, Object> filters = new HashMap<>();
            Map<String, Object> id = new HashMap<>();
            id.put("apiCollectionId", apiCollectionId);
            id.put("url", url);
            id.put("method", method);
            id.put("responseCode", -1);
            id.put("isHeader", true);
            id.put("param", "host");
            id.put("subType", "GENERIC");
            id.put("isUrlParam", false);
            filters.put("_id", id);

            // Build updates
            List<String> updates = new ArrayList<>();
            updates.add(String.format("{\"field\": \"timestamp\", \"val\": %d, \"op\": \"set\"}", timestamp));
            updates.add(String.format("{\"field\": \"collectionIds\", \"val\": [%d], \"op\": \"set\"}", apiCollectionId));
            updates.add("{\"field\": \"count\", \"val\": 1, \"op\": \"set\"}");
            updates.add("{\"field\": \"domain\", \"val\": \"ANY\", \"op\": \"set\"}");

            BulkUpdates bulkUpdate = new BulkUpdates(filters, updates);
            writes.add(bulkUpdate);
        }

        return writes;
    }

    /**
     * Build BulkUpdates for api_info collection.
     */
    private List<BulkUpdates> buildApiInfoWrites(Map<String, HttpResponseParams> newApis) {
        List<BulkUpdates> writes = new ArrayList<>();
        long timestamp = System.currentTimeMillis() / 1000;

        for (HttpResponseParams params : newApis.values()) {
            int apiCollectionId = params.getRequestParams().getApiCollectionId();
            String url = FastDiscoveryParser.normalizeUrl(params.getRequestParams().getURL());
            String method = params.getRequestParams().getMethod();

            // Build filters for api_info
            Map<String, Object> filters = new HashMap<>();
            Map<String, Object> id = new HashMap<>();
            id.put("apiCollectionId", apiCollectionId);
            id.put("url", url);
            id.put("method", method);
            filters.put("_id", id);

            // Build updates
            List<String> updates = new ArrayList<>();
            updates.add(String.format("{\"field\": \"lastSeen\", \"val\": %d, \"op\": \"set\"}", timestamp));
            updates.add(String.format("{\"field\": \"discoveredTimestamp\", \"val\": %d, \"op\": \"setOnInsert\"}", timestamp));
            updates.add(String.format("{\"field\": \"collectionIds\", \"val\": [%d], \"op\": \"set\"}", apiCollectionId));
            updates.add("{\"field\": \"allAuthTypesFound\", \"val\": [], \"op\": \"set\"}");
            updates.add("{\"field\": \"apiAccessTypes\", \"val\": [], \"op\": \"set\"}");
            updates.add("{\"field\": \"apiType\", \"val\": \"REST\", \"op\": \"set\"}");

            BulkUpdates bulkUpdate = new BulkUpdates(filters, updates);
            writes.add(bulkUpdate);
        }

        return writes;
    }
}
