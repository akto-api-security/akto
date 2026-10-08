package com.akto.threat.detection.utils;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.enums.RedactionType;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.detection.cache.AccountConfig;
import com.akto.threat.detection.cache.AccountConfigurationCache;
import com.akto.utils.RedactParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApiMetadata;
import com.akto.dto.api_protection_parse_layer.Condition.DistinctIdentifier;
import com.akto.dto.api_protection_parse_layer.Condition.ValueSource;
import com.akto.util.JSONUtils;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.dto.test_editor.Category;
import com.akto.dto.test_editor.Info;
import com.akto.threat.detection.hyperscan.ThreatCategory;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.Metadata;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SchemaConformanceError;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.threat.detection.constants.RedisKeyInfo;

public class Utils {

    private static final int MAX_REASON_MEMBERS = 10;

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

    public static FilterConfig getApiLevelRateLimitFilter() {
        FilterConfig filter = new FilterConfig("ApiLevelRateLimited", null, null, null);
        Info info = new Info();
        info.setName("ApiLevelRateLimited");
        info.setCategory(new Category("ApiAbuse", "ApiAbuse", "ApiAbuse"));
        info.setSubCategory("API_LEVEL_RATE_LIMITING");
        info.setSeverity("MEDIUM");
        filter.setInfo(info);
        return filter;
    }

    public static FilterConfig buildSequenceAnomalyFilter() {
        FilterConfig filter = new FilterConfig("SequenceAnomaly", null, null, null);
        Info info = new Info();
        info.setName("SequenceAnomaly");
        info.setCategory(new Category("Behavioral", "Behavioral", "Behavioral"));
        info.setSubCategory("ApiSequenceAnomaly");
        info.setSeverity("HIGH");
        filter.setInfo(info);
        return filter;
    }

    /**
     * Build a synthetic FilterConfig for a Hyperscan-detected threat category.
     * This allows Hyperscan results to flow through the same event pipeline as YAML filters.
     * Uses ThreatCategory enum as single source of truth for filter ID, category, and severity.
     */
    public static FilterConfig buildHyperscanFilterConfig(ThreatCategory tc) {
        FilterConfig filter = new FilterConfig(tc.getFilterId(), null, null, null);
        Info info = new Info();
        info.setName(tc.getFilterId());
        info.setCategory(tc.toYamlCategory());
        info.setSubCategory(tc.getCategoryName());
        info.setSeverity(tc.getSeverity());
        filter.setInfo(info);
        return filter;
    }

    public static SampleMaliciousRequest buildSampleMaliciousRequest(String actor, HttpResponseParams responseParam, FilterConfig apiFilter, RawApiMetadata metadata, List<SchemaConformanceError> errors, boolean successfulExploit, boolean ignoredEvent, RedactionType redactionType) {
        return buildSampleMaliciousRequest(actor, responseParam, apiFilter, metadata, errors, successfulExploit, ignoredEvent, redactionType, null);
    }

