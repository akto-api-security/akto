package com.akto.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.BFLATest;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.test_editor.execution.Executor;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

public class AccessMatrixAnalyzer {
    private static final LoggerMaker loggerMaker = new LoggerMaker(AccessMatrixAnalyzer.class);
    private static final int LIMIT = 2000;

    private static final int DELTA_PERIOD_VALUE = 60 * 24 * 60 * 60;
    public List<ApiInfoKey> getEndpointsToAnalyze(AccessMatrixTaskInfo task) {
        EndpointLogicalGroup endpointLogicalGroup =
                EndpointLogicalGroupDao.instance.findOne(EndpointLogicalGroup.GROUP_NAME, task.getEndpointLogicalGroupName());

        if (endpointLogicalGroup == null) return new ArrayList<>();
        List<ApiInfoKey> ret = new ArrayList<>();
        for(ApiCollection apiCollection: ApiCollectionsDao.instance.getMetaAll()) {
            int lastBatchSize = 0;
            int skip = 0;
            boolean isAutomatedTrafficCollection = apiCollection != null && !StringUtils.isEmpty(apiCollection.getHostName());
            do {
                List<BasicDBObject> apiBatch = new ArrayList<>();
                if(isAutomatedTrafficCollection){
                    apiBatch = ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), skip, false);
                }else{
                    apiBatch = ApiCollectionsDao.fetchEndpointsInCollection(apiCollection.getId(), skip, LIMIT, DELTA_PERIOD_VALUE);
                }
                skip += LIMIT;
                lastBatchSize = apiBatch.size();
                if (!apiBatch.isEmpty()) {
                    apiBatch.forEach(element -> {
                        BasicDBObject item = (BasicDBObject) element.get(Constants.ID);
                        if (item == null) {
                            return;
                        }
                        ApiInfoKey apiInfoKey = new ApiInfoKey(
                                item.getInt(ApiInfoKey.API_COLLECTION_ID),
                                item.getString(ApiInfoKey.URL),
                                Method.fromString(item.getString(ApiInfoKey.METHOD)));

                        if (endpointLogicalGroup.getTestingEndpoints().containsApi(apiInfoKey)) {
                            ret.add(apiInfoKey);
                        }
                    });
                }

            } while (lastBatchSize == LIMIT);

        }

        return ret;
    }

    public void run() throws Exception {
        Bson pendingTasks = Filters.lt(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, Context.now());
        for(AccessMatrixTaskInfo task: AccessMatrixTaskInfosDao.instance.findAll(pendingTasks)) {
            loggerMaker.debugAndAddToDb("Running task: " + task.toString(),LogDb.TESTING);

            List<ApiInfoKey> endpoints = getEndpointsToAnalyze(task);
            loggerMaker.debugAndAddToDb("Number of endpoints: " + (endpoints == null ? 0 : endpoints.size()),LogDb.TESTING);
            SampleMessageStore sampleMessageStore = SampleMessageStore.create();
            CustomTestingEndpoints tempTestingEndpoints = new CustomTestingEndpoints(endpoints);


            List<ApiInfo.ApiInfoKey> apiInfoKeyList = tempTestingEndpoints.returnApis();
            if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
            loggerMaker.debugAndAddToDb("APIs found: " + apiInfoKeyList.size(), LogDb.TESTING);


            Set<Integer> apiCollectionIds = Main.extractApiCollectionIds(apiInfoKeyList);
            sampleMessageStore.fetchSampleMessages(apiCollectionIds);
            String roleFromTask = task.getEndpointLogicalGroupName().substring(0, task.getEndpointLogicalGroupName().length()-EndpointLogicalGroup.GROUP_NAME_SUFFIX.length());
            loggerMaker.debugAndAddToDb("Role found: " + roleFromTask, LogDb.TESTING);
            List<TestRoles> testRoles = new ArrayList<>();
            TestRoles testRoleForTask = Executor.fetchOrFindTestRole(roleFromTask, false);
            if (testRoleForTask != null) {
                testRoles.add(testRoleForTask);
            }

            List<CustomAuthType> customAuthTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
            TestingUtil testingUtil = new TestingUtil(sampleMessageStore, testRoles,"", customAuthTypes);

            BFLATest bflaTest = new BFLATest();

            if(endpoints!=null && !endpoints.isEmpty()){
                for(ApiInfoKey endpoint: endpoints){
                    loggerMaker.debugAndAddToDb("Started checking for " + task.getId() + " " + endpoint.getMethod() + " " + endpoint.getUrl(), LogDb.TESTING);

                    List<RawApi> messages = sampleMessageStore.fetchAllOriginalMessages(endpoint);
                    if (messages!=null){

                        for (RawApi rawApi: messages) {
                            bflaTest.updateAllowedRoles(rawApi, endpoint, testingUtil);
                        }
                    }
                    loggerMaker.debugAndAddToDb("Finished checking for " + task.getId() + " " + endpoint.getMethod() + " " + endpoint.getUrl(), LogDb.TESTING);
                }
            }
            Bson q = Filters.eq(Constants.ID, task.getId());
            Bson update = Updates.combine(
                Updates.set(AccessMatrixTaskInfo.LAST_COMPLETED_TIMESTAMP,Context.now()),
                Updates.set(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, Context.now() + task.getFrequencyInSeconds())
            );
            AccessMatrixTaskInfosDao.instance.updateOne(q, update);
            loggerMaker.debugAndAddToDb("Matrix analyzer task " + task.getId() + "  completed successfully", LogDb.TESTING);
        }
    }
}
