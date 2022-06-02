package com.akto.rules;

import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.TestResult;
import com.akto.utils.RedactSampleData;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public abstract class TestPlugin {

    public abstract void start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId);

    public abstract String testName();

    public boolean isStatusGood(HttpResponseParams httpResponseParams) {
        return httpResponseParams.statusCode >= 200 && httpResponseParams.statusCode<300;
    }

    public boolean isStatusUnauthenticated(HttpResponseParams httpResponseParams) {
        return httpResponseParams.statusCode == 401;
    }

    public void addWithoutRequestError(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestResult.TestError testError) {
        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name());
        Bson update = Updates.set("resultMap." + testName(), new TestResult(null,false, Collections.singletonList(testError)));
        TestingRunResultDao.instance.updateOne(filter, update);
    }

    public void addWithRequestError(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId, TestResult.TestError testError, HttpResponseParams httpResponseParams) {
        Bson filter = TestingRunResultDao.generateFilter(testRunId, apiInfoKey.getApiCollectionId(), apiInfoKey.url, apiInfoKey.method.name());

        String message = null;
        try {
            message = RedactSampleData.convertHttpRespToOriginalString(httpResponseParams);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Bson update = Updates.set("resultMap." + testName(), new TestResult(message,false, Collections.singletonList(testError)));
        TestingRunResultDao.instance.updateOne(filter, update);
    }


    public void addTestSuccessResult(HttpResponseParams httpResponseParams, ObjectId testRunId, boolean vulnerable) {
        HttpRequestParams httpRequestParams = httpResponseParams.getRequestParams();

        String message = null;
        try {
            message = RedactSampleData.convertHttpRespToOriginalString(httpResponseParams);
        } catch (Exception e) {
            // TODO:
            e.printStackTrace();
            return;
        }

        Bson filter = TestingRunResultDao.generateFilter(testRunId, httpRequestParams);
        Bson update = Updates.set("resultMap." + testName(), new TestResult(message, vulnerable, new ArrayList<>()));
        TestingRunResultDao.instance.updateOne(filter, update);
    }

    public HttpResponseParams generateEmptyResponsePayload(HttpRequestParams httpRequestParams) {
        return new HttpResponseParams(
                "", 0,"", new HashMap<>(), null, httpRequestParams, Context.now(),
                1_000_000+"",false, HttpResponseParams.Source.OTHER, "",""
        );
    }

}