    public static SampleMaliciousRequest buildSampleMaliciousRequest(String actor, HttpResponseParams responseParam, FilterConfig apiFilter, RawApiMetadata metadata, List<SchemaConformanceError> errors, boolean successfulExploit, boolean ignoredEvent, RedactionType redactionType, String reason) {
        Metadata.Builder metadataBuilder = Metadata.newBuilder();
        if (errors != null && !errors.isEmpty()) {
            metadataBuilder.addAllSchemaErrors(errors);
        }
        boolean hasReason = reason != null && !reason.isEmpty();
        if (hasReason) {
            metadataBuilder.setReason(reason);
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

        
        if (metadata != null) {
            metadataBuilder.setCountryCode(metadata.getCountryCode());
            metadataBuilder.setDestCountryCode(metadata.getDestCountryCode() != null ? metadata.getDestCountryCode() : "");
        }
        // Keep the reason even when geo metadata couldn't be built
        if (metadata != null || hasReason) {
            maliciousReqBuilder.setMetadata(metadataBuilder.build());
        }
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

    private static final LoggerMaker logger = new LoggerMaker(Utils.class, LogDb.THREAT_DETECTION);

    public static String extractHostFromHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        List<String> hostValues = headers.get("host");
        if (hostValues != null && !hostValues.isEmpty()) {
            return hostValues.get(0);
        }
        return null;
    }

    public static RedactionType getRedactionType(Map<String, List<String>> headers, DataActor dataActor) {
        
        if(AccountConfig.isGraphQLAccount()){
            return RedactionType.NONE;
        }

        try {
            if (Context.isRedactPayload.get() != null && Context.isRedactPayload.get()) {
                return RedactionType.REDACT_ALL;
            }
            String host = extractHostFromHeaders(headers);
            if (host != null && !host.isEmpty()) {
                int hostHashCode = host.hashCode();
                AccountConfig config = AccountConfigurationCache.getInstance().getConfig(dataActor);
                Boolean isApiCollectionRedacted = config.isApiCollectionRedacted(hostHashCode);
                if (isApiCollectionRedacted != null && isApiCollectionRedacted) {
                    return RedactionType.REDACT_BY_API_COLLECTION;
                }
            }
            if (SingleTypeInfo.isCustomDataTypeAvailable(Context.accountId.get())) {
                return RedactionType.REDACT_BY_CUSTOM_FIELD;
            }
            return RedactionType.NONE;
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error determining redaction type, defaulting to NONE");
            return RedactionType.NONE;
        }
    }

    public static String extractIdentity(HttpResponseParams responseParam, ValueSource valueSource) {
        if (valueSource == null || valueSource.getSource() == null || valueSource.getKey() == null) return null;
        try {
            switch (valueSource.getSource()) {
                case "request_headers":
                    List<String> headerVals = responseParam.getRequestParams().getHeaders().get(valueSource.getKey());
                    return (headerVals != null && !headerVals.isEmpty()) ? headerVals.get(0) : null;
                case "request_payload":
                    return JSONUtils.extractValueForKey(responseParam.getRequestParams().getPayload(), valueSource.getKey());
                case "response_payload":
                    return JSONUtils.extractValueForKey(responseParam.getPayload(), valueSource.getKey());
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    /** Returns defaultAggKey when groupBy is unset, null when the identity is missing, else "<identity>|<groupKey>". */
    public static String buildAggKey(String defaultAggKey, ValueSource groupBy, HttpResponseParams responseParam, String groupKey) {
        if (groupBy == null) return defaultAggKey;
        String group = extractIdentity(responseParam, groupBy);
        return (group == null || group.isEmpty()) ? null : group + "|" + groupKey;
    }

    public static String buildDistinctReason(String groupLabel, DistinctIdentifier distinct, Set<String> members, int windowMinutes) {
        List<String> sorted = new ArrayList<>(new TreeSet<>(members));
        String shown = String.join(", ", sorted.subList(0, Math.min(sorted.size(), MAX_REASON_MEMBERS)));
        if (sorted.size() > MAX_REASON_MEMBERS) {
            shown += " +" + (sorted.size() - MAX_REASON_MEMBERS) + " more";
        }
        String dimension = distinct.getAttribute() != null ? distinct.getAttribute() : distinct.getKey();
        return groupLabel + ": " + sorted.size() + " distinct " + dimension + " within " + windowMinutes + " min (" + shown + ")";
    }

    public static SampleMaliciousRequest withReason(SampleMaliciousRequest request, String reason) {
        return request.toBuilder().setMetadata(request.getMetadata().toBuilder().setReason(reason)).build();
    }

    public static String extractDistinctValue(HttpResponseParams responseParam, RawApiMetadata metadata, DistinctIdentifier distinct) {
        if (distinct == null) return null;
        if ("country_code".equals(distinct.getAttribute())) {
            return metadata != null ? metadata.getCountryCode() : null;
        }
        return extractIdentity(responseParam, distinct);
    }

}