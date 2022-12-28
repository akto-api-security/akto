package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.HardcodedAuthParam;
import com.akto.dto.testing.TestResult;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.testing.ApiExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OldApiVersionTest extends AuthRequiredTestPlugin{
    @Override
    public Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages) {
        String url = apiInfoKey.getUrl();
        String oldVersionUrl = decrementUrlVersion(url, 1, 1);
        if (oldVersionUrl == null) return null;

        RawApi rawApi = filteredMessages.get(0).copy();
        Result result = null;

        int i = 0;
        while (oldVersionUrl != null) {
            if (i >= 10) break;
            ApiInfo.ApiInfoKey oldVersionApiInfoKey = new ApiInfo.ApiInfoKey(apiInfoKey.getApiCollectionId(), oldVersionUrl, apiInfoKey.getMethod());

            // ignore if exists in traffic data
            if (testingUtil.getSampleMessages().containsKey(oldVersionApiInfoKey)) {
                oldVersionUrl = decrementUrlVersion(oldVersionUrl, 1, 1);
                continue;
            }

            i += 1;

            // change the url to oldVersionUrl
            OriginalHttpRequest testRequest = rawApi.getRequest().copy();
            testRequest.setUrl(oldVersionUrl);

            // hit the older version api once without changing anything else to get base condition to compare against
            OriginalHttpResponse originalHttpResponse;
            try {
                originalHttpResponse = ApiExecutor.sendRequest(testRequest, true);
            } catch (Exception e) {
                e.printStackTrace();
                oldVersionUrl = decrementUrlVersion(oldVersionUrl, 1, 1);
                continue;
            }

            // try BOLA
            BOLATest bolaTest = new BOLATest();
            RawApi dummy = new RawApi(testRequest, originalHttpResponse, rawApi.getOriginalMessage());
            List<BOLATest.ExecutorResult> executorResults = bolaTest.execute(dummy, apiInfoKey, testingUtil);
            result = convertExecutorResultsToResult(executorResults);

            if (result.isVulnerable) return result;

            oldVersionUrl = decrementUrlVersion(oldVersionUrl, 1, 1);
        }

        return result;
    }

    @Override
    public String superTestName() {
        return "BOLA";
    }

    @Override
    public String subTestName() {
        return "REPLACE_AUTH_TOKEN_OLD_VERSION";
    }
}
