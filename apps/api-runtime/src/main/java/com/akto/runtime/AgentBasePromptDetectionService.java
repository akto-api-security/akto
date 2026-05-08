package com.akto.runtime;

import com.akto.dao.AgentTrafficLogDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.AgentTrafficLog;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.URLMethods;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.*;

/**
 * Service to detect base prompts with placeholders from agent traffic.
 * Runs periodically to analyze traffic patterns and update API collections with detected base prompts.
 */
public class AgentBasePromptDetectionService {

    private static final LoggerMaker logger = new LoggerMaker(AgentBasePromptDetectionService.class, LogDb.RUNTIME);
    
    
    private static final int MIN_PROMPTS_FOR_DETECTION = 100;
    private static final int MAX_PROMPTS_PER_REQUEST = 1000; // Batch size for Python service
    private static final int MAX_TOTAL_PROMPTS = 10000; // Max prompts to process per collection (sampling)
    private static final int MAX_REQUEST_SIZE_BYTES = 5 * 1024 * 1024; // 5MB max per HTTP request

    private final BasePromptDetectionClient detectionClient;

    public AgentBasePromptDetectionService() {
        this(new BasePromptDetectionClient());
    }

    AgentBasePromptDetectionService(BasePromptDetectionClient detectionClient) {
        this.detectionClient = detectionClient;
    }

    /**
     * Main job execution method - finds all Gen AI collections and processes them
     * Only processes collections with the "gen-ai" tag
     */
    public void runJob() {
        // Filter at database level for Gen AI collections only
        Bson genAiFilter = Filters.and(
            Filters.exists(ApiCollection.TAGS_STRING),
            Filters.elemMatch(ApiCollection.TAGS_STRING, 
                Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_GEN_AI_TAG))
        );

        List<ApiCollection> agentCollections = ApiCollectionsDao.instance.findAll(
            genAiFilter,
            Projections.exclude("urls", "conditions")
        );

        logger.info("Found {} Gen AI collections for base prompt detection.", agentCollections.size());

        if (agentCollections.isEmpty()) {
            return;
        }

