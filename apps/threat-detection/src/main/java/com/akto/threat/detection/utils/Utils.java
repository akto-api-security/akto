package com.akto.threat.detection.utils;

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
import com.akto.threat.detection.constants.RedisKeyInfo;

public class Utils {

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

    public static SampleMaliciousRequest buildSampleMaliciousRequest(String actor, HttpResponseParams responseParam, FilterConfig apiFilter, RawApiMetadata metadata, List<SchemaConformanceError> errors, boolean successfulExploit, boolean ignoredEvent) {
        Metadata.Builder metadataBuilder = Metadata.newBuilder();
        if(errors != null && !errors.isEmpty()) {
            metadataBuilder.addAllSchemaErrors(errors);
        }
        
        // Determine status based on ignoredEvent flag
        String status = ignoredEvent ? com.akto.util.ThreatDetectionConstants.IGNORED : com.akto.util.ThreatDetectionConstants.ACTIVE;
        
        SampleMaliciousRequest.Builder maliciousReqBuilder = SampleMaliciousRequest.newBuilder()
            .setUrl(responseParam.getRequestParams().getURL())
            .setMethod(responseParam.getRequestParams().getMethod())
            .setPayload(responseParam.getOriginalMsg().get())
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