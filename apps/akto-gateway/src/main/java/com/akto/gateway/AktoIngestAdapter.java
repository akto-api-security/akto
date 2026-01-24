package com.akto.gateway;

import com.akto.dto.IngestDataBatch;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class AktoIngestAdapter {

    private static final Logger logger = LogManager.getLogger(AktoIngestAdapter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String aktoAccountId;
    private String aktoVxlanId;
    private String source;

    public AktoIngestAdapter() {
        this.aktoAccountId = loadAccountIdFromEnv();
        this.aktoVxlanId = loadVxlanIdFromEnv();
        this.source = loadSourceFromEnv();
        logger.info("AktoIngestAdapter initialized - AccountId: {}, VxlanId: {}, Source: {}",
                aktoAccountId, aktoVxlanId, source);
    }

    public AktoIngestAdapter(String aktoAccountId, String aktoVxlanId, String source) {
        this.aktoAccountId = aktoAccountId;
        this.aktoVxlanId = aktoVxlanId;
        this.source = source;
        logger.info("AktoIngestAdapter initialized with custom config - AccountId: {}, VxlanId: {}, Source: {}",
                aktoAccountId, aktoVxlanId, source);
    }

    /**
     * Converts http-proxy format to Akto ingest format
     *
     * Input format:
     * {
     *   "url": "http://example.com",
     *   "path": "/api/endpoint",
     *   "request": {
     *      "method": "POST",
     *      "headers": {},
     *      "body": "...",
     *      "queryParams": {},
     *      "metadata": {}
     *   },
     *   "response": {
     *      "headers": {},
     *      "body": "...",
     *      "protocol": "HTTP/1.1",
     *      "statusCode": 200,
     *      "status": "SUCCESS",
     *      "metadata": {}
     *   }
     * }
     *
     * Output format (Akto ingest):
     * {
     *   "path": "/api/endpoint",
     *   "requestHeaders": "{...}",  // JSON string
     *   "responseHeaders": "{...}", // JSON string
     *   "method": "POST",
     *   "requestPayload": "...",
     *   "responsePayload": "...",
     *   "ip": "...",
     *   "time": "1745858591",
     *   "statusCode": "200",
     *   "type": "HTTP/1.1",
     *   "status": "SUCCESS",
     *   "akto_account_id": "1000000",
     *   "akto_vxlan_id": "0",
     *   "is_pending": "false",
     *   "source": "MIRRORING"
     * }
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToAktoIngestFormat(Map<String, Object> proxyData) {
        logger.info("Converting proxy data to Akto ingest format");

        try {
            Map<String, Object> aktoIngest = new HashMap<>();

            // Extract core fields
            String path = (String) proxyData.get("path");
            Map<String, Object> request = (Map<String, Object>) proxyData.get("request");
            Map<String, Object> response = (Map<String, Object>) proxyData.get("response");

            // Basic fields
            aktoIngest.put("path", path);

            // Request fields
            if (request != null) {
                String method = (String) request.get("method");
                aktoIngest.put("method", method);

                // Convert request headers to JSON string
                Map<String, Object> requestHeaders = (Map<String, Object>) request.get("headers");
                if (requestHeaders != null) {
                    String requestHeadersJson = objectMapper.writeValueAsString(requestHeaders);
                    aktoIngest.put("requestHeaders", requestHeadersJson);

                    // Extract IP from headers
                    String ip = extractIpFromHeaders(requestHeaders);
                    aktoIngest.put("ip", ip);
                } else {
                    aktoIngest.put("requestHeaders", "{}");
                    aktoIngest.put("ip", "0.0.0.0");
                }

                // Request payload
                Object body = request.get("body");
                if (body != null) {
                    if (body instanceof String) {
                        aktoIngest.put("requestPayload", body);
                    } else {
                        aktoIngest.put("requestPayload", objectMapper.writeValueAsString(body));
                    }
                } else {
                    aktoIngest.put("requestPayload", "");
                }
            }

            // Response fields
            if (response != null) {
                // Convert response headers to JSON string
                Map<String, Object> responseHeaders = (Map<String, Object>) response.get("headers");
                if (responseHeaders != null) {
                    String responseHeadersJson = objectMapper.writeValueAsString(responseHeaders);
                    aktoIngest.put("responseHeaders", responseHeadersJson);
                } else {
                    aktoIngest.put("responseHeaders", "{}");
                }

                // Response body (now called "body" instead of "payload")
                Object body = response.get("body");
                if (body != null) {
                    if (body instanceof String) {
                        aktoIngest.put("responsePayload", body);
                    } else {
                        aktoIngest.put("responsePayload", objectMapper.writeValueAsString(body));
                    }
                } else {
                    aktoIngest.put("responsePayload", "");
                }

                // Status code
                Object statusCode = response.get("statusCode");
                if (statusCode != null) {
                    aktoIngest.put("statusCode", String.valueOf(statusCode));
                } else {
                    aktoIngest.put("statusCode", "200");
                }

                // Status
                String status = (String) response.get("status");
                if (status != null) {
                    aktoIngest.put("status", status);
                } else {
                    aktoIngest.put("status", "SUCCESS");
                }

                // Protocol/Type
                String protocol = (String) response.get("protocol");
                if (protocol != null) {
                    aktoIngest.put("type", protocol);
                } else {
                    aktoIngest.put("type", "HTTP/1.1");
                }
            }

            // Timestamp (current time in seconds)
            long currentTime = System.currentTimeMillis() / 1000;
            aktoIngest.put("time", String.valueOf(currentTime));

            // Akto-specific fields
            aktoIngest.put("akto_account_id", aktoAccountId);
            aktoIngest.put("akto_vxlan_id", aktoVxlanId);
            aktoIngest.put("is_pending", "false");
            aktoIngest.put("source", source);

            logger.info("Successfully converted to Akto ingest format");
            return aktoIngest;

        } catch (Exception e) {
            logger.error("Error converting to Akto ingest format: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert to Akto ingest format: " + e.getMessage(), e);
        }
    }

    /**
     * Converts http-proxy format to IngestDataBatch object for Kafka ingestion
     *
     * @param proxyData The proxy data in http-proxy format
     * @return IngestDataBatch object ready for Kafka publishing
     */
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
                if (requestHeaders != null) {
                    batch.setRequestHeaders(objectMapper.writeValueAsString(requestHeaders));
                    // Extract IP from headers
                    batch.setIp(extractIpFromHeaders(requestHeaders));
                } else {
                    batch.setRequestHeaders("{}");
                    batch.setIp("0.0.0.0");
                }

                // Set request payload (body)
                Object requestBody = request.get("body");
                if (requestBody != null) {
                    if (requestBody instanceof String) {
                        batch.setRequestPayload((String) requestBody);
                    } else {
                        batch.setRequestPayload(objectMapper.writeValueAsString(requestBody));
                    }
                } else {
                    batch.setRequestPayload("");
                }
            } else {
                batch.setMethod("");
                batch.setRequestHeaders("{}");
                batch.setRequestPayload("");
                batch.setIp("0.0.0.0");
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
                    batch.setResponsePayload("");
                }

                // Set status code
                Object statusCode = response.get("statusCode");
                batch.setStatusCode(statusCode != null ? String.valueOf(statusCode) : "200");

                // Set status
                Object status = response.get("status");
                batch.setStatus(status != null ? String.valueOf(status) : "SUCCESS");
            } else {
                batch.setResponseHeaders("{}");
                batch.setResponsePayload("");
                batch.setStatusCode("200");
                batch.setStatus("SUCCESS");
            }

            // Set timestamp (in seconds)
            batch.setTime(String.valueOf(System.currentTimeMillis() / 1000));

            // Set account and vxlan IDs
            batch.setAkto_account_id(aktoAccountId);
            batch.setAkto_vxlan_id(aktoVxlanId);

            // Set source
            batch.setSource(source);

            // Set type (HTTP or HTTPS based on URL)
            if (url != null && url.toLowerCase().startsWith("https")) {
                batch.setType("HTTPS");
            } else {
                batch.setType("HTTP");
            }

            logger.info("Successfully converted to IngestDataBatch - path: {}, method: {}", path, batch.getMethod());
            return batch;

        } catch (Exception e) {
            logger.error("Error converting to IngestDataBatch: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert to IngestDataBatch: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts IP address from request headers
     * Checks common headers: X-Forwarded-For, X-Real-IP, Remote-Addr
     */
    private String extractIpFromHeaders(Map<String, Object> headers) {
        if (headers == null) {
            return "0.0.0.0";
        }

        // Check X-Forwarded-For (can be comma-separated list)
        Object xForwardedFor = headers.get("X-Forwarded-For");
        if (xForwardedFor == null) {
            xForwardedFor = headers.get("x-forwarded-for");
        }
        if (xForwardedFor != null) {
            String xff = xForwardedFor.toString().trim();
            // Take the first IP if comma-separated
            if (xff.contains(",")) {
                xff = xff.split(",")[0].trim();
            }
            if (!xff.isEmpty()) {
                return xff;
            }
        }

        // Check X-Real-IP
        Object xRealIp = headers.get("X-Real-IP");
        if (xRealIp == null) {
            xRealIp = headers.get("x-real-ip");
        }
        if (xRealIp != null) {
            String realIp = xRealIp.toString().trim();
            if (!realIp.isEmpty()) {
                return realIp;
            }
        }

        // Check Remote-Addr
        Object remoteAddr = headers.get("Remote-Addr");
        if (remoteAddr == null) {
            remoteAddr = headers.get("remote-addr");
        }
        if (remoteAddr != null) {
            String addr = remoteAddr.toString().trim();
            if (!addr.isEmpty()) {
                return addr;
            }
        }

        return "0.0.0.0";
    }

    private static String loadAccountIdFromEnv() {
        String accountId = System.getenv("AKTO_ACCOUNT_ID");
        if (accountId == null || accountId.isEmpty()) {
            accountId = "1000000"; // Default account ID
        }
        return accountId;
    }

    private static String loadVxlanIdFromEnv() {
        String vxlanId = System.getenv("AKTO_VXLAN_ID");
        if (vxlanId == null || vxlanId.isEmpty()) {
            vxlanId = "0"; // Default VXLAN ID
        }
        return vxlanId;
    }

    private static String loadSourceFromEnv() {
        String source = System.getenv("AKTO_SOURCE");
        if (source == null || source.isEmpty()) {
            source = "MIRRORING"; // Default source
        }
        return source;
    }

    // Getters and setters
    public String getAktoAccountId() {
        return aktoAccountId;
    }

    public void setAktoAccountId(String aktoAccountId) {
        this.aktoAccountId = aktoAccountId;
    }

    public String getAktoVxlanId() {
        return aktoVxlanId;
    }

    public void setAktoVxlanId(String aktoVxlanId) {
        this.aktoVxlanId = aktoVxlanId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
