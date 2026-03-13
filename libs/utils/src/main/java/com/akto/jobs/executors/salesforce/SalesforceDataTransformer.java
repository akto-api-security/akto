package com.akto.jobs.executors.salesforce;

import com.akto.jobs.executors.AIAgentConnectorConstants;
import com.akto.log.LoggerMaker;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Transforms Salesforce AI Agent chat data into Akto HTTP request/response format.
 * Groups messages by session and creates request-response pairs.
 */
public class SalesforceDataTransformer {

    private static final LoggerMaker logger = new LoggerMaker(SalesforceDataTransformer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final String salesforceBaseUrl;

    public SalesforceDataTransformer(String salesforceBaseUrl, String aktoAccountId) {
        this.salesforceBaseUrl = salesforceBaseUrl;
        // Note: aktoAccountId parameter is ignored - always using constant from AIAgentConnectorConstants
        logger.info("SalesforceDataTransformer initialized with salesforceBaseUrl={}, aktoAccountId will always be set to {}",
            salesforceBaseUrl, AIAgentConnectorConstants.AKTO_ACCOUNT_ID_CONSTANT);
    }

    /**
     * Transform Salesforce chat data into Akto HTTP format.
     * Groups messages by session and creates request-response pairs.
     *
     * @param salesforceData Raw data from Salesforce
     * @return List of Akto HTTP entries
     */
    public List<Map<String, Object>> transformToAktoFormat(List<Map<String, Object>> salesforceData) {
        if (salesforceData == null || salesforceData.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, List<Map<String, Object>>> groupedBySession = groupBySession(salesforceData);
        List<Map<String, Object>> aktoEntries = new ArrayList<>();

        for (Map.Entry<String, List<Map<String, Object>>> entry : groupedBySession.entrySet()) {
            String sessionId = entry.getKey();
            List<Map<String, Object>> messages = entry.getValue();

            List<Map<String, Object>> pairs = createMessagePairs(messages);

            for (Map<String, Object> pair : pairs) {
                Map<String, Object> inputMsg = (Map<String, Object>) pair.get("input");
                Map<String, Object> outputMsg = (Map<String, Object>) pair.get("output");

                if (inputMsg != null && outputMsg != null) {
                    Map<String, Object> aktoEntry = createAktoEntry(inputMsg, outputMsg);
                    aktoEntries.add(aktoEntry);
                }
            }
        }

        logger.info("Transformed {} Salesforce messages into {} Akto entries",
            salesforceData.size(), aktoEntries.size());

        return aktoEntries;
    }

    /**
     * Group messages by session ID.
     */
    private Map<String, List<Map<String, Object>>> groupBySession(List<Map<String, Object>> data) {
        Map<String, List<Map<String, Object>>> grouped = new HashMap<>();

        for (Map<String, Object> message : data) {
            String sessionId = (String) message.get("sessionId");
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = "unknown";
            }

            grouped.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
        }

        return grouped;
    }

    /**
     * Create input-output message pairs from a list of messages.
     * Messages are sorted by ID and paired sequentially.
     */
    private List<Map<String, Object>> createMessagePairs(List<Map<String, Object>> messages) {
        List<Map<String, Object>> pairs = new ArrayList<>();

        // Sort messages by ID
        messages.sort((a, b) -> {
            String idA = (String) a.get("id");
            String idB = (String) b.get("id");
            return idA != null ? idA.compareTo(idB) : -1;
        });

        Map<String, Object> currentPair = new HashMap<>();

        for (Map<String, Object> message : messages) {
            String messageType = (String) message.get("messageType");

            if ("Input".equals(messageType)) {
                if (currentPair.containsKey("input")) {
                    pairs.add(new HashMap<>(currentPair));
                    currentPair.clear();
                }
                currentPair.put("input", message);
            } else if ("Output".equals(messageType)) {
                if (currentPair.containsKey("input")) {
                    currentPair.put("output", message);
                    pairs.add(new HashMap<>(currentPair));
                    currentPair.clear();
                } else {
                    currentPair.put("output", message);
                }
            }
        }

        if (!currentPair.isEmpty()) {
            pairs.add(currentPair);
        }

        return pairs;
    }

    /**
     * Create an Akto HTTP entry from input and output messages.
     */
    private Map<String, Object> createAktoEntry(Map<String, Object> inputMsg, Map<String, Object> outputMsg) {
        String sessionId = (String) inputMsg.get("sessionId");
        if (sessionId == null) {
            sessionId = "unknown";
        }

        String participantId = (String) inputMsg.get("participantId");
        if (participantId == null) {
            participantId = "unknown";
        }

        String contentText = (String) inputMsg.get("contentText");
        if (contentText == null) {
            contentText = "";
        }

        String responseText = (String) outputMsg.get("contentText");
        if (responseText == null) {
            responseText = "";
        }

        long timestamp = System.currentTimeMillis() / 1000;
        String salesforceHost = cleanUrl(salesforceBaseUrl);

        Map<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("message", contentText);

        Map<String, Object> responsePayload = new HashMap<>();
        responsePayload.put("message", responseText);

        try {
            Map<String, Object> entry = new HashMap<>();
            entry.put("path", "/api/chat/" + sessionId);
            entry.put("requestHeaders", buildRequestHeaders(sessionId, participantId, salesforceHost));
            entry.put("responseHeaders", buildResponseHeaders(sessionId));
            entry.put("method", AIAgentConnectorConstants.HTTP_METHOD_POST);
            entry.put("requestPayload", objectMapper.writeValueAsString(requestPayload));
            entry.put("responsePayload", objectMapper.writeValueAsString(responsePayload));
            entry.put("ip", AIAgentConnectorConstants.IP_ADDRESS_DEFAULT);
            entry.put("time", String.valueOf(timestamp));
            entry.put("statusCode", String.valueOf(AIAgentConnectorConstants.HTTP_STATUS_200));
            entry.put("type", AIAgentConnectorConstants.HTTP_VERSION);
            entry.put("status", AIAgentConnectorConstants.HTTP_STATUS_OK);
            entry.put("akto_account_id", AIAgentConnectorConstants.AKTO_ACCOUNT_ID_CONSTANT);
            entry.put("akto_vxlan_id", AIAgentConnectorConstants.AKTO_VXLAN_ID_DEFAULT);
            entry.put("is_pending", AIAgentConnectorConstants.IS_PENDING_FALSE);
            entry.put("source", AIAgentConnectorConstants.DATA_SOURCE_MIRRORING);
            entry.put("tag", "{\"source\":\"" + AIAgentConnectorConstants.DATA_SOURCE_MIRRORING + "\",\"gen-ai\":\"" + AIAgentConnectorConstants.DATA_TAG_GEN_AI + "\"}");

            return entry;
        } catch (Exception e) {
            logger.error("Error creating Akto entry: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * Build request headers JSON string.
     */
    private String buildRequestHeaders(String sessionId, String participantId, String host) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("host", host);
            headers.put("x-session-id", sessionId);
            headers.put("x-participant-id", participantId);
            headers.put("content-type", AIAgentConnectorConstants.CONTENT_TYPE_JSON);

            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            logger.error("Error building request headers: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Build response headers JSON string.
     */
    private String buildResponseHeaders(String sessionId) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("Transfer-Encoding", "chunked");
            headers.put("Connection", "keep-alive");
            headers.put("Api-Supported-Versions", "1, 2");
            headers.put("Request-Context", "appId=cid-v1:" + sessionId);
            headers.put("content-type", AIAgentConnectorConstants.CONTENT_TYPE_JSON);

            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            logger.error("Error building response headers: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * Clean URL by removing protocol and trailing slash.
     */
    private String cleanUrl(String url) {
        if (url == null) {
            return "salesforce.com";
        }
        return url.replaceAll("^https?://", "").replaceAll("/$", "");
    }
}
