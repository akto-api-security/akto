package com.akto.testing;

import java.util.ArrayList;
import java.util.List;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AccessMatrixTaskInfosDao;
import com.akto.dao.testing.EndpointLogicalGroupDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.RawApi;
import com.akto.dto.testing.*;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.rules.BFLATest;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import org.bson.conversions.Bson;

public class AccessMatrixAnalyzer {
    private static final LoggerMaker loggerMaker = new LoggerMaker(AccessMatrixAnalyzer.class);

    public List<ApiInfoKey> getEndpointsToAnalyze(AccessMatrixTaskInfo task) {
        EndpointLogicalGroup endpointLogicalGroup =
                EndpointLogicalGroupDao.instance.findOne(EndpointLogicalGroup.GROUP_NAME, task.getEndpointLogicalGroupName());

        if (endpointLogicalGroup == null) return new ArrayList<>();

        return endpointLogicalGroup.getTestingEndpoints().returnApis();
    }

    public void run() {
        Context.accountId.set(1_000_000);

        Bson pendingTasks = Filters.lt(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, Context.now());
        for(AccessMatrixTaskInfo task: AccessMatrixTaskInfosDao.instance.findAll(pendingTasks)) {
            loggerMaker.infoAndAddToDb("Running task: " + task.toString(),LogDb.TESTING);

            List<ApiInfoKey> endpoints = getEndpointsToAnalyze(task);
            loggerMaker.infoAndAddToDb("Number of endpoints: " + (endpoints == null ? 0 : endpoints.size()),LogDb.TESTING);

            TestingEndpoints testingEndpoints = new CustomTestingEndpoints(endpoints);
            AuthMechanism authMechanism = TestExecutor.createAuthMechanism();
            SampleMessageStore sampleMessageStore = SampleMessageStore.create();
            TestingUtil testingUtil = TestExecutor.createTestingUtil(authMechanism, sampleMessageStore, "system@akto.io");

            BFLATest bflaTest = new BFLATest();

            if(endpoints!=null && !endpoints.isEmpty()){
                for(ApiInfoKey endpoint: endpoints){
                    List<RawApi> messages = testingUtil.getSampleMessageStore().fetchAllOriginalMessages(endpoint);
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
            Bson q = Filters.eq(Constants.ID, task.getId());
            Bson update = Updates.combine(
                Updates.set(AccessMatrixTaskInfo.LAST_COMPLETED_TIMESTAMP,Context.now()),
                Updates.set(AccessMatrixTaskInfo.NEXT_SCHEDULED_TIMESTAMP, Context.now() + task.getFrequencyInSeconds())
            );
            AccessMatrixTaskInfosDao.instance.updateOne(q, update);
        }
    }
}
