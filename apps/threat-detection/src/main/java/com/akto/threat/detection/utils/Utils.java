package com.akto.threat.detection.utils;

import com.akto.enums.RedactionType;
import com.akto.utils.RedactParser;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApiMetadata;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.Category;
import com.akto.dto.test_editor.Info;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.Metadata;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.threat.detection.constants.RedisKeyInfo;

public class Utils {

    /**
     * Applies redaction to HttpResponseParams and converts to HttpResponseParam protobuf string
     */
    private static String getRedactedPayload(HttpResponseParams responseParam, RedactionType redactionType) throws Exception{
        // Create a copy to avoid mutating the original
        HttpResponseParams copy = responseParam.copy();
        // Apply redaction

        RedactParser.redactHttpResponseParam(copy, redactionType);


        // Convert redacted HttpResponseParams to HttpResponseParam protobuf
        HttpResponseParam.Builder builder = HttpResponseParam.newBuilder();

        // Set request fields
        builder.setMethod(copy.getRequestParams().getMethod());
        builder.setPath(copy.getRequestParams().getURL());
        builder.setType(copy.type);
        builder.setRequestPayload(copy.getRequestParams().getPayload());

        // Convert request headers to StringList map
        if (copy.getRequestParams().getHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : copy.getRequestParams().getHeaders().entrySet()) {
                StringList stringList = StringList.newBuilder().addAllValues(entry.getValue()).build();
                builder.putRequestHeaders(entry.getKey(), stringList);
            }
        }

        // Set response fields
        builder.setStatusCode(copy.getStatusCode());
        builder.setStatus(copy.status);
        builder.setResponsePayload(copy.getPayload());

        // Convert response headers to StringList map
        if (copy.getHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : copy.getHeaders().entrySet()) {
                StringList stringList = StringList.newBuilder().addAllValues(entry.getValue()).build();
                builder.putResponseHeaders(entry.getKey(), stringList);
            }
        }

        // Set metadata fields
        builder.setTime(copy.getTime());
        builder.setAktoAccountId(copy.getAccountId());
        builder.setIp(copy.getSourceIP());
        builder.setDestIp(copy.getDestIP() != null ? copy.getDestIP() : "");
        builder.setDirection(copy.getDirection() != null ? copy.getDirection() : "");
        builder.setSource(copy.getSource().name());
        builder.setAktoVxlanId(String.valueOf(copy.getRequestParams().getApiCollectionId()));
        builder.setIsPending(copy.getIsPending());

        // Build and return as string (protobuf toString format)
        return builder.build().toString();
    }

    public static String buildApiHitCountKey(int apiCollectionId, String url, String method) {
        return RedisKeyInfo.API_COUNTER_KEY_PREFIX + "|" + apiCollectionId + "|" + url + "|" + method;
    }

    public static String buildIpApiCmsDataKey(String ip, String apiCollectionId, String url, String method) {
        return RedisKeyInfo.IP_API_CMS_DATA_PREFIX + "|" + apiCollectionId + "|" + ip + "|" + url + "|" + method;
    }

    public static String buildApiDistributionKey(String apiCollectionId, String url, String method) {
        return apiCollectionId + "|" + url + "|" + method;
    }

    public static FilterConfig getipApiRateLimitFilter() {
        FilterConfig ipApiRateLimitFilter = new FilterConfig("IpApiRateLimited", null, null, null);
        Info info = new Info();
        info.setName("IpApiRateLimited");
        info.setCategory(new Category("ApiAbuse", "ApiAbuse", "ApiAbuse"));
        info.setSubCategory("RateLimiting");
        info.setSeverity("MEDIUM");
        ipApiRateLimitFilter.setInfo(info);
        return ipApiRateLimitFilter;
    }

    public static SampleMaliciousRequest buildSampleMaliciousRequest(String actor, HttpResponseParams responseParam, FilterConfig apiFilter, RawApiMetadata metadata, List<SchemaConformanceError> errors, boolean successfulExploit, boolean ignoredEvent, RedactionType redactionType) {
        Metadata.Builder metadataBuilder = Metadata.newBuilder();
        if (errors != null && !errors.isEmpty()) {
            metadataBuilder.addAllSchemaErrors(errors);
        }

        // Determine status based on ignoredEvent flag
        String status = ignoredEvent ? com.akto.util.ThreatDetectionConstants.IGNORED : com.akto.util.ThreatDetectionConstants.ACTIVE;

        String redactedPayload = responseParam.getOriginalMsg().get();
        if (redactionType != RedactionType.NONE) {
            // Redact sensitive data from the payload
            try {
                redactedPayload = getRedactedPayload(responseParam, redactionType);
            } catch (Exception e) {
                // If redaction fails, fall back to original message
            }
        }
            SampleMaliciousRequest.Builder maliciousReqBuilder = SampleMaliciousRequest.newBuilder()
                    .setUrl(responseParam.getRequestParams().getURL())
                    .setMethod(responseParam.getRequestParams().getMethod())
                    .setPayload(redactedPayload)
                    .setIp(actor) // For now using actor as IP
                    .setApiCollectionId(responseParam.getRequestParams().getApiCollectionId())
                    .setTimestamp(responseParam.getTime())
                    .setFilterId(apiFilter.getId())
                    .setSuccessfulExploit(successfulExploit)
                    .setStatus(status);

            metadataBuilder.setCountryCode(metadata.getCountryCode());
            maliciousReqBuilder.setMetadata(metadataBuilder.build());
            return maliciousReqBuilder.build();
        }

    public static boolean apiDistributionEnabled(boolean redisEnabled, boolean apiDistributionEnabled) {
        if (!redisEnabled) {
            return false;
        }
        return apiDistributionEnabled;
    }

    public static String getThreatProtectionBackendUrl() {
        return System.getenv("AKTO_THREAT_PROTECTION_BACKEND_URL");
    }

    public static Map<String, List<String>> buildHeaders() {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Authorization", Collections.singletonList("Bearer " + System.getenv("AKTO_THREAT_PROTECTION_BACKEND_TOKEN")));
        return headers;
    }

}