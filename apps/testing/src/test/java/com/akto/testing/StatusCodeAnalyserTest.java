package com.akto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.store.SampleMessageStore;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class StatusCodeAnalyserTest {

    @Test
    public void testPotentialStatusCodeKeys() {
        Map<String, Set<String>> responseParamMap = new HashMap<>();
        responseParamMap.put("something", null);
        responseParamMap.put("status#reason", Collections.singleton(null));
        responseParamMap.put("status#code", Collections.singleton("200"));
        responseParamMap.put("status#message", Collections.singleton("message"));
        responseParamMap.put("status#type", Collections.singleton("type"));
        responseParamMap.put("status#title", Collections.singleton("title"));

        List<String> potentialStatusCodeKeys = StatusCodeAnalyser.getPotentialStatusCodeKeys(responseParamMap);
        assertEquals(potentialStatusCodeKeys.size(), 1);

        responseParamMap.put("status#type", Collections.singleton("300"));
        potentialStatusCodeKeys = StatusCodeAnalyser.getPotentialStatusCodeKeys(responseParamMap);
        assertEquals(potentialStatusCodeKeys.size(), 1);

        responseParamMap.put("status#code", Collections.singleton("2000"));
        responseParamMap.put("status#type", Collections.singleton("type"));
        potentialStatusCodeKeys = StatusCodeAnalyser.getPotentialStatusCodeKeys(responseParamMap);
        assertEquals(potentialStatusCodeKeys.size(), 0);
    }


    @Test
    public void testGetStatusCode() {
        StatusCodeAnalyser.result = new ArrayList<>();
        StatusCodeAnalyser.result.add(new StatusCodeAnalyser.StatusCodeIdentifier(new HashSet<>(Arrays.asList("status#code", "status#reason", "status#message", "status#type", "status#title")), "status#code"));

        HttpResponseParams httpResponseParams = new HttpResponseParams();
        httpResponseParams.statusCode = 199;
        int statusCode = StatusCodeAnalyser.getStatusCode(httpResponseParams);
        assertEquals(statusCode, 199);

        httpResponseParams.statusCode = 300;
        statusCode = StatusCodeAnalyser.getStatusCode(httpResponseParams);
        assertEquals(statusCode, 300);

        httpResponseParams.statusCode = 204;
        httpResponseParams.setPayload("{\"status\":{\"code\":401,\"message\":\"UNAUTHORIZED\",\"reason\":\"\",\"type\":\"\",\"title\":\"\"}}");
        statusCode = StatusCodeAnalyser.getStatusCode(httpResponseParams);
        assertEquals(statusCode,401);

        httpResponseParams.statusCode = 204;
        httpResponseParams.setPayload("{\"status\":{\"code\":200,\"message\":\"OK\",\"reason\":\"\",\"type\":\"\",\"title\":\"\"},\"payload\":{\"kycIdType\":\"NONE\",\"kycStatus\":\"NOT_FOUND\",\"kycUploadedTime\":\"\",\"kycRejectionReason\":\"\",\"kycRemainingAttemptCount\":60,\"kycRequired\":false}}");
        statusCode = StatusCodeAnalyser.getStatusCode(httpResponseParams);
        assertEquals(statusCode,200);
    }
}
