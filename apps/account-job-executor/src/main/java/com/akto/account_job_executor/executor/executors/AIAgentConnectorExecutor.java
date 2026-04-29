package com.akto.account_job_executor.executor.executors;

import com.akto.account_job_executor.client.CyborgApiClient;
import com.akto.account_job_executor.executor.AccountJobExecutor;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.tracing.SpanDao;
import com.akto.dao.tracing.TraceDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.tracing.model.Span;
import com.akto.dto.tracing.model.Trace;
import com.akto.dto.jobs.AccountJob;
import com.akto.jobs.executors.BinaryDownloader;
import com.akto.jobs.executors.BinaryExecutor;
import com.akto.jobs.executors.clickup.ClickupApiClient;
import com.akto.jobs.executors.clickup.ClickupTraceParser;
import com.akto.jobs.executors.salesforce.IngestorClient;
import com.akto.jobs.executors.salesforce.SalesforceApiClient;
import com.akto.jobs.executors.salesforce.SalesforceDataTransformer;
import com.akto.jobs.executors.salesforce.SalesforceStateManager;
import com.akto.log.LoggerMaker;
import com.akto.tracing.ServiceGraphBuilder;
import com.akto.util.Constants;
import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.model.Filters;

import java.io.File;
import java.net.URI;
import java.util.*;

import static com.akto.jobs.executors.AIAgentConnectorConstants.*;

/**
 * Executor for AI Agent Connector jobs.
 * Handles execution of AI Agent Connector integrations:
 * - N8N, Langchain, Copilot Studio, Databricks, Snowflake: Binary-based execution
 * - Salesforce: Direct Java implementation for fetching and pushing chat data
 *
 * This is a singleton executor - use AIAgentConnectorExecutor.INSTANCE to access it.
 */
public class AIAgentConnectorExecutor extends AccountJobExecutor {

    public static final AIAgentConnectorExecutor INSTANCE = new AIAgentConnectorExecutor();

    private static final LoggerMaker logger = new LoggerMaker(AIAgentConnectorExecutor.class);

    /**
     * Private constructor for singleton pattern.
     */
    private AIAgentConnectorExecutor() {
    }

    /**
     * Execute AI Agent Connector job.
     * Processes connector-specific logic based on subType (N8N, LANGCHAIN, COPILOT_STUDIO).
     *
     * @param job The AccountJob to execute
     * @throws Exception If job execution fails
     */
    @Override
    protected void runJob(AccountJob job) throws Exception {
        logger.info("Executing AI Agent Connector job: jobId={}, subType={}",
            job.getId(), job.getSubType());

        // Extract job configuration
        Map<String, Object> config = job.getConfig();
        String subType = job.getSubType();

        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("Job config is null or empty for job: " + job.getId());
        }

        if (subType == null || subType.isEmpty()) {
            throw new IllegalArgumentException("Job subType is null or empty for job: " + job.getId());
        }

        // Execute based on connector type
        switch (subType) {
            case "N8N":
                executeN8NConnector(job, config);
                break;

            case "LANGCHAIN":
            case "LANGSMITH":
                executeLangchainConnector(job, config);
                break;

            case "COPILOT_STUDIO":
                executeCopilotStudioConnector(job, config);
                break;

            case "SNOWFLAKE":
                executeSnowflakeConnector(job, config);
                break;

            case "DATABRICKS":
                executeDatabricksConnector(job, config);
                break;

            case "SALESFORCE":
                executeSalesforceConnector(job, config);
                break;

            case "ANTHROPIC":
                executeAnthropicConnector(job, config);
                break;

            case "OPENAI":
                executeOpenaiConnector(job, config);
                break;

            case CONNECTOR_TYPE_CLICKUP:
                executeClickupConnector(job, config);
                break;

            default:
                logger.warn("Unknown AI Agent Connector subType: {}. Skipping job execution.", subType);
                throw new IllegalArgumentException("Unsupported AI Agent Connector subType: " + subType);
        }

