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

    /**
     * Build a synthetic FilterConfig for a Hyperscan-detected threat category.
     * This allows Hyperscan results to flow through the same event pipeline as YAML filters.
     */
    public static FilterConfig buildHyperscanFilterConfig(String category, String severity) {
        Category yamlCategory = mapCategoryToYamlCategory(category);
        String filterId = yamlCategory.getName();
        FilterConfig filter = new FilterConfig(filterId, null, null, null);
        Info info = new Info();
        info.setName(filterId);
        info.setCategory(yamlCategory);
        info.setSubCategory(category);
        info.setSeverity(severity != null ? severity : mapCategoryToSeverity(category));
        filter.setInfo(info);
        return filter;
    }

    /**
     * Map Hyperscan top-level category to the Category used by YAML filters,
     * so events show up consistently in the dashboard.
     */
    private static Category mapCategoryToYamlCategory(String category) {
        if (category == null) return new Category("SM", "Security Misconfiguration", "Security Misconfiguration (SM)");
        switch (category.toLowerCase()) {
            case "sqli":
                return new Category("SQL_INJECTION", "SQL Injection", "SQL Injection");
            case "nosql":
                return new Category("NOSQL_INJECTION", "NoSQL Injection", "NoSQL Injection");
            case "xss":
                return new Category("XSS", "XSS", "Cross-site scripting (XSS)");
            case "os_cmd":
                return new Category("OS_COMMAND_INJECTION", "OS Command Injection", "OS Command Injection");
            case "windows":
                return new Category("COMMAND_INJECTION", "Command Injection", "Command Injection");
            case "ssrf":
                return new Category("SSRF", "Server Side Request Forgery", "Server Side Request Forgery (SSRF)");
            case "lfi":
                return new Category("LFI_RFI", "Local File Inclusion", "Local File Inclusion / Remote File Inclusion (LFI/RFI)");
            case "xxe":
                return new Category("COMMAND_INJECTION", "Command Injection", "Command Injection");
            case "ldap":
                return new Category("COMMAND_INJECTION", "Command Injection", "Command Injection");
            case "ssti":
                return new Category("SSTI", "Server Side Template Injection", "Server Side Template Injection (SSTI)");
            case "debug":
            case "version":
                return new Category("SecurityMisconfig", "Security Misconfiguration", "Security Misconfiguration");
            case "stack_trace":
                return new Category("SecurityMisconfig", "Security Misconfiguration", "Security Misconfiguration");
            default:
                return new Category("SecurityMisconfig", "Security Misconfiguration", "Security Misconfiguration");
        }
    }

    private static String mapCategoryToSeverity(String category) {
        if (category == null) return "MEDIUM";
        switch (category.toLowerCase()) {
            case "sqli": case "os_cmd": case "xxe": case "nosql": case "windows":
            case "xss": case "ssrf": case "lfi": case "ssti": case "ldap":
                return "HIGH";
            case "debug": case "version": case "stack_trace":
                return "LOW";
            default:
                return "MEDIUM";
        }
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
            metadataBuilder.setDestCountryCode(metadata.getDestCountryCode() != null ? metadata.getDestCountryCode() : "");
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