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
    private DataPublisher dataPublisher;

    private Gateway() {
        this.guardrailsClient = new GuardrailsClient();
    }

    public static synchronized Gateway getInstance() {
        if (instance == null) {
            instance = new Gateway();
        }
        return instance;
    }

    public Map<String, Object> processHttpProxy(Map<String, Object> requestData) {
        logger.info("Processing HTTP proxy request - path: {}, method: {}, guardrails: {}, ingest_data: {}",
            requestData.get("path"), requestData.get("method"),
            requestData.get("guardrails"), requestData.get("ingest_data"));

        long start = System.currentTimeMillis();
        try {
            String requestPayload = getStringField(requestData, "requestPayload");
            if (requestPayload == null || requestPayload.isEmpty()) {
                logger.warn("Missing required field: requestPayload");
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Missing required field: requestPayload");
                error.put("error", "requestPayload is required");
                return error;
            }

            Map<String, Object> result = new HashMap<>();

            String guardrails = getStringField(requestData, "guardrails");
            if ("true".equalsIgnoreCase(guardrails)) {
                long guardrailsStart = System.currentTimeMillis();
                Map<String, Object> guardrailsResponse = callGuardrails(requestData);
                logger.info("Guardrails call completed - path: {}, latencyMs: {}",
                    requestData.get("path"), System.currentTimeMillis() - guardrailsStart);
                result.put("guardrailsResult", guardrailsResponse);
            }

            String ingestData = getStringField(requestData, "ingest_data");
            if ("true".equalsIgnoreCase(ingestData)) {
                long kafkaStart = System.currentTimeMillis();
                ingestData(requestData);
                logger.info("Kafka ingestion completed - path: {}, latencyMs: {}",
                    requestData.get("path"), System.currentTimeMillis() - kafkaStart);
            }

            logger.info("processHttpProxy completed - path: {}, method: {}, totalLatencyMs: {}",
                requestData.get("path"), requestData.get("method"), System.currentTimeMillis() - start);
            result.put("success", true);
            result.put("message", "Request processed successfully");
            return result;

        } catch (Exception e) {
            logger.error("Error processing HTTP proxy request: {}, latencyMs: {}", e.getMessage(),
                System.currentTimeMillis() - start, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Unexpected error: " + e.getMessage());
            error.put("error", e.getMessage());
            return error;
        }
    }

    private Map<String, Object> callGuardrails(Map<String, Object> requestData) {
        Map<String, Object> validateRequest = new HashMap<>();
        validateRequest.put("requestPayload", requestData.get("requestPayload"));
        validateRequest.put("contextSource", requestData.get("contextSource"));

        putIfNotNull(validateRequest, requestData, "path");
        putIfNotNull(validateRequest, requestData, "requestHeaders");
        putIfNotNull(validateRequest, requestData, "responseHeaders");
        putIfNotNull(validateRequest, requestData, "method");
        putIfNotNull(validateRequest, requestData, "responsePayload");
        putIfNotNull(validateRequest, requestData, "ip");
        putIfNotNull(validateRequest, requestData, "destIp");
        putIfNotNull(validateRequest, requestData, "time");
        putIfNotNull(validateRequest, requestData, "statusCode");
        putIfNotNull(validateRequest, requestData, "type");
        putIfNotNull(validateRequest, requestData, "status");
        putIfNotNull(validateRequest, requestData, "akto_account_id");
        putIfNotNull(validateRequest, requestData, "akto_vxlan_id");
        putIfNotNull(validateRequest, requestData, "is_pending");
        putIfNotNull(validateRequest, requestData, "source");
        putIfNotNull(validateRequest, requestData, "direction");
        putIfNotNull(validateRequest, requestData, "tag");
        putIfNotNull(validateRequest, requestData, "metadata");

        String contextSource = getStringField(requestData, "contextSource");
        logger.info("Calling guardrails /validate/request, contextSource: {}", contextSource);

        Map<String, Object> guardrailsResponse = guardrailsClient.callValidateRequest(validateRequest);

        logger.info("Guardrails response - allowed: {}",
            guardrailsResponse != null ? guardrailsResponse.get("Allowed") : "null");

        return guardrailsResponse;
    }

    private void ingestData(Map<String, Object> requestData) {
        IngestDataBatch batch = new IngestDataBatch();
        batch.setPath(getStringField(requestData, "path"));
        batch.setRequestHeaders(getStringField(requestData, "requestHeaders"));
        batch.setResponseHeaders(getStringField(requestData, "responseHeaders"));
        batch.setMethod(getStringField(requestData, "method"));
        batch.setRequestPayload(getStringField(requestData, "requestPayload"));
        batch.setResponsePayload(getStringField(requestData, "responsePayload"));
        batch.setIp(getStringField(requestData, "ip"));
        batch.setDestIp(getStringField(requestData, "destIp"));
        batch.setTime(getStringField(requestData, "time"));
        batch.setStatusCode(getStringField(requestData, "statusCode"));
        batch.setType(getStringField(requestData, "type"));
        batch.setStatus(getStringField(requestData, "status"));
        batch.setAkto_account_id(getStringField(requestData, "akto_account_id"));
        batch.setAkto_vxlan_id(getStringField(requestData, "akto_vxlan_id"));
        batch.setIs_pending(getStringField(requestData, "is_pending"));
        batch.setSource(getStringField(requestData, "source"));
        batch.setDirection(getStringField(requestData, "direction"));
        batch.setProcess_id(getStringField(requestData, "process_id"));
        batch.setSocket_id(getStringField(requestData, "socket_id"));
        batch.setDaemonset_id(getStringField(requestData, "daemonset_id"));
        batch.setEnabled_graph(getStringField(requestData, "enabled_graph"));
        batch.setTag(getStringField(requestData, "tag"));

        if (dataPublisher != null) {
            try {
                dataPublisher.publish(batch);
                logger.info("Data ingested to Kafka - path: {}, method: {}",
                    requestData.get("path"), requestData.get("method"));
            } catch (Exception e) {
                logger.error("Error publishing data to Kafka: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to publish data: " + e.getMessage(), e);
            }
        } else {
            logger.warn("DataPublisher not configured - data will not be published to Kafka");
        }
    }

    private String getStringField(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val != null ? val.toString() : null;
    }

    private void putIfNotNull(Map<String, Object> target, Map<String, Object> source, String key) {
        Object val = source.get(key);
        if (val != null) {
            target.put(key, val);
        }
    }

    public DataPublisher getDataPublisher() {
        return dataPublisher;
    }

    public void setDataPublisher(DataPublisher dataPublisher) {
        this.dataPublisher = dataPublisher;
    }
}
