package com.akto.runtime;

import com.akto.DaoInit;
import com.akto.dao.AgentTrafficLogDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
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
    private static final int WINDOW_SECONDS = 24 * 60 * 60; // 24 hours
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
     * Fetches prompts from agent_traffic_logs collection for the last 24 hours
     */
    private void processAgentCollection(ApiCollection apiCollection) {
        try {
            logger.info("Processing agent collection: {} (ID: {})", 
                apiCollection.getName(), apiCollection.getId());

            // Calculate 24-hour window timestamps
            int currentTimestamp = Context.now();
            int startTimestamp = currentTimestamp - WINDOW_SECONDS;

            // Fetch prompts from agent_traffic_logs collection
            List<String> prompts = AgentTrafficLogDao.instance.fetchPromptsForCollection(
                apiCollection.getId(), 
                startTimestamp, 
                currentTimestamp
            );

            if (CollectionUtils.isEmpty(prompts)) {
                logger.info("No prompts found in agent_traffic_logs for collection: {} in last 24 hours", 
                    apiCollection.getId());
                return;
            }

            if (prompts.size() < MIN_PROMPTS_FOR_DETECTION) {
                logger.info("Skipping collection {} due to insufficient prompts in 24h window. Found: {}, Required: {}",
                        apiCollection.getId(), prompts.size(), MIN_PROMPTS_FOR_DETECTION);
                return;
            }

            logger.info("Found {} prompts for collection {} in last 24 hours", 
                prompts.size(), apiCollection.getId());

            // Sample prompts if collection is too large
            List<String> processedPrompts = samplePromptsIfNeeded(prompts);
            logger.debug("Processing {} prompts for base prompt detection", processedPrompts.size());

            String detectedBasePrompt = detectUsingExternalServiceWithBatching(processedPrompts);
            
            if (StringUtils.isBlank(detectedBasePrompt)) {
                logger.warnAndAddToDb("External base prompt detection failed for collection " + apiCollection.getId()
                        + ". No base prompt detected.", LogDb.RUNTIME);
                return;
            }

            // Update the collection with detected base prompt
            updateCollectionWithBasePrompt(apiCollection, detectedBasePrompt);
            logger.info("Successfully detected and updated base prompt for collection: {}", 
                apiCollection.getId());

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
            logger.errorAndAddToDb("Error during external base prompt detection: " + e.getMessage(), LogDb.RUNTIME);
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
     * Update API collection with detected base prompt
     */
    private void updateCollectionWithBasePrompt(ApiCollection apiCollection, String basePrompt) {
        try {
            Bson filter = Filters.eq(ApiCollection.ID, apiCollection.getId());
            Bson update = Updates.set(ApiCollection.DETECTED_BASE_PROMPT, basePrompt);
            
            ApiCollectionsDao.instance.updateOne(filter, update);
            
            logger.info("Updated collection {} with base prompt: {}", 
                apiCollection.getId(), 
                basePrompt.length() > 100 ? basePrompt.substring(0, 100) + "..." : basePrompt);
                
        } catch (Exception e) {
            logger.error("Error updating collection with base prompt: " + apiCollection.getId(), e);
        }
    }

    /**
     * Main method to run the service standalone
     * Usage:
     *   export AKTO_MONGO_CONN=mongodb://localhost:27017
     *   export BASE_PROMPT_DETECTOR_URL=http://localhost:8093/detect (optional)
     *   export AKTO_ACCOUNT_ID=1000000 (optional)
     *   java -cp <classpath> com.akto.runtime.AgentBasePromptDetectionService
     */
    public static void main(String[] args) {
        try {
            // Get MongoDB connection string from environment or use default
            String mongoURI = System.getenv("AKTO_MONGO_CONN");
            if (mongoURI == null || mongoURI.isEmpty()) {
                mongoURI = "mongodb://localhost:27017";
                logger.info("AKTO_MONGO_CONN not set, using default: " + mongoURI);
            }
            
            // Initialize DAOs with MongoDB connection
            DaoInit.init(new ConnectionString(mongoURI));
            logger.info("Connected to MongoDB: " + mongoURI);
            
            // Set account context
            String accountIdStr = System.getenv("AKTO_ACCOUNT_ID");
            if (accountIdStr != null && !accountIdStr.isEmpty()) {
                try {
                    Context.accountId.set(Integer.parseInt(accountIdStr));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid AKTO_ACCOUNT_ID, using default");
                    Context.accountId.set(1_000_000);
                }
            } else {
                Context.accountId.set(1_000_000);
            }
            
            logger.info("Using account ID: " + Context.accountId.get());
            logger.info("Starting Agent Base Prompt Detection Service...");
            
            // Check Python service URL
            String pythonServiceUrl = System.getenv("BASE_PROMPT_DETECTOR_URL");
            if (pythonServiceUrl != null && !pythonServiceUrl.isEmpty()) {
                logger.info("Python service URL: " + pythonServiceUrl);
            } else {
                logger.warn("BASE_PROMPT_DETECTOR_URL not set, detection will fail");
            }
            
            // Run the detection service
            AgentBasePromptDetectionService service = new AgentBasePromptDetectionService();
            service.runJob();
            
            logger.info("Base prompt detection completed!");
            
        } catch (Exception e) {
            logger.error("Error running base prompt detection: " + e.getMessage(), e);
            System.exit(1);
        }
    }
}
