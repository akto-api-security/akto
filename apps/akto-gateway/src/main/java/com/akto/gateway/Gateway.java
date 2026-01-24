package com.akto.gateway;

import com.akto.dto.IngestDataBatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;


public class Gateway {

    private static final Logger logger = LogManager.getLogger(Gateway.class);
    private static Gateway instance;
    private final GuardrailsClient guardrailsClient;
    private final AktoIngestAdapter aktoIngestAdapter;
    private final AdapterFactory adapterFactory;
    private DataPublisher dataPublisher;

    private Gateway() {
        this.guardrailsClient = new GuardrailsClient();
        this.aktoIngestAdapter = new AktoIngestAdapter();
        this.adapterFactory = new AdapterFactory(guardrailsClient);
        logger.info("Gateway instance initialized with adapter factory (Strategy pattern)");
    }

    /**
     * Get singleton instance of Gateway
     * @return Gateway instance
     */
    public static synchronized Gateway getInstance() {
        if (instance == null) {
            instance = new Gateway();
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> processHttpProxy(Map<String, Object> proxyData) {
        logger.info("Processing HTTP proxy request");

        try {
            String url = (String) proxyData.get("url");
            String path = (String) proxyData.get("path");
            Map<String, Object> request = (Map<String, Object>) proxyData.get("request");
            Map<String, Object> response = (Map<String, Object>) proxyData.get("response");

            // Extract URL query parameters (from the actual HTTP request URL)
            Map<String, Object> urlQueryParams = (Map<String, Object>) proxyData.get("urlQueryParams");

            logger.debug("Request map contents: {}", request);
            logger.debug("URL Query Params: {}", urlQueryParams);

            String method = String.valueOf(request.getOrDefault("method", ""));

            logger.info("Request - Method: {}, URL: {}, Path: {}", method, url, path);

            // Apply guardrails validation if enabled
            GuardrailsValidationResult guardrailsResult = applyGuardrailsValidation(
                url, path, request, response, urlQueryParams);

            // Ingest data if requested (regardless of guardrails result)
            DataIngestionResult ingestionResult = processDataIngestion(proxyData, urlQueryParams);

            // Build and return final response
            return buildFinalResponse(url, path, method, guardrailsResult, ingestionResult);

        } catch (Exception e) {
            logger.error("Error processing HTTP proxy request: {}", e.getMessage(), e);
            return buildErrorResponse("Error processing request: " + e.getMessage());
        }
    }

    /**
     * Applies guardrails validation if enabled
     */
    private GuardrailsValidationResult applyGuardrailsValidation(String url, String path,
                                                                   Map<String, Object> request,
                                                                   Map<String, Object> response,
                                                                   Map<String, Object> urlQueryParams) {
        GuardrailsValidationResult result = new GuardrailsValidationResult();

        boolean shouldApplyGuardrails = adapterFactory.shouldApplyGuardrails(urlQueryParams);
        result.applied = shouldApplyGuardrails;

        if (!shouldApplyGuardrails) {
            result.adapterUsed = "none";
            return result;
        }

        // Select appropriate adapter
        GuardrailsAdapter adapter = adapterFactory.selectAdapter(urlQueryParams);
        result.adapterUsed = adapter.getAdapterName();
        logger.info("Guardrails enabled - using {} adapter", result.adapterUsed);

        // Format the request using the selected adapter
        Map<String, Object> formattedApiRequest = adapter.formatRequest(url, path, request, response);
        logger.debug("Adapter formatted API request: {}", formattedApiRequest);

        // Call guardrails service
        result.guardrailsResponse = guardrailsClient.callValidateRequest(formattedApiRequest);

        return result;
    }

    /**
     * Processes data ingestion if requested
     */
    private DataIngestionResult processDataIngestion(Map<String, Object> proxyData,
                                                       Map<String, Object> urlQueryParams) {
        DataIngestionResult result = new DataIngestionResult();

        boolean shouldIngestData = checkShouldIngestData(urlQueryParams);
        result.shouldIngest = shouldIngestData;

        if (!shouldIngestData) {
            return result;
        }

        logger.info("ingest_data=true, converting to IngestDataBatch");

        try {
            IngestDataBatch ingestBatch = aktoIngestAdapter.convertToIngestDataBatch(proxyData);
            logger.info("Successfully converted to IngestDataBatch");

            // Publish to data pipeline if publisher is configured
            if (dataPublisher != null) {
                logger.info("Publishing to data pipeline via DataPublisher");
                dataPublisher.publish(ingestBatch);
                result.ingested = true;
                logger.info("Data successfully published to Kafka");
            } else {
                logger.warn("DataPublisher not configured - data will not be published to Kafka");
                result.error = "DataPublisher not configured";
            }
        } catch (Exception e) {
            logger.error("Error ingesting data: {}", e.getMessage(), e);
            result.error = e.getMessage();
        }

        return result;
    }

    /**
     * Builds the final response with all processing results
     */
    private Map<String, Object> buildFinalResponse(String url, String path, String method,
                                                     GuardrailsValidationResult guardrailsResult,
                                                     DataIngestionResult ingestionResult) {
        Map<String, Object> result = new HashMap<>();

        result.put("success", true);
        result.put("url", url);
        result.put("path", path);
        result.put("method", method);
        result.put("guardrailsApplied", guardrailsResult.applied);
        result.put("adapterUsed", guardrailsResult.adapterUsed);
        result.put("blocked", guardrailsResult.blocked);

        if (guardrailsResult.guardrailsResponse != null) {
            result.put("guardrailsResult", guardrailsResult.guardrailsResponse);
        }

        result.put("ingestData", ingestionResult.shouldIngest);
        if (ingestionResult.shouldIngest) {
            result.put("ingested", ingestionResult.ingested);
            if (ingestionResult.error != null) {
                result.put("ingestError", ingestionResult.error);
            }
        }

        result.put("timestamp", System.currentTimeMillis());

        logger.info("HTTP proxy processed - Adapter: {}, Blocked: {}, IngestData: {}, Ingested: {}",
            guardrailsResult.adapterUsed, guardrailsResult.blocked,
            ingestionResult.shouldIngest, ingestionResult.ingested);

        return result;
    }

    /**
     * Check if data should be ingested based on URL query parameters
     */
    private boolean checkShouldIngestData(Map<String, Object> urlQueryParams) {
        if (urlQueryParams == null || urlQueryParams.isEmpty()) {
            return false;
        }

        Object ingestData = urlQueryParams.get("ingest_data");
        if (ingestData == null) {
            return false;
        }

        String ingestDataValue = ingestData.toString();
        return "true".equalsIgnoreCase(ingestDataValue);
    }

    /**
     * Build error response
     */
    private Map<String, Object> buildErrorResponse(String errorMessage) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", errorMessage);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }

    // Getters and setters
    public GuardrailsClient getGuardrailsClient() {
        return guardrailsClient;
    }

    public AktoIngestAdapter getAktoIngestAdapter() {
        return aktoIngestAdapter;
    }

    public AdapterFactory getAdapterFactory() {
        return adapterFactory;
    }

    public DataPublisher getDataPublisher() {
        return dataPublisher;
    }

    /**
     * Set the data publisher for publishing IngestDataBatch to Kafka
     * @param dataPublisher The publisher implementation
     */
    public void setDataPublisher(DataPublisher dataPublisher) {
        this.dataPublisher = dataPublisher;
        logger.info("DataPublisher configured for Gateway");
    }

    /**
     * Helper class to hold guardrails validation results
     */
    private static class GuardrailsValidationResult {
        boolean applied = false;
        boolean blocked = false;
        String adapterUsed = "none";
        Map<String, Object> guardrailsResponse;
    }

    /**
     * Helper class to hold data ingestion results
     */
    private static class DataIngestionResult {
        boolean shouldIngest = false;
        boolean ingested = false;
        String error = null;
    }
}
