package com.akto.otel;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Pair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static com.akto.util.Constants.AKTO_GEN_AI_TAG;

import java.util.*;

/**
 * Converts OpenTelemetry spans from Datadog format to Akto HttpResponseParams format
 */
public class OtelSpanConverter {

    private static final LoggerMaker logger = new LoggerMaker(OtelSpanConverter.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // OpenTelemetry semantic convention attribute names
    private static final class OtelAttributes {
        static final String INPUT = "input";
        static final String OUTPUT = "output";

        static final Set<String> RESPONSE_HEADERS_ATTRIBUTES = new HashSet<>(Arrays.asList( "duration", "trace_id", "span_id"));
        static final Set<String> BODY_ATTRIBUTES = new HashSet<>(Arrays.asList("input", "output"));
    }

    private String extractString(JsonNode attributes, String key) {
        JsonNode node = attributes.get(key);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    public ConversionResult convert(String datadogResponseJson, int accountId) throws Exception {
        JsonNode root = objectMapper.readTree(datadogResponseJson);
        JsonNode dataNode = root.get("data");

        if (dataNode == null || !dataNode.isArray()) {
            return new ConversionResult(Collections.emptyList(), 0, 0);
        }

        List<HttpResponseParams> convertedTraces = new ArrayList<>();
        int totalProcessed = dataNode.size();

        for (JsonNode span : dataNode) {
            try {
                HttpResponseParams trace = convertSpan(span, accountId);
                if (trace != null) {
                    convertedTraces.add(trace);
                }
            } catch (Exception e) {
                logger.errorAndAddToDb("Failed to convert span: " + e.getMessage());
            }
        }

        return new ConversionResult(convertedTraces, totalProcessed, convertedTraces.size());
    }

    private Pair<Integer, String> getApiCollectionId(JsonNode attributes) {
        int apiCollectionId = 0;
        String hostName = null;
        JsonNode tagsNode = attributes.get("tags");
        if (tagsNode != null && tagsNode.isArray()) {
            String serviceName = null;
            for (JsonNode tag : tagsNode) {
                if(tag.asText().contains("service")) {
                    serviceName = tag.asText();
                    break;
                }
            }
            String[] serviceNameParts = serviceName.split(":");
            hostName = serviceNameParts[1];

            ApiCollection apiCollection = ApiCollectionsDao.instance.findByHost(hostName);
            if(apiCollection == null){
                ApiCollection newApiCollection = new ApiCollection();
                apiCollectionId = hostName.hashCode();
                newApiCollection.setId(apiCollectionId);
                newApiCollection.setHostName(hostName);
                newApiCollection.setStartTs(Context.now());
                newApiCollection.setTagsList(Collections.singletonList(new CollectionTags(Context.now(), AKTO_GEN_AI_TAG, "DataDog Workflow", CollectionTags.TagSource.KUBERNETES)));
                ApiCollectionsDao.instance.insertOne(newApiCollection);
            }else{
                apiCollectionId = apiCollection.getId();
                hostName = apiCollection.getHostName();
            }
        }
        return new Pair<>(apiCollectionId, hostName);
    }

    private HttpResponseParams convertSpan(JsonNode span, int accountId) {
        JsonNode attributes = span.get("attributes");
        if (attributes == null) {
            return null;
        }

        Pair<Integer, String> apiCollectionInfo = getApiCollectionId(attributes);

        HttpRequestParams requestParams = buildRequestParams(attributes, apiCollectionInfo.getFirst(), apiCollectionInfo.getSecond());
        return buildResponseParams(attributes, requestParams, accountId);
    }

    private HttpRequestParams buildRequestParams(JsonNode attributes, int apiCollectionId, String hostName) {
        HttpRequestParams requestParams = new HttpRequestParams();
        requestParams.setMethod("POST"); // TODO: get the method from the attributes for tools call
        requestParams.setUrl("/invoke"); // TODO: get the url from the attributes for tools call
        requestParams.type = "HTTP/1.1";

        // Map<String, List<String>> requestHeaders = extractHeaders(attributes, OtelAttributes.HTTP_REQUEST_HEADERS_PREFIX);
        Map<String, List<String>> requestHeaders = extractHeaders(attributes, false);
        requestHeaders.put("host", Collections.singletonList(hostName));
        requestParams.setHeaders(requestHeaders);

        String requestBody = extractString(attributes, OtelAttributes.INPUT);
        requestParams.setPayload(requestBody != null ? requestBody : "{}");
        requestParams.setApiCollectionId(apiCollectionId);

        return requestParams;
    }

    private HttpResponseParams buildResponseParams(JsonNode attributes, HttpRequestParams requestParams, int accountId) {
        String responseBody = extractString(attributes, OtelAttributes.OUTPUT);
        Map<String, List<String>> responseHeaders = extractHeaders(attributes, true);

        int status = 200;
        long timestampNanos = attributes.has("start_ns") ? attributes.get("start_ns").asLong() : 0;
        int timestampSeconds = timestampNanos > 0 ? (int) (timestampNanos / 1000) : Context.now();

        String ipString = "0.0.0.0/0";

        return new HttpResponseParams(
            "HTTP/1.1",
            status,
            "OK",
            responseHeaders,
            responseBody != null ? responseBody : "{}",
            requestParams,
            timestampSeconds,
            String.valueOf(accountId),
            false,
            HttpResponseParams.Source.OTHER,
            "opentelemetry",
            ipString,
            ipString,
            ""
        );
    }

    private Map<String, List<String>> extractHeaders(JsonNode attributes, boolean responseHeaders) {
        Map<String, List<String>> headers = new HashMap<>();

        attributes.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if(OtelAttributes.BODY_ATTRIBUTES.contains(key)) {return;}
            if (responseHeaders && OtelAttributes.RESPONSE_HEADERS_ATTRIBUTES.contains(key) ) {
                String headerName = "X-" + key;
                String headerValue = entry.getValue().asText();
                headers.put(headerName, Collections.singletonList(headerValue));
            }else if (!responseHeaders) {
                String headerName = "X-" + key;
                String headerValue = entry.getValue().asText();
                headers.put(headerName, Collections.singletonList(headerValue));
            }
        });

        return headers;
    }
    /**
     * Result of span conversion operation
     */
    public static class ConversionResult {
        private final List<HttpResponseParams> traces;
        private final int totalProcessed;
        private final int successfullyConverted;

        public ConversionResult(List<HttpResponseParams> traces, int totalProcessed, int successfullyConverted) {
            this.traces = traces;
            this.totalProcessed = totalProcessed;
            this.successfullyConverted = successfullyConverted;
        }

        public List<HttpResponseParams> getTraces() {
            return traces;
        }

        public int getTotalProcessed() {
            return totalProcessed;
        }

        public int getSuccessfullyConverted() {
            return successfullyConverted;
        }
    }
}
