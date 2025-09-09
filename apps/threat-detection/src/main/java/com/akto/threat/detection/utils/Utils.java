package com.akto.threat.detection.utils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApiMetadata;
import com.akto.dto.monitoring.FilterConfig;
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

    public static SampleMaliciousRequest buildSampleMaliciousRequest(String actor, HttpResponseParams responseParam, FilterConfig apiFilter, RawApiMetadata metadata, List<SchemaConformanceError> errors) {
        Metadata.Builder metadataBuilder = Metadata.newBuilder();
        if(errors != null && !errors.isEmpty()) {
            metadataBuilder.addAllSchemaErrors(errors);
        }
        
        SampleMaliciousRequest.Builder maliciousReqBuilder = SampleMaliciousRequest.newBuilder()
            .setUrl(responseParam.getRequestParams().getURL())
            .setMethod(responseParam.getRequestParams().getMethod())
            .setPayload(responseParam.getOriginalMsg().get())
            .setIp(actor) // For now using actor as IP
            .setApiCollectionId(responseParam.getRequestParams().getApiCollectionId())
            .setTimestamp(responseParam.getTime())
            .setFilterId(apiFilter.getId());

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