        agentCollections.forEach(this::processAgentCollection);
    }

    /**
     * Process a single agent collection to detect base prompts
     * Processes each endpoint separately using fetchLogsByCollectionAndEndpoint
     */
    private void processAgentCollection(ApiCollection apiCollection) {
        try {
            logger.info("Processing agent collection: {} (ID: {})", 
                apiCollection.getName(), apiCollection.getId());

            // Get unique endpoints that have logs
            Set<String[]> uniqueEndpoints = AgentTrafficLogDao.instance.getUniqueEndpoints(apiCollection.getId());

            if (uniqueEndpoints.isEmpty()) {
                logger.info("No endpoints with prompts found in agent_traffic_logs for collection: {}", 
                    apiCollection.getId());
                return;
            }

            logger.info("Found {} unique endpoints with prompts for collection {}", 
                uniqueEndpoints.size(), apiCollection.getId());

            int updatedCount = 0;
            int skippedCount = 0;

            // Process each endpoint separately
            for (String[] endpoint : uniqueEndpoints) {
                String url = endpoint[0];
                String methodStr = endpoint[1];
                
                try {
                    // Fetch logs for this specific endpoint
                    List<AgentTrafficLog> logs = AgentTrafficLogDao.instance.fetchLogsByCollectionAndEndpoint(
                        apiCollection.getId(), url, methodStr);

                    if (logs.isEmpty()) {
                        continue;
                    }

                    // Extract prompts for this endpoint
                    List<String> prompts = new ArrayList<>();
                    for (AgentTrafficLog log : logs) {
                        String requestPayload = log.getRequestPayload();
                        if (requestPayload != null && !requestPayload.trim().isEmpty()) {
                            prompts.add(requestPayload);
                        }
                    }

                    if (prompts.size() < MIN_PROMPTS_FOR_DETECTION) {
                        logger.debug("Skipping endpoint {} {} due to insufficient prompts. Found: {}, Required: {}",
                                url, methodStr, prompts.size(), MIN_PROMPTS_FOR_DETECTION);
                        skippedCount++;
                        continue;
                    }

                    // Sample prompts if needed
                    List<String> processedPrompts = samplePromptsIfNeeded(prompts);
                    logger.debug("Processing {} prompts for endpoint {} {}", 
                        processedPrompts.size(), url, methodStr);

                    // Detect base prompt for this endpoint
                    String detectedBasePrompt = detectUsingExternalServiceWithBatching(processedPrompts);
                    
                    if (StringUtils.isBlank(detectedBasePrompt)) {
                        logger.debug("No base prompt detected for endpoint {} {}", url, methodStr);
                        skippedCount++;
                        continue;
                    }

                    // Update this specific endpoint
                    URLMethods.Method method = URLMethods.Method.fromString(methodStr);
                    if (method != null) {
                        Set<ApiInfoKey> endpointSet = new HashSet<>();
                        endpointSet.add(new ApiInfoKey(apiCollection.getId(), url, method));
                        updateEndpointsWithBasePrompt(apiCollection.getId(), endpointSet, detectedBasePrompt);
                        updatedCount++;
                    } else {
                        logger.warn("Failed to parse method: {} for URL: {}", methodStr, url);
                        skippedCount++;
                    }

                } catch (Exception e) {
                    logger.error("Error processing endpoint {} {} in collection {}: {}", 
                        url, methodStr, apiCollection.getId(), e.getMessage(), e);
                    skippedCount++;
                }
            }

            logger.info("Completed processing collection {}. Updated: {}, Skipped: {}", 
                apiCollection.getId(), updatedCount, skippedCount);

        } catch (Exception e) {
            logger.error("Error processing agent collection: " + apiCollection.getId(), e);
        }
    }

    /**
     * Sample prompts if collection is too large to avoid memory and processing issues
     */
    private List<String> samplePromptsIfNeeded(List<String> prompts) {
        if (prompts.size() <= MAX_TOTAL_PROMPTS) {
            return prompts;
        }

        logger.info("Collection has {} prompts, sampling {} for processing", 
            prompts.size(), MAX_TOTAL_PROMPTS);

        // Use systematic sampling: take every Nth prompt
        List<String> sampled = new ArrayList<>(MAX_TOTAL_PROMPTS);
        double step = (double) prompts.size() / MAX_TOTAL_PROMPTS;
        
        for (int i = 0; i < MAX_TOTAL_PROMPTS; i++) {
            int index = (int) (i * step);
            sampled.add(prompts.get(index));
        }

        return sampled;
    }

    /**
     * Detect base prompt using external Python service with batching for large prompt lists
     * Splits prompts into batches based on count and size limits, then merges results
     */
    private String detectUsingExternalServiceWithBatching(List<String> prompts) {
        if (prompts.size() <= MAX_PROMPTS_PER_REQUEST && estimateRequestSize(prompts) <= MAX_REQUEST_SIZE_BYTES) {
            // Small enough, send directly
            return detectUsingExternalService(prompts);
        }

        logger.info("Large prompt set detected ({} prompts, ~{} bytes). Processing in batches", 
            prompts.size(), estimateRequestSize(prompts));

        // Process in batches and collect results
        List<String> detectedBasePrompts = new ArrayList<>();
        int batchCount = 0;
        int i = 0;

        while (i < prompts.size()) {
            List<String> batch = new ArrayList<>();
            int batchSize = 0;
            int batchStart = i;

            // Build batch respecting both count and size limits
            while (i < prompts.size() && batch.size() < MAX_PROMPTS_PER_REQUEST) {
                String prompt = prompts.get(i);
                int promptSize = estimateStringSize(prompt);
                
                // Check if adding this prompt would exceed size limit
                if (batchSize + promptSize > MAX_REQUEST_SIZE_BYTES && !batch.isEmpty()) {
                    break; // Batch is full
                }

                batch.add(prompt);
                batchSize += promptSize;
                i++;
            }

            if (batch.isEmpty()) {
                break; // No more prompts to process
            }

            batchCount++;
            logger.debug("Processing batch {} (prompts {}-{}, ~{} bytes)", 
                batchCount, batchStart + 1, i, batchSize);

            String batchResult = detectUsingExternalService(batch);
            if (StringUtils.isNotBlank(batchResult)) {
                detectedBasePrompts.add(batchResult);
            }
        }

        if (detectedBasePrompts.isEmpty()) {
            logger.warn("No base prompts detected in any batch");
            return null;
        }

        String mostCommon = findMostCommonBasePrompt(detectedBasePrompts);
        logger.info("Merged {} batch results into final base prompt", detectedBasePrompts.size());
        
        return mostCommon;
    }

    /**
     * Estimate request size in bytes (rough approximation for JSON)
     */
    private int estimateRequestSize(List<String> prompts) {
        int size = 20; // JSON overhead: {"prompts":[]}
        for (String prompt : prompts) {
            size += estimateStringSize(prompt) + 2; // +2 for quotes and comma
        }
        return size;
    }

    /**
     * Estimate string size in bytes (UTF-8 encoding)
     */
    private int estimateStringSize(String str) {
        if (str == null) {
            return 0;
        }
        // Rough estimate: most characters are 1 byte, some are 2-4 bytes
        // Using average of 1.5 bytes per character for safety
        return (int) (str.length() * 1.5);
    }

    /**
     * Detect base prompt using external Python service (single batch)
     */
    private String detectUsingExternalService(List<String> prompts) {
        try {
            BasePromptDetectionClient.DetectionResult result = detectionClient.detectBasePrompt(prompts);
            if (result == null) {
                logger.warn("Python service returned null result");
                return null;
            }

            String basePrompt = result.getBasePrompt();
            logger.debug("Python service response received. Confidence: {}", result.getConfidence());

            if (!CollectionUtils.isEmpty(result.getAnomalies())) {
                logger.info("Detected {} anomalies for base prompt detection run.", result.getAnomalies().size());
            }

            return basePrompt;
        } catch (Exception e) {
            logger.error("Error during external base prompt detection: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Find the most common base prompt from multiple batch results using majority vote
     */
    private String findMostCommonBasePrompt(List<String> basePrompts) {
        if (basePrompts.size() == 1) {
            return basePrompts.get(0);
        }

        // Count frequencies
        Map<String, Integer> frequency = new HashMap<>();
        for (String prompt : basePrompts) {
            frequency.put(prompt, frequency.getOrDefault(prompt, 0) + 1);
        }

        // Find most frequent
        String mostCommon = frequency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(basePrompts.get(0));

        int count = frequency.get(mostCommon);
        if (count < basePrompts.size() / 2) {
            logger.warn("No clear majority in batch results. Using most common ({} out of {} batches)", 
                count, basePrompts.size());
        }

        return mostCommon;
    }

    /**
     * Update only the specific ApiInfo endpoints that had prompts with detected base prompt.
     * 
     * Note: ApiInfo has a composite key (_id: {apiCollectionId, url, method}).
     * We update only the endpoints that were part of the detection process.
     * 
     * Following PR #3597: base prompt is stored in ApiInfo, not ApiCollection.
     */
    private void updateEndpointsWithBasePrompt(int apiCollectionId, Set<ApiInfoKey> endpoints, String basePrompt) {
        if (endpoints.isEmpty()) {
            logger.warn("No endpoints to update for collection: {}", apiCollectionId);
            return;
        }

        try {
            List<UpdateManyModel<ApiInfo>> updateModels = new ArrayList<>();
            
            // Update each endpoint individually using its composite key
            for (ApiInfoKey endpoint : endpoints) {
                // Create filter for this specific endpoint (apiCollectionId + url + method)
                Bson filter = Filters.and(
                    Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollectionId),
                    Filters.eq(ApiInfo.ID_URL, endpoint.getUrl()),
                    Filters.eq(ApiInfo.ID_METHOD, endpoint.getMethod().name())
                );
                
                Bson update = Updates.set(ApiInfo.DETECTED_BASE_PROMPT, basePrompt);
                
                updateModels.add(new UpdateManyModel<>(
                    filter,
                    update,
                    new UpdateOptions()
                ));
            }
            
            // Bulk update all endpoints
            if (!updateModels.isEmpty()) {
                ApiInfoDao.instance.getMCollection().bulkWrite(
                    updateModels,
                    new BulkWriteOptions().ordered(false)
                );
                
                logger.info("Updated {} ApiInfo endpoints for collection {} with base prompt: {}", 
                    endpoints.size(),
                    apiCollectionId, 
                    basePrompt.length() > 100 ? basePrompt.substring(0, 100) + "..." : basePrompt);
            }
                
        } catch (Exception e) {
            logger.error("Error updating ApiInfo endpoints with base prompt for collection: " + apiCollectionId, e);
        }
    }

 
}
