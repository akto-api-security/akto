package com.akto.rules;

import com.akto.DaoInit;
import com.akto.dao.ParamTypeInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestResult;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.ParamTypeInfo;
import com.akto.parsers.HttpCallParser;
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.testing.ApiExecutor;
import com.akto.testing.StatusCodeAnalyser;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import org.bson.types.ObjectId;

import java.util.List;

public class BOLATest extends TestPlugin {

    public BOLATest() { }

    @Override
    public boolean start(ApiInfo.ApiInfoKey apiInfoKey, ObjectId testRunId) {
        HttpResponseParams originalHttpResponseParams = SampleMessageStore.fetchOriginalMessage(apiInfoKey);
        if (originalHttpResponseParams == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_PATH);
            return false;
        }

        AuthMechanism authMechanism = AuthMechanismStore.getAuthMechanism();
        if (authMechanism == null) {
            addWithoutRequestError(apiInfoKey, testRunId, TestResult.TestError.NO_AUTH_MECHANISM);
            return false;
        }

        HttpRequestParams httpRequestParams = originalHttpResponseParams.getRequestParams();
        boolean result = authMechanism.addAuthToRequest(httpRequestParams);
        if (!result) return false; // this means that auth token was not there in original request so exit

        boolean containsPrivateResource = containsPrivateResource(httpRequestParams, apiInfoKey);
        if (!containsPrivateResource) {
            HttpResponseParams newHttpResponseParams = generateEmptyResponsePayload(httpRequestParams);
            addTestSuccessResult(apiInfoKey, newHttpResponseParams, testRunId, false);
            return false;
        }

        HttpResponseParams httpResponseParams = null;
        try {
            httpResponseParams = ApiExecutor.sendRequest(httpRequestParams);
        } catch (Exception e) {
            HttpResponseParams newHttpResponseParams = generateEmptyResponsePayload(httpRequestParams);
            addWithRequestError(apiInfoKey, testRunId, TestResult.TestError.API_REQUEST_FAILED, newHttpResponseParams);
            return false;
        }

        int statusCode = StatusCodeAnalyser.getStatusCode(httpResponseParams);
        boolean vulnerable = isStatusGood(statusCode);
        if (vulnerable) {
            double val = compareWithOriginalResponse(originalHttpResponseParams.getPayload(), httpResponseParams.getPayload());
            vulnerable = val > 90;
        }

        addTestSuccessResult(apiInfoKey,httpResponseParams, testRunId, vulnerable);

        return vulnerable;
    }

    @Override
    public String testName() {
        return "BOLA";
    }



    public static void main(String[] args) {
        DaoInit.init(new ConnectionString("mongodb://172.18.0.2:27017/admini"));
        Context.accountId.set(1_000_000);
        List<ParamTypeInfo> paramTypeInfoList = ParamTypeInfoDao.instance.findAll(new BasicDBObject());

        for (ParamTypeInfo paramTypeInfo: paramTypeInfoList) {
            SampleMessageStore.paramTypeInfoMap.put(paramTypeInfo.composeKey(), paramTypeInfo);
        }

        BOLATest bolaTest = new BOLATest();
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(new BasicDBObject());
        int idx = 0;
        for (SampleData sampleData: sampleDataList) {
            if (sampleData.getSamples().isEmpty()) continue;
            String message = sampleData.getSamples().get(0);
            try {
                HttpResponseParams httpResponseParams = HttpCallParser.parseKafkaMessage(message);
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(sampleData.getId().getApiCollectionId(), sampleData.getId().getUrl() ,sampleData.getId().method);
                boolean f = bolaTest.containsPrivateResource(httpResponseParams.requestParams, apiInfoKey);
                if (f) {
                    idx += 1;
                    System.out.println(apiInfoKey);
                    String[] v = httpResponseParams.requestParams.url.split("\\?");
                    if (v.length > 1) System.out.println(v[1]);
                    System.out.println(httpResponseParams.getRequestParams().getPayload());
                    System.out.println("*****************************************************************");
                } else {
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println(idx);
        System.out.println(sampleDataList.size());
    }
}
