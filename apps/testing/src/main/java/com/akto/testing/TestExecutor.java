package com.akto.testing;

import com.akto.dao.ParamTypeInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.CollectionWiseTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.type.ParamTypeInfo;
import com.akto.dto.type.RequestTemplate;
import com.akto.rules.BOLATest;
import com.akto.rules.NoAuthTest;
import com.mongodb.client.model.Filters;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TestExecutor {

    public static String slashHandling(String url) {
        if (!url.startsWith("/")) url = "/"+url;
        if (!url.endsWith("/")) url = url+"/";
        return url;
    }

    Map<String, ParamTypeInfo> paramTypeInfoMap = new HashMap<>();

    private static final Logger logger = LoggerFactory.getLogger(TestExecutor.class);
    public void init(TestingRun testingRun) {
        TestingEndpoints testingEndpoints = testingRun.getTestingEndpoints();

        buildParameterInfoMap(testingEndpoints);

        List<ApiInfo.ApiInfoKey> apiInfoKeyList = testingEndpoints.returnApis();
        if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
        System.out.println("APIs: " + apiInfoKeyList.size());

        bolaTest = new BOLATest(this.paramTypeInfoMap);
        noAuthTest = new NoAuthTest();

        Set<ApiInfo.ApiInfoKey> store = new HashSet<>();
        for (ApiInfo.ApiInfoKey apiInfoKey: apiInfoKeyList) {
            try {
                String url = slashHandling(apiInfoKey.url+"");
                ApiInfo.ApiInfoKey modifiedKey = new ApiInfo.ApiInfoKey(apiInfoKey.getApiCollectionId(), url, apiInfoKey.method);
                if (store.contains(modifiedKey)) continue;
                store.add(modifiedKey);
                start(apiInfoKey, testingRun.getTestIdConfig(), testingRun.getId());
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        }
    }

    private BOLATest bolaTest;
    private NoAuthTest noAuthTest;

    public void start(ApiInfo.ApiInfoKey apiInfoKey, int testIdConfig, ObjectId testRunId) {
        if (testIdConfig != 0) return;

        boolean noAuthResult = noAuthTest.start(apiInfoKey, testRunId);
        if (!noAuthResult) {
            bolaTest.start(apiInfoKey, testRunId);
        }

    }

    public void buildParameterInfoMap(TestingEndpoints testingEndpoints) {
        TestingEndpoints.Type type = testingEndpoints.getType();
        List<ParamTypeInfo> paramTypeInfoList = new ArrayList<>();
        paramTypeInfoMap = new HashMap<>();
        try {
            if (type.equals(TestingEndpoints.Type.COLLECTION_WISE)) {
                CollectionWiseTestingEndpoints collectionWiseTestingEndpoints = (CollectionWiseTestingEndpoints) testingEndpoints;
                int apiCollectionId = collectionWiseTestingEndpoints.getApiCollectionId();
                paramTypeInfoList = ParamTypeInfoDao.instance.findAll(Filters.eq(ParamTypeInfo.API_COLLECTION_ID, apiCollectionId));
            } else {
                logger.error("ONLY COLLECTION TYPE TESTING ENDPOINTS ALLOWED");
            }

            for (ParamTypeInfo paramTypeInfo: paramTypeInfoList) {
                paramTypeInfoMap.put(paramTypeInfo.composeKey(), paramTypeInfo);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