        logger.info("AI Agent Connector job completed successfully: jobId={}", job.getId());
    }

    /**
     * Execute N8N connector logic.
     * Downloads the N8N shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeN8NConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing N8N connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_N8N);

        logger.info("N8N connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute Langchain/Langsmith connector logic.
     * Downloads the Langchain shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeLangchainConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Langchain connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_LANGCHAIN);

        logger.info("Langchain connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute Copilot Studio connector logic.
     * Downloads the Copilot shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeCopilotStudioConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Copilot Studio connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_COPILOT_STUDIO);

        logger.info("Copilot Studio connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute Snowflake connector logic.
     * Downloads the Snowflake shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeSnowflakeConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Snowflake connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_SNOWFLAKE);

        logger.info("Snowflake connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute Databricks connector logic.
     * Downloads the Databricks shield binary from Azure Storage and executes it with config as env vars.
     */
    private void executeDatabricksConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Databricks connector: jobId={}", job.getId());

        // Execute connector binary with config
        executeBinaryConnector(job, config, BINARY_NAME_DATABRICKS);

        logger.info("Databricks connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute Anthropic connector (enterprise).
     * Downloads anthropic-shield binary and runs with config env vars.
     */
    private void executeAnthropicConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Anthropic connector: jobId={}", job.getId());
        executeBinaryConnector(job, config, BINARY_NAME_ANTHROPIC);
        logger.info("Anthropic connector execution completed: jobId={}", job.getId());
    }

    /**
     * Execute OpenAI connector (enterprise).
     */
    private void executeOpenaiConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing OpenAI connector: jobId={}", job.getId());
        executeBinaryConnector(job, config, BINARY_NAME_OPENAI);
        logger.info("OpenAI connector execution completed: jobId={}", job.getId());
    }

    private void executeClickupConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing ClickUp connector: jobId={}", job.getId());

        String clickupBaseUrl = getConfigValue(config, CONFIG_CLICKUP_BASE_URL);
        String clickupApiToken = getConfigValue(config, CONFIG_CLICKUP_API_TOKEN);
        String clickupWorkspaceId = getConfigValue(config, CONFIG_CLICKUP_WORKSPACE_ID);
        int pageRows = parseIntConfig(config, CONFIG_CLICKUP_PAGE_ROWS, 200);
        int lookbackHours = parseIntConfig(config, CONFIG_CLICKUP_LOOKBACK_HOURS, 24);

        long nowMs = System.currentTimeMillis();
        long fromTimestampMs = loadLastTimestampFromConfig(config, nowMs - (lookbackHours * 60L * 60L * 1000L));
        if (fromTimestampMs >= nowMs) {
            fromTimestampMs = nowMs - (5 * 60 * 1000L);
        }

        ClickupApiClient clickupApiClient = new ClickupApiClient(clickupBaseUrl, clickupApiToken, clickupWorkspaceId, pageRows);
        JsonNode responseNode = clickupApiClient.fetchTraceSummaries(fromTimestampMs, nowMs);
        updateJobHeartbeat(job);

        int apiCollectionId = getOrCreateClickupCollectionId(clickupBaseUrl, clickupWorkspaceId);
        ClickupTraceParser.ParseResult parseResult = new ClickupTraceParser().parse(responseNode, apiCollectionId);

        int traceCount = upsertTraces(parseResult.getTraces());
        int spanCount = upsertSpans(parseResult.getSpans());
        if (!parseResult.getServiceGraphEdges().isEmpty()) {
            ServiceGraphBuilder.getInstance().updateServiceGraph(apiCollectionId, parseResult.getServiceGraphEdges());
        }

        long newCursor = parseResult.getMaxSeenTimestampMs() > 0
            ? Math.max(fromTimestampMs, parseResult.getMaxSeenTimestampMs() - 60_000L)
            : nowMs - 60_000L;
        saveLastTimestampToJob(job, config, newCursor);

        logger.infoAndAddToDb(
            "ClickUp connector completed: workspaceId=" + clickupWorkspaceId +
                ", tracesUpserted=" + traceCount +
                ", spansUpserted=" + spanCount +
                ", serviceGraphEdges=" + parseResult.getServiceGraphEdges().size(),
            LoggerMaker.LogDb.AGENTIC_TESTING
        );
    }

    /**
     * Execute Salesforce connector logic.
     * Fetches AI Agent chat data from Salesforce and pushes to Akto ingestion API.
     */
    private void executeSalesforceConnector(AccountJob job, Map<String, Object> config) throws Exception {
        logger.info("Executing Salesforce connector: jobId={}", job.getId());

        try {
            logger.info("accountId={}", job.getAccountId());

            // Extract and validate configuration
            String salesforceUrl = getConfigValue(config, "SALESFORCE_URL");
            String consumerKey = getConfigValue(config, "SALESFORCE_CONSUMER_KEY");
            String consumerSecret = getConfigValue(config, "SALESFORCE_CONSUMER_SECRET");
            String ingestionUrl = getConfigValue(config, "DATA_INGESTION_SERVICE_URL");
            String ingestionApiKey = getConfigValue(config, "INGESTION_API_KEY");

            logger.info("Configuration loaded: salesforce_url={}, ingestion_url={}",
                salesforceUrl, ingestionUrl);

            // Initialize Salesforce API client
            // Token will be generated on first API call
            logger.infoAndAddToDb(">>> Initializing Salesforce API client (token will be generated on first fetch)");
            SalesforceApiClient salesforceClient = new SalesforceApiClient(
                salesforceUrl,
                "v66.0",
                consumerKey,
                consumerSecret
            );

            SalesforceDataTransformer dataTransformer = new SalesforceDataTransformer(
                salesforceUrl,
                Context.accountId.toString()
            );

            IngestorClient ingestorClient = new IngestorClient(ingestionUrl, ingestionApiKey, 50);

            // Initialize state manager for tracking processed data
            SalesforceStateManager stateManager = new SalesforceStateManager(10000);

            // Load persisted offset from previous execution
            int currentOffset = loadOffsetFromConfig(config);
            stateManager = new SalesforceStateManager(10000);
            // Note: Setting offset explicitly to continue from last position
            for (int i = 0; i < currentOffset; i++) {
                stateManager.incrementOffset(1);
            }
            logger.info(">>> State manager initialized: loading offset={} from previous execution", currentOffset);

            // Fetch data from Salesforce
            logger.infoAndAddToDb(">>> Fetching Salesforce AI Agent chat data (limit=100, offset={})...", LoggerMaker.LogDb.AGENTIC_TESTING);
            long fetchStart = System.currentTimeMillis();
            List<Map<String, Object>> salesforceData = salesforceClient.fetchChatData(100, currentOffset);
            long fetchDuration = System.currentTimeMillis() - fetchStart;

            if (salesforceData.isEmpty()) {
                logger.infoAndAddToDb("No chat data found in Salesforce. Exiting.");
                return;
            }

            logger.info(">>> Fetch completed in {}ms: {} records received", fetchDuration, salesforceData.size());

            // Mark fetched records as processed
            List<String> fetchedIds = new ArrayList<>();
            for (Map<String, Object> record : salesforceData) {
                Object id = record.get("id");
                if (id != null) {
                    fetchedIds.add(id.toString());
                }
            }
            stateManager.markMultipleAsProcessed(fetchedIds);
            logger.info(">>> Marked {} records as processed", fetchedIds.size());

            // Transform data to Akto format
            logger.info(">>> Transforming {} records to Akto format...", salesforceData.size());
            long transformStart = System.currentTimeMillis();
            List<Map<String, Object>> aktoData = dataTransformer.transformToAktoFormat(salesforceData);
            long transformDuration = System.currentTimeMillis() - transformStart;

            if (aktoData.isEmpty()) {
                logger.warn("No entries after transformation. Exiting.");
                return;
            }

            logger.info(">>> Transform completed in {}ms: {} Akto entries created",
                transformDuration, aktoData.size());

            // Push data to Akto ingestion service
            logger.info(">>> PUSHING {} ENTRIES TO AKTO INGESTION SERVICE...", aktoData.size());
            long pushStart = System.currentTimeMillis();
            IngestorClient.PushResult result = ingestorClient.pushData(aktoData);
            long pushDuration = System.currentTimeMillis() - pushStart;

            if (result.success) {
                logger.infoAndAddToDb("✓ SUCCESS: Data pushed in {}ms to ingestion service", LoggerMaker.LogDb.AGENTIC_TESTING);
                logger.info("  Batches: {} total, {} successful, {} failed",
                    result.totalBatches, result.successfulBatches, result.failedBatches);

                // Update offset and save to config for next execution
                int newOffset = currentOffset + salesforceData.size();
                stateManager.incrementOffset(salesforceData.size());
                saveOffsetToConfig(config, newOffset);
            } else {
                logger.error("✗ FAILED: {} of {} batches failed",
                    result.failedBatches, result.totalBatches);
                throw new Exception("Failed to push all batches: " + result.failedBatches + " failed");
            }

        } catch (Exception e) {
            logger.error("Error in Salesforce connector: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Extract configuration value with validation.
     */
    private String getConfigValue(Map<String, Object> config, String key) throws Exception {
        Object value = config.get(key);
        if (value == null || value.toString().isEmpty()) {
            throw new IllegalArgumentException("Missing required config: " + key);
        }
        return value.toString();
    }

    /**
     * Load persisted offset from job config for pagination continuation.
     * This ensures subsequent executions continue from where the previous execution left off.
     */
    private int loadOffsetFromConfig(Map<String, Object> config) {
        Object offsetObj = config.get("SALESFORCE_OFFSET");
        if (offsetObj == null) {
            logger.info("No persisted offset found, starting from offset=0");
            return 0;
        }
        try {
            int offset = Integer.parseInt(offsetObj.toString());
            logger.info("Loaded persisted offset from config: {}", offset);
            return offset;
        } catch (NumberFormatException e) {
            logger.error("Invalid offset in config: {}, resetting to 0", offsetObj);
            return 0;
        }
    }

    /**
     * Save offset to job config for persistence across executions.
     * This allows pagination to continue on the next job execution.
     */
    private void saveOffsetToConfig(Map<String, Object> config, int offset) {
        config.put("SALESFORCE_OFFSET", String.valueOf(offset));
        logger.info("Saved offset to config: {}", offset);
    }

    private long loadLastTimestampFromConfig(Map<String, Object> config, long defaultTimestamp) {
        Object tsObj = config.get(CONFIG_CLICKUP_LAST_TIMESTAMP);
        if (tsObj == null) {
            return defaultTimestamp;
        }

        try {
            return Long.parseLong(tsObj.toString());
        } catch (NumberFormatException e) {
            logger.error("Invalid ClickUp timestamp in config: {}, using default {}", tsObj, defaultTimestamp);
            return defaultTimestamp;
        }
    }

    private void saveLastTimestampToJob(AccountJob job, Map<String, Object> config, long lastTimestamp) {
        config.put(CONFIG_CLICKUP_LAST_TIMESTAMP, String.valueOf(lastTimestamp));
        Map<String, Object> updates = new HashMap<>();
        updates.put(AccountJob.CONFIG, config);
        updates.put(AccountJob.LAST_UPDATED_AT, Context.now());
        CyborgApiClient.updateJob(job.getId(), updates);
    }

    private int parseIntConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            logger.error("Invalid integer config for {}: {}. Falling back to {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    private int getOrCreateClickupCollectionId(String clickupBaseUrl, String workspaceId) {
        String hostName;
        try {
            hostName = "clickup-" + workspaceId + "." + URI.create(clickupBaseUrl).getHost();
        } catch (Exception e) {
            hostName = "clickup-" + workspaceId + ".clickup.local";
        }

        ApiCollection collection = ApiCollectionsDao.instance.findByHost(hostName);
        if (collection != null) {
            return collection.getId();
        }

        int collectionId = hostName.hashCode();
        ApiCollection createdCollection = ApiCollection.createManualCollection(collectionId, "ClickUp Agent " + workspaceId);
        createdCollection.setHostName(hostName);
        createdCollection.setTagsList(Collections.singletonList(
            new CollectionTags(Context.now(), Constants.AKTO_GEN_AI_TAG, "ClickUp Agent Connector", CollectionTags.TagSource.USER)
        ));
        ApiCollectionsDao.instance.replaceOne(Filters.eq(ApiCollection.ID, collectionId), createdCollection);
        return collectionId;
    }

    private int upsertTraces(List<Trace> traces) {
        if (traces == null || traces.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Trace trace : traces) {
            TraceDao.instance.replaceOne(Filters.eq("_id", trace.getId()), trace);
            count++;
        }
        return count;
    }

    private int upsertSpans(List<Span> spans) {
        if (spans == null || spans.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (Span span : spans) {
            SpanDao.instance.replaceOne(Filters.eq("_id", span.getId()), span);
            count++;
        }
        return count;
    }

    /**
     * Common method to execute any AI Agent Connector binary.
     * Downloads binary from Azure Storage and executes it with config as environment variables.
     *
     * @param job The AccountJob being executed
     * @param config Job configuration containing connector-specific settings
     * @param binaryName Name of the binary to download and execute
     * @throws Exception if download or execution fails
     */
    private void executeBinaryConnector(AccountJob job, Map<String, Object> config, String binaryName) throws Exception {
        logger.info("Executing binary connector: binaryName={}, jobId={}", binaryName, job.getId());

        // Get Azure Storage connection string from environment
        String connectionString = System.getenv(AZURE_CONNECTION_STRING_ENV);
        String blobUrl = System.getenv(AZURE_BLOB_URL_ENV);

        if (connectionString == null && blobUrl == null) {
            throw new Exception("Azure Storage credentials not configured. Set either " +
                AZURE_CONNECTION_STRING_ENV + " or " + AZURE_BLOB_URL_ENV + " environment variable");
        }

        // Download binary from Azure Storage
        File binaryFile;
        if (connectionString != null && !connectionString.isEmpty()) {
            logger.info("Downloading binary using connection string: {}", binaryName);
            binaryFile = BinaryDownloader.downloadBinary(binaryName, connectionString, AZURE_CONTAINER_NAME);
        } else {
            logger.info("Downloading binary using blob URL: {}", binaryName);
            binaryFile = BinaryDownloader.downloadBinaryFromUrl(binaryName, blobUrl);
        }

        // Update heartbeat after download (download might take time)
        updateJobHeartbeat(job);

        // Prepare environment variables for binary execution
        Map<String, String> envVars = new HashMap<>();

        // Add all config values as environment variables
        if (config != null) {
            config.forEach((key, value) -> {
                if (value != null) {
                    envVars.put(key, value.toString());
                }
            });
        }

        // Add AKTO_JWT_TOKEN from DATABASE_ABSTRACTOR_SERVICE_TOKEN (for DIS authentication)
        String aktoJwtToken = System.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN");
        if (aktoJwtToken == null || aktoJwtToken.isEmpty()) {
            throw new Exception("DATABASE_ABSTRACTOR_SERVICE_TOKEN environment variable not set. Required for DIS authentication.");
        }
        envVars.put("AKTO_JWT_TOKEN", aktoJwtToken);

        // Add job metadata
        envVars.put("AKTO_JOB_ID", job.getId().toHexString());
        envVars.put("AKTO_ACCOUNT_ID", String.valueOf(job.getAccountId()));
        envVars.put("AKTO_JOB_TYPE", job.getJobType());
        envVars.put("AKTO_JOB_SUB_TYPE", job.getSubType());

        logger.info("Executing binary with {} environment variables", envVars.size());

        // Execute binary with -once flag and timeout
        // The -once flag tells the binary to run one iteration and exit
        String[] args = new String[] { "-once" };
        BinaryExecutor.ExecutionResult result = BinaryExecutor.executeBinary(
            binaryFile,
            args,
            envVars,
            BINARY_TIMEOUT_SECONDS
        );

        // Update heartbeat after execution
        updateJobHeartbeat(job);

        // Check execution result
        if (!result.isSuccess()) {
            String errorMsg = "Binary execution failed with exit code " + result.getExitCode() +
                ". Output: " + result.getStdout().substring(0, Math.min(500, result.getStdout().length()));
            logger.error("Binary execution failed: {}", errorMsg);
            throw new Exception(errorMsg);
        }

        logger.info("Binary execution successful: binaryName={}, exitCode={}, outputLength={}",
            binaryName, result.getExitCode(), result.getStdout().length());
    }
}
