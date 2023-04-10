package com.akto.testing;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AccessMatrixTaskInfo;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.CollectionWiseTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.BFLATest;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

public class AccessMatrixAnalyzer {
    private static final LoggerMaker loggerMaker = new LoggerMaker(AccessMatrixAnalyzer.class);

    public List<ApiInfoKey> getEndpointsToAnalyze(AccessMatrixTaskInfo task) {
        final List<ApiInfoKey> endpoints = new ArrayList<>();
        if(task.getApiInfoKeys() == null || task.getApiInfoKeys().isEmpty()) {
            List<BasicDBObject> list = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(task.getApiCollectionId(), 0, 10_000, 60 * 30 * 24 * 30);

            if (list != null && !list.isEmpty()) {
                list.forEach(element -> {
                    BasicDBObject item = (BasicDBObject) element.get(Constants.ID);
                    if (item == null) {
                        return;
                    }
                    ApiInfoKey apiInfoKey = new ApiInfoKey(
                            task.getApiCollectionId(),
                            item.getString(ApiInfoKey.URL),
                            Method.fromString(item.getString(ApiInfoKey.METHOD)));
                    endpoints.add(apiInfoKey);
                });
            }

        } else {
            endpoints.addAll(task.getApiInfoKeys());
        }

        return endpoints;
    }

    public void run() {
        Context.accountId.set(1_000_000);

        Bson pendingTasks = Filters.lt(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, Context.now());
        for(AccessMatrixTaskInfo task: AccessMatrixTaskInfosDao.instance.findAll(pendingTasks)) {
            loggerMaker.infoAndAddToDb("Running task: " + task.toString(),LogDb.TESTING);

            List<ApiInfoKey> endpoints = getEndpointsToAnalyze(task);
            loggerMaker.infoAndAddToDb("Number of endpoints: " + (endpoints == null ? 0 : endpoints.size()),LogDb.TESTING);

            TestingEndpoints testingEndpoints = new CollectionWiseTestingEndpoints(task.getApiCollectionId());
            AuthMechanism authMechanism = TestExecutor.createAuthMechanism();
            TestingUtil testingUtil = TestExecutor.createTestingUtil(testingEndpoints, authMechanism);

            BFLATest bflaTest = new BFLATest();

            if(endpoints!=null && !endpoints.isEmpty()){
                for(ApiInfoKey endpoint: endpoints){
                    List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(endpoint, testingUtil.getSampleMessages());
                    if (messages!=null){
                        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, testingUtil.getAuthMechanism());
                        if (filteredMessages!=null){
                            for (RawApi rawApi: filteredMessages) {
                                bflaTest.updateAllowedRoles(rawApi, endpoint, testingUtil);
                            }
                        }
                    }
                }
            }
        }
    }
}
