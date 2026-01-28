package com.akto.utils;

import com.akto.config.GuardrailsConfig;
import com.akto.dto.IngestDataBatch;
import com.akto.kafka.Kafka;
import com.akto.kafka.KafkaProtoProducer;
import com.akto.log.LoggerMaker;
import com.akto.proto.generated.threat_detection.message.http_response_param.v1.HttpResponseParam;
import com.akto.proto.generated.threat_detection.message.http_response_param.v1.StringList;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles publishing messages to multiple Kafka topics based on configuration.
 */
public class TopicPublisher {

    private static final LoggerMaker logger = new LoggerMaker(TopicPublisher.class, LoggerMaker.LogDb.DATA_INGESTION);
    private static final String THREAT_DETECTION_TOPIC = "akto.api.logs2";

    private final GuardrailsConfig config;

    public TopicPublisher(GuardrailsConfig config) {
        this.config = config;
    }

    public void publish(String message, String primaryTopic) {
        publish(message, primaryTopic, null);
    }

    public void publish(String message, String primaryTopic, IngestDataBatch payload) {
        Kafka currentProducer = KafkaUtils.getKafkaProducer();

        logger.debug("→ Sending message to PRIMARY topic: {}", primaryTopic);
        currentProducer.send(message, primaryTopic);
        logger.debug("✓ Message sent to topic '{}'", primaryTopic);

        // Conditionally publish to guardrails topic
        if (config != null && config.isEnabled()) {
            try {
                String guardrailsTopic = config.getTopicName();
                logger.debug("→ Sending message to GUARDRAILS topic: {}", guardrailsTopic);
                currentProducer.send(message, guardrailsTopic);
                logger.debug("✓ Message sent to guardrails topic '{}'", guardrailsTopic);
            } catch (Exception e) {
                logger.error("Failed to send message to guardrails topic (non-fatal): {}", e.getMessage());
            }
        }

        // Conditionally publish to threat detection topic (protobuf format)
        if (payload != null) {
            try {
                HttpResponseParam httpResponseParam = buildHttpResponseParam(payload);
                KafkaProtoProducer protoProducer = KafkaUtils.getKafkaProtoProducer();
                protoProducer.send(THREAT_DETECTION_TOPIC, httpResponseParam);
            } catch (Exception e) {
                logger.error("Failed to send message to threat detection topic (non-fatal): {}", e.getMessage());
            }
        }
    }

    private Map<String, StringList> parseHeadersToProto(String headersJson) {
        Map<String, StringList> headers = new HashMap<>();
        if (headersJson == null || headersJson.isEmpty()) {
            return headers;
        }

        try {
            headersJson = headersJson.trim();
            if (headersJson.startsWith("{") && headersJson.endsWith("}")) {
                headersJson = headersJson.substring(1, headersJson.length() - 1);
                String[] pairs = headersJson.split(",");

                for (String pair : pairs) {
                    String[] keyValue = pair.split(":", 2);
                    if (keyValue.length == 2) {
                        String key = keyValue[0].trim().replaceAll("\"", "");
                        String value = keyValue[1].trim().replaceAll("\"", "");
                        headers.put(key, StringList.newBuilder().addValues(value).build());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing headers: {}", e.getMessage(), e);
        }

        return headers;
    }

    private HttpResponseParam buildHttpResponseParam(IngestDataBatch payload) {
        Map<String, StringList> requestHeaders = parseHeadersToProto(payload.getRequestHeaders());
        Map<String, StringList> responseHeaders = parseHeadersToProto(payload.getResponseHeaders());

        HttpResponseParam.Builder builder = HttpResponseParam.newBuilder();
        builder.setMethod(payload.getMethod() != null ? payload.getMethod() : "")
            .setPath(payload.getPath() != null ? payload.getPath() : "")
            .setType(payload.getType() != null ? payload.getType() : "HTTP/1.1")
            .putAllRequestHeaders(requestHeaders)
            .putAllResponseHeaders(responseHeaders)
            .setRequestPayload(payload.getRequestPayload() != null ? payload.getRequestPayload() : "")
            .setResponsePayload(payload.getResponsePayload() != null ? payload.getResponsePayload() : "")
            .setStatusCode(payload.getStatusCode() != null ? Integer.parseInt(payload.getStatusCode()) : 0)
            .setStatus(payload.getStatus() != null ? payload.getStatus() : "")
            .setTime(payload.getTime() != null ? Integer.parseInt(payload.getTime()) : (int)(System.currentTimeMillis() / 1000))
            .setAktoAccountId(payload.getAkto_account_id() != null ? payload.getAkto_account_id() : "")
            .setAktoVxlanId(payload.getAkto_vxlan_id() != null ? payload.getAkto_vxlan_id() : "")
            .setIp(payload.getIp() != null ? payload.getIp() : "")
            .setDestIp(payload.getDestIp() != null ? payload.getDestIp() : "")
            .setDirection(payload.getDirection() != null ? payload.getDirection() : "")
            .setIsPending(payload.getIs_pending() != null ? Boolean.parseBoolean(payload.getIs_pending()) : false)
            .setSource(payload.getSource() != null ? payload.getSource() : "");

        return builder.build();
    }
}
