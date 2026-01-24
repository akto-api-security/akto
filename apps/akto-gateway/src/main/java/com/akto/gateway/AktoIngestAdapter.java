package com.akto.gateway;

import com.akto.dto.IngestDataBatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class AktoIngestAdapter {

    private static final Logger logger = LogManager.getLogger(AktoIngestAdapter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Constants for Akto ingest configuration
    private static final String AKTO_ACCOUNT_ID = "1000000";
    private static final String AKTO_VXLAN_ID = "0";
    private static final String AKTO_SOURCE = "MIRRORING";
    private static final String AKTO_IP = "0.0.0.0";

    public AktoIngestAdapter() {
        logger.info("AktoIngestAdapter initialized - AccountId: {}, VxlanId: {}, Source: {}",
                AKTO_ACCOUNT_ID, AKTO_VXLAN_ID, AKTO_SOURCE);
    }

    @SuppressWarnings("unchecked")
    public IngestDataBatch convertToIngestDataBatch(Map<String, Object> proxyData) {
        logger.info("Converting proxy data to IngestDataBatch format");

        try {
            IngestDataBatch batch = new IngestDataBatch();

            // Extract core fields
            String url = (String) proxyData.get("url");
            String path = (String) proxyData.get("path");
            Map<String, Object> request = (Map<String, Object>) proxyData.get("request");
            Map<String, Object> response = (Map<String, Object>) proxyData.get("response");

            // Set path
            batch.setPath(path);

            // Set method
            if (request != null) {
                String method = (String) request.get("method");
                batch.setMethod(method != null ? method : "");

                // Convert request headers to JSON string
                Map<String, Object> requestHeaders = (Map<String, Object>) request.get("headers");
                if (requestHeaders == null) {
                    requestHeaders = new HashMap<>();
                }

                // Add host header from URL
                if (url != null && !url.isEmpty()) {
                    try {
                        URL parsedUrl = new URL(url);
                        String host = parsedUrl.getHost();
                        if (host != null && !host.isEmpty()) {
                            requestHeaders.put("host", host);
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to parse URL for host extraction: {}", url);
                    }
                }

                batch.setRequestHeaders(objectMapper.writeValueAsString(requestHeaders));

                // Always use hardcoded IP
                batch.setIp(AKTO_IP);

                // Set request payload (body)
                Object requestBody = request.get("body");
                if (requestBody != null) {
                    if (requestBody instanceof String) {
                        batch.setRequestPayload((String) requestBody);
                    } else {
                        batch.setRequestPayload(objectMapper.writeValueAsString(requestBody));
                    }
                } else {
                    batch.setRequestPayload("{}");
                }

                // Extract tag from request metadata
                Map<String, Object> metadata = (Map<String, Object>) request.get("metadata");
                if (metadata != null) {
                    Object tag = metadata.get("tag");
                    if (tag != null) {
                        batch.setTag(objectMapper.writeValueAsString(tag));
                    }
                }
            } else {
                batch.setMethod("");
                batch.setRequestHeaders("{}");
                batch.setRequestPayload("{}");
                batch.setIp(AKTO_IP);
            }

            // Response fields
            if (response != null) {
                // Convert response headers to JSON string
                Map<String, Object> responseHeaders = (Map<String, Object>) response.get("headers");
                if (responseHeaders != null) {
                    batch.setResponseHeaders(objectMapper.writeValueAsString(responseHeaders));
                } else {
                    batch.setResponseHeaders("{}");
                }

                // Set response payload (body)
                Object responseBody = response.get("body");
                if (responseBody != null) {
                    if (responseBody instanceof String) {
                        batch.setResponsePayload((String) responseBody);
                    } else {
                        batch.setResponsePayload(objectMapper.writeValueAsString(responseBody));
                    }
                } else {
                    batch.setResponsePayload("{}");
                }

                // Set status code
                Object statusCode = response.get("statusCode");
                batch.setStatusCode(statusCode != null ? String.valueOf(statusCode) : "200");

                // Set status
                Object status = response.get("status");
                batch.setStatus(status != null ? String.valueOf(status) : "SUCCESS");

                // Set type (protocol from response, e.g., "HTTP/1.1", "HTTP/2")
                Object protocol = response.get("protocol");
                batch.setType(protocol != null ? String.valueOf(protocol) : "HTTP/1.1");
            } else {
                batch.setResponseHeaders("{}");
                batch.setResponsePayload("{}");
                batch.setStatusCode("200");
                batch.setStatus("SUCCESS");
                batch.setType("HTTP/1.1");
            }

            // Set timestamp (in seconds)
            batch.setTime(String.valueOf(System.currentTimeMillis() / 1000));

            // Set account and vxlan IDs (always constant values)
            batch.setAkto_account_id(AKTO_ACCOUNT_ID);
            batch.setAkto_vxlan_id(AKTO_VXLAN_ID);

            // Set source (always constant value)
            batch.setSource(AKTO_SOURCE);

            // Set is_pending to false
            batch.setIs_pending("false");

            logger.info("Successfully converted to IngestDataBatch - path: {}, method: {}", path, batch.getMethod());
            return batch;

        } catch (Exception e) {
            logger.error("Error converting to IngestDataBatch: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert to IngestDataBatch: " + e.getMessage(), e);
        }
    }
}
