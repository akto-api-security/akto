package com.akto.fast_discovery;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.runtime.utils.Utils;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.util.JSONUtils;
import com.alibaba.fastjson2.JSON;
import com.google.gson.Gson;
import org.apache.commons.lang3.math.NumberUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * FastDiscoveryParser - Lightweight parser for Kafka messages focused on extracting
 * URL, method, and apiCollectionId for fast new API discovery.
 *
 * Based on SampleParser.parseSampleMessage but optimized for speed by skipping
 * non-essential processing.
 */
public class FastDiscoveryParser {

    private static final Gson gson = new Gson();
    private static final String CONTENT_ENCODING_HEADER = "content-encoding";
    private static final String GZIP = "gzip";

    /**
     * Parse Kafka message into HttpResponseParams.
     * Extracts essential fields: URL, method, apiCollectionId, headers, payload.
     *
     * @param message Raw JSON message from Kafka
     * @return HttpResponseParams object or null if parsing fails
     */
    public static HttpResponseParams parseKafkaMessage(String message) {
        try {
            // 1. Parse JSON
            Map<String, Object> json = JSON.parseObject(message);

            // 2. Filter out trigger messages (mini-runtime → database-abstractor)
            // Trigger messages have "triggerMethod" field (e.g., "bulkWriteSti", "bulkWriteSampleData")
            if (json.containsKey("triggerMethod")) {
                return null;  // Skip trigger messages
            }

            // 3. Extract request fields
            String method = (String) json.get("method");
            String url = (String) json.get("path");
            String type = (String) json.get("type");

            // 4. Parse headers
            Map<String, List<String>> requestHeaders = OriginalHttpRequest.buildHeadersMap(json, "requestHeaders");
            Map<String, List<String>> responseHeaders = OriginalHttpRequest.buildHeadersMap(json, "responseHeaders");

            // 5. Transform payloads
            String rawRequestPayload = (String) json.get("requestPayload");
            String requestPayload = HttpRequestResponseUtils.rawToJsonString(rawRequestPayload, requestHeaders);

            String responsePayload = (String) json.get("responsePayload");
            responsePayload = HttpRequestResponseUtils.rawToJsonString(responsePayload, responseHeaders);
            responsePayload = JSONUtils.parseIfJsonP(responsePayload);
            responsePayload = decodeIfGzipEncoding(responsePayload, responseHeaders);

            // 6. Extract metadata
            String apiCollectionIdStr = json.getOrDefault("akto_vxlan_id", "0").toString();
            int apiCollectionId = 0;
            if (NumberUtils.isDigits(apiCollectionIdStr)) {
                apiCollectionId = NumberUtils.toInt(apiCollectionIdStr, 0);
            }

            int statusCode;
            try {
                statusCode = Integer.parseInt(json.get("statusCode").toString());
            } catch (Exception e) {
                statusCode = 200; // Default status code
            }

            String status = (String) json.get("status");

            int time;
            try {
                time = Integer.parseInt(json.get("time").toString());
            } catch (Exception e) {
                time = (int) (System.currentTimeMillis() / 1000);
            }

            String accountId = (String) json.get("akto_account_id");
            String sourceIP = (String) json.getOrDefault("ip", "");
            String destIP = (String) json.getOrDefault("destIp", "");
            String direction = (String) json.getOrDefault("direction", "");

            String isPendingStr = (String) json.getOrDefault("is_pending", "false");
            boolean isPending = !isPendingStr.toLowerCase().equals("false");

            String sourceStr = (String) json.getOrDefault("source", HttpResponseParams.Source.OTHER.name());
            HttpResponseParams.Source source;
            try {
                source = HttpResponseParams.Source.valueOf(sourceStr);
            } catch (Exception e) {
                source = HttpResponseParams.Source.OTHER;
            }

            String tags = (String) json.getOrDefault("tag", "");
            List<String> parentMcpToolNames;
            try {
                parentMcpToolNames = (List<String>) json.get("parentMcpToolNames");
            } catch (Exception e) {
                parentMcpToolNames = new ArrayList<>();
            }

            // 7. Build HttpRequestParams
            HttpRequestParams requestParams = new HttpRequestParams(
                    method, url, type, requestHeaders, requestPayload, apiCollectionId
            );

            // 8. Build HttpResponseParams
            return new HttpResponseParams(
                    type,
                    statusCode,
                    status,
                    responseHeaders,
                    responsePayload,
                    requestParams,
                    time,
                    accountId,
                    isPending,
                    source,
                    message,
                    sourceIP,
                    destIP,
                    direction,
                    tags,
                    parentMcpToolNames
            );

        } catch (Exception e) {
            System.err.println("Failed to parse Kafka message: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Decode GZIP-encoded payload if content-encoding header indicates gzip.
     */
    private static String decodeIfGzipEncoding(String payload, Map<String, List<String>> headers) {
        if (payload == null || payload.isEmpty()) {
            return payload;
        }

        List<String> contentEncodings = getHeaderIgnoreCase(headers, CONTENT_ENCODING_HEADER);
        if (contentEncodings == null || contentEncodings.isEmpty()) {
            return payload;
        }

        boolean isGzip = false;
        for (String encoding : contentEncodings) {
            if (encoding != null && encoding.toLowerCase().contains(GZIP)) {
                isGzip = true;
                break;
            }
        }

        if (!isGzip) {
            return payload;
        }

        try {
            byte[] compressed = Base64.getDecoder().decode(payload);
            ByteArrayInputStream bis = new ByteArrayInputStream(compressed);
            GZIPInputStream gis = new GZIPInputStream(bis);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gis.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }

            gis.close();
            bos.close();

            return bos.toString("UTF-8");
        } catch (Exception e) {
            System.err.println("Failed to decode GZIP payload: " + e.getMessage());
            return payload;
        }
    }

    /**
     * Get header value by name (case-insensitive).
     */
    private static List<String> getHeaderIgnoreCase(Map<String, List<String>> headers, String headerName) {
        if (headers == null || headerName == null) {
            return null;
        }

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(headerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Normalize URL by stripping query parameters.
     * Example: /api/users?id=123 -> /api/users
     */
    public static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }

        int queryIndex = url.indexOf('?');
        if (queryIndex != -1) {
            return url.substring(0, queryIndex);
        }
        return url;
    }

    /**
     * Build API key for Bloom filter and database lookups.
     * Format: "apiCollectionId url method"
     */
    public static String buildApiKey(int apiCollectionId, String url, String method) {
        return apiCollectionId + " " + normalizeUrl(url) + " " + method;
    }
}
