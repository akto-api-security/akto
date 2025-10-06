package com.akto.imperva;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.HttpRequestResponseUtils;
import com.akto.dto.HttpResponseParams;
import com.akto.util.JSONUtils;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.akto.utils.JsonUtils;

public class ImpervaUtils {
    
    private static final String VISA_HOST = "api.authorize.net";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final LoggerMaker logger = new LoggerMaker(ImpervaUtils.class, LogDb.RUNTIME);
    private static final Set<String> WHITELISTED_CONTENT_TYPES = new HashSet<>(
        Arrays.asList(
            HttpRequestResponseUtils.APPLICATION_JSON,
            HttpRequestResponseUtils.APPLICATION_XML,
            HttpRequestResponseUtils.APPLICATION_SOAP_XML
        ));


    public static List<HttpResponseParams> parseImpervaResponse(HttpResponseParams responseParams) {
        try {
            if (responseParams == null || responseParams.getRequestParams() == null) {
                return Collections.emptyList();
            }

            String requestPayload = responseParams.getRequestParams().getPayload();

            if (!isImpervaRequest(responseParams)) {
                return Collections.emptyList();
            }

            Map<String, Object> payloadMap = JsonUtils.getMap(requestPayload);
            if (payloadMap == null || payloadMap.isEmpty()) {
                return Collections.emptyList();
            }

            List<HttpResponseParams> results = new java.util.ArrayList<>();

            for (Map.Entry<String, Object> entry : payloadMap.entrySet()) {
                String rootKey = entry.getKey();
                Object subPayloadObj = entry.getValue();

                String newUrl = HttpResponseParams.addPathParamToUrl(responseParams.getRequestParams().getURL(),
                    rootKey);
                String computedPayload;
                try {
                    Map<String, Object> payloadWithRootKey = new java.util.HashMap<>();
                    payloadWithRootKey.put(rootKey, subPayloadObj);
                    computedPayload = mapper.writeValueAsString(payloadWithRootKey);
                } catch (Exception e) {
                    logger.error("[Skipping] Error while creating JSON for Imperva Reuqest. key: {}", rootKey, e);
                    continue;
                }
                final String newRequestPayload = computedPayload;

                HttpResponseParams copy = responseParams.copy();
                copy.getRequestParams().setUrl(newUrl);
                copy.getRequestParams().setPayload(newRequestPayload);

                copy.setPayload("");
                copy.setHeaders(Collections.emptyMap());

                modifyOriginalHttpMessage(copy, newRequestPayload);
                results.add(copy);
            }
            return results;
        } catch (Exception e) {
            logger.error("Error parsing response in ImpervaUtils", e);
            return Collections.emptyList();
        }
    }

    private static boolean isImpervaRequest(HttpResponseParams responseParams) {
        if (HttpResponseParams.Source.IMPERVA != responseParams.getSource()) {
            return false;
        }

        Map<String, List<String>> headers = responseParams.getRequestParams().getHeaders();
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        
        boolean isHostHeaderVisa = false;
        boolean isContentTypeJsonOrXml = false;

        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String headerKey = entry.getKey().toLowerCase();
            List<String> headerValues = entry.getValue();

            if ("host".equals(headerKey)) {
                isHostHeaderVisa = headerValues.stream().anyMatch(VISA_HOST::equals);
            }

            if ("content-type".equals(headerKey)) {
                isContentTypeJsonOrXml = headerValues.stream()
                        .anyMatch(header -> {
                            String lowerHeader = header.toLowerCase();
                            return WHITELISTED_CONTENT_TYPES.stream()
                                    .anyMatch(lowerHeader::contains);
                        });
            }
        }
        return isHostHeaderVisa && isContentTypeJsonOrXml;
    }

    private static void modifyOriginalHttpMessage(HttpResponseParams responseParams, String newRequestPayload) {
        String origReqJson = responseParams.getOrig();
        try {
            Map<String, Object> origReq = JSONUtils.getMap(origReqJson);
            origReq.put("requestPayload", newRequestPayload);
            origReq.remove("responsePayload");
            responseParams.setOrig(JSONUtils.getString(origReq));
        } catch (Exception e) {
            logger.error("Error parsing original HTTP message as JSON. Not updating sample data for MCP tools/call", e);
        }
    }
}