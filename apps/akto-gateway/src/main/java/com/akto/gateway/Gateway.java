package com.akto.gateway;

import com.akto.dao.context.Context;
import com.akto.data_actor.ClientActor;
import com.akto.dto.IngestDataBatch;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;


public class Gateway {

    private static final LoggerMaker loggerMaker = new LoggerMaker(Gateway.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // The guardrail result travels with the traffic as its own field on the Kafka message, the same
    // way _traces already does. Keeping it out of requestHeaders matters: a recorded header becomes
    // part of the API's schema and its stored sample, and would be replayed during tests.
    private static final String GUARDRAIL_FIELD = "_guardrail";
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
        loggerMaker.infoAndAddToDb(
            "Processing HTTP proxy request - path: {}, method: {}, guardrails: {}, response_guardrails: {}, ingest_data: {}",
            requestData.get("path"),
            requestData.get("method"),
            requestData.get("guardrails"),
            requestData.get("response_guardrails"),
            requestData.get("ingest_data"));

        long start = System.currentTimeMillis();
        try {
            String requestPayload = getStringField(requestData, "requestPayload");
            if (requestPayload == null || requestPayload.isEmpty()) {
                loggerMaker.warnAndAddToDb("Missing required field: requestPayload");
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "Missing required field: requestPayload");
                error.put("error", "requestPayload is required");
                return error;
            }

            Map<String, Object> result = new HashMap<>();

            boolean runRequestGuardrails = "true".equalsIgnoreCase(getStringField(requestData, "guardrails"));
            boolean runResponseGuardrails = "true".equalsIgnoreCase(getStringField(requestData, "response_guardrails"));

            if (shouldForceGuardrailsForAccount(Context.accountId.get())
                || shouldForceGuardrailsForAccount(ClientActor.getAbstractorAccountIdFromEnvOrNull())) {
                runRequestGuardrails = true;
                runResponseGuardrails = true;
            }

            if (runRequestGuardrails || runResponseGuardrails) {
                long guardrailsStart = System.currentTimeMillis();
                Map<String, Object> guardrailsResult = null;

                if (runRequestGuardrails) {
                    guardrailsResult = mergeGuardrailsResults(guardrailsResult, callGuardrails(requestData, false));
                }
                if (runResponseGuardrails) {
                    guardrailsResult = mergeGuardrailsResults(guardrailsResult, callGuardrails(requestData, true));
                }

                loggerMaker.infoAndAddToDb("Guardrails call(s) completed - path: {}, latencyMs: {}",
                    requestData.get("path"), System.currentTimeMillis() - guardrailsStart);
                result.put("guardrailsResult", guardrailsResult);
                // Must run before ingestData below. Otherwise the result only goes back to the
                // caller, and the trace shows the prompt with no sign of whether it was an attack,
                // which policy was hit, or why it was blocked.
                recordGuardrailVerdict(requestData, guardrailsResult);
            }

            String ingestData = getStringField(requestData, "ingest_data");
            if ("true".equalsIgnoreCase(ingestData)) {
                long kafkaStart = System.currentTimeMillis();
                ingestData(requestData);
                loggerMaker.infoAndAddToDb("Kafka ingestion completed - path: {}, latencyMs: {}",
                    requestData.get("path"), System.currentTimeMillis() - kafkaStart);
            }

            loggerMaker.infoAndAddToDb("processHttpProxy completed - path: {}, method: {}, totalLatencyMs: {}",
                requestData.get("path"), requestData.get("method"), System.currentTimeMillis() - start);
            result.put("success", true);
            result.put("message", "Request processed successfully");
            return result;

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error processing HTTP proxy request: {}, latencyMs: {}",
                e.getMessage(), System.currentTimeMillis() - start);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "Unexpected error: " + e.getMessage());
            error.put("error", e.getMessage());
            return error;
        }
    }

    private static boolean shouldForceGuardrailsForAccount(Integer accountId) {
        return accountId != null && (accountId == 1710118493 || accountId == 1000000);
    }

    private Map<String, Object> mergeGuardrailsResults(Map<String, Object> existing, Map<String, Object> incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;

        Map<String, Object> merged = new HashMap<>(existing);
        boolean allowed = isAllowed(existing) && isAllowed(incoming);
        merged.put("Allowed", allowed);
        merged.put("requestResult", existing);
        merged.put("responseResult", incoming);
        return merged;
    }

    private boolean isAllowed(Map<String, Object> result) {
        if (result == null) return false;
        Object val = result.get("Allowed");
        if (val == null) {
            val = result.get("allowed");
        }
        if (val instanceof Boolean) return (Boolean) val;
        if (val != null) return Boolean.parseBoolean(val.toString());
        return false;
    }

    /**
     * Records the guardrail result on the traffic we ingest, so the trace record mini-runtime builds
     * from it saves whether the prompt was an attack, which policy and rule were hit, what was done
     * about it, and why it was blocked.
     *
     * The violated flag is always written, even for clean traffic, so we can tell a checked prompt
     * apart from one that was never checked. The key names are the wire contract with mini-runtime.
     * Errors are only logged: a bad verdict must never stop traffic from being ingested.
     */
    private void recordGuardrailVerdict(Map<String, Object> requestData, Map<String, Object> guardrailsResult) {
        if (requestData == null || guardrailsResult == null) return;
        try {
            Map<String, Object> verdict = effectiveVerdict(guardrailsResult);
            Map<String, Object> out = new HashMap<>();

            out.put("guardrailViolated", !isAllowed(guardrailsResult));
            putIfNotBlank(out, "guardrailAction", firstNonBlank(verdict, "behaviour", "Behaviour"));
            putIfNotBlank(out, "guardrailReason", firstNonBlank(verdict, "reason", "Reason"));

            Object metaObj = verdict.get("Metadata") != null ? verdict.get("Metadata") : verdict.get("metadata");
            if (metaObj instanceof Map) {
                Map<?, ?> meta = (Map<?, ?>) metaObj;
                putIfNotBlank(out, "guardrailPolicy", asString(meta.get("policy_name")));
                putIfNotBlank(out, "guardrailRule", asString(meta.get("rule_violated")));
                putIfNotBlank(out, "guardrailSeverity", asString(meta.get("severity")));
            }

            requestData.put(GUARDRAIL_FIELD, objectMapper.writeValueAsString(out));
        } catch (Exception e) {
            loggerMaker.warnAndAddToDb("Could not record guardrail result on traffic: " + e.getMessage());
        }
    }

    /**
     * When the request and response results are merged, the merged map only keeps the combined
     * Allowed flag, so the policy, rule and reason have to come from whichever side was violated.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> effectiveVerdict(Map<String, Object> guardrailsResult) {
        for (String key : new String[] { "requestResult", "responseResult" }) {
            Object side = guardrailsResult.get(key);
            if (side instanceof Map && !isAllowed((Map<String, Object>) side)) {
                return (Map<String, Object>) side;
            }
        }
        return guardrailsResult;
    }

    private String firstNonBlank(Map<String, Object> source, String... keys) {
        for (String key : keys) {
            String val = asString(source.get(key));
            if (!val.isEmpty()) return val;
        }
        return "";
    }

    private String asString(Object val) {
        return val == null ? "" : val.toString().trim();
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isEmpty()) target.put(key, value);
    }

    private Map<String, Object> callGuardrails(Map<String, Object> requestData, boolean isResponse) {
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
        String endpoint = isResponse ? "/validate/response" : "/validate/request";
        loggerMaker.infoAndAddToDb("Calling guardrails {}, contextSource: {}", endpoint, contextSource);

        Map<String, Object> guardrailsResponse = isResponse
            ? guardrailsClient.callValidateResponse(validateRequest)
            : guardrailsClient.callValidateRequest(validateRequest);

        loggerMaker.infoAndAddToDb("Guardrails response - allowed: {}",
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
        batch.set_guardrail(getStringField(requestData, GUARDRAIL_FIELD));

        if (dataPublisher != null) {
            try {
                dataPublisher.publish(batch);
                loggerMaker.infoAndAddToDb("Data ingested to Kafka - path: {}, method: {}",
                    requestData.get("path"), requestData.get("method"));
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error publishing data to Kafka: {}", e.getMessage());
                throw new RuntimeException("Failed to publish data: " + e.getMessage(), e);
            }
        } else {
            loggerMaker.warnAndAddToDb("DataPublisher not configured - data will not be published to Kafka");
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
