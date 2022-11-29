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
import com.akto.testing.ApiExecutor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class OldApiVersionTest extends TestPlugin {
    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, AuthMechanism authMechanism, Map<ApiInfo.ApiInfoKey, List<String>> sampleMessages, Map<String, SingleTypeInfo> singleTypeInfos) {
        String url = apiInfoKey.getUrl();
        String oldVersionUrl = decrementUrlVersion(url, 1, 1);
        if (oldVersionUrl == null) return null;

        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, sampleMessages);
        if (messages.isEmpty()) return null;
        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, authMechanism);
        if (filteredMessages.isEmpty()) return null;

        RawApi rawApi = filteredMessages.get(0).copy();
        Result result = null;

        boolean foundOlderVersionApiThatWorks = false;

        while (oldVersionUrl != null) { // todo: add a check for max
            ApiInfo.ApiInfoKey oldVersionApiInfoKey = new ApiInfo.ApiInfoKey(apiInfoKey.getApiCollectionId(), oldVersionUrl, apiInfoKey.getMethod());

            // ignore if exists in traffic data
            if (sampleMessages.containsKey(oldVersionApiInfoKey)) {
                oldVersionUrl = decrementUrlVersion(oldVersionUrl, 1, 1);
                continue;
            }

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

            boolean isStatusGood = isStatusGood(originalHttpResponse.getStatusCode());
            if (!isStatusGood) {
                oldVersionUrl = decrementUrlVersion(oldVersionUrl, 1, 1);
                continue;
            }

            foundOlderVersionApiThatWorks = true;

            // try BOLA
            BOLATest bolaTest = new BOLATest();
            RawApi dummy = new RawApi(testRequest, originalHttpResponse, rawApi.getOriginalMessage());
            BOLATest.ExecutorResult executorResult = bolaTest.execute(dummy, apiInfoKey, authMechanism, singleTypeInfos);
            TestResult.TestError testError = executorResult.testError;
            if (testError != null) {
                return addWithRequestError(executorResult.rawApi.getOriginalMessage(), testError, executorResult.rawApi.getRequest());
            } else {
                 TestResult testResult = buildTestResult(
                        executorResult.testRequest, executorResult.testResponse, executorResult.rawApi.getOriginalMessage(),
                        executorResult.percentageMatch, executorResult.vulnerable
                 );

                 result = addTestSuccessResult(
                                 executorResult.vulnerable, Collections.singletonList(testResult), executorResult.singleTypeInfos, executorResult.confidence
                 );

                if (executorResult.vulnerable) return result;
            }

            oldVersionUrl = decrementUrlVersion(oldVersionUrl, 1, 1);
        }

        if (!foundOlderVersionApiThatWorks) return null;

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
