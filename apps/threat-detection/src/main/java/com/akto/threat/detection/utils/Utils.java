package com.akto.threat.detection.utils;

import com.akto.dto.HttpResponseParams;
import com.akto.dto.RawApiMetadata;
import com.akto.dto.monitoring.FilterConfig;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.Metadata;
import com.akto.proto.generated.threat_detection.message.sample_request.v1.SampleMaliciousRequest;
import com.akto.threat.detection.constants.RedisKeyInfo;

public class Utils {

    public static String buildApiHitCountKey(int apiCollectionId, String url, String method) {
        return RedisKeyInfo.API_COUNTER_KEY_PREFIX + "|" + apiCollectionId + "|" + url + "|" + method;
    }

    public static SampleMaliciousRequest buildSampleMaliciousRequest(String actor, HttpResponseParams responseParam, FilterConfig apiFilter, RawApiMetadata metadata) {
        return SampleMaliciousRequest.newBuilder()
              .setUrl(responseParam.getRequestParams().getURL())
              .setMethod(responseParam.getRequestParams().getMethod())
              .setPayload(responseParam.getOriginalMsg().get())
              .setIp(actor) // For now using actor as IP
              .setApiCollectionId(responseParam.getRequestParams().getApiCollectionId())
              .setTimestamp(responseParam.getTime())
              .setFilterId(apiFilter.getId())
              .setMetadata(Metadata.newBuilder().setCountryCode(metadata.getCountryCode()))
              .build();
    }
}