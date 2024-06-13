package com.akto.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
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
import com.akto.store.AuthMechanismStore;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;

public class AccessMatrixAnalyzer {
    private static final LoggerMaker loggerMaker = new LoggerMaker(AccessMatrixAnalyzer.class);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();
    private static final int LIMIT = 2000;

    private static final int DELTA_PERIOD_VALUE = 60 * 24 * 60 * 60;
    public List<ApiInfoKey> getEndpointsToAnalyze(AccessMatrixTaskInfo task) {
        EndpointLogicalGroup endpointLogicalGroup = dataActor.fetchEndpointLogicalGroup(task.getEndpointLogicalGroupName());

        if (endpointLogicalGroup == null) return new ArrayList<>();
        List<ApiInfoKey> ret = new ArrayList<>();
        List<ApiCollection> apiCollections = dataActor.fetchAllApiCollectionsMeta();
        for(ApiCollection apiCollection: apiCollections) {
            int lastBatchSize = 0;
            int skip = 0;
            do {
                List<BasicDBObject> apiBatch =
                        ApiCollectionsDao.fetchEndpointsInCollectionUsingHost(apiCollection.getId(), skip, LIMIT, DELTA_PERIOD_VALUE);
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
        List<AccessMatrixTaskInfo> accessMatrixTaskInfos = dataActor.fetchPendingAccessMatrixInfo(Context.now());
        for(AccessMatrixTaskInfo task: accessMatrixTaskInfos) {
            loggerMaker.infoAndAddToDb("Running task: " + task.toString(),LogDb.TESTING);

            List<ApiInfoKey> endpoints = getEndpointsToAnalyze(task);
            loggerMaker.infoAndAddToDb("Number of endpoints: " + (endpoints == null ? 0 : endpoints.size()),LogDb.TESTING);
            SampleMessageStore sampleMessageStore = SampleMessageStore.create();
            CustomTestingEndpoints tempTestingEndpoints = new CustomTestingEndpoints(endpoints);        

            List<ApiInfo.ApiInfoKey> apiInfoKeyList = tempTestingEndpoints.returnApis();
            if (apiInfoKeyList == null || apiInfoKeyList.isEmpty()) return;
            loggerMaker.infoAndAddToDb("APIs found: " + apiInfoKeyList.size(), LogDb.TESTING);


            Set<Integer> apiCollectionIds = Main.extractApiCollectionIds(apiInfoKeyList);
            sampleMessageStore.fetchSampleMessages(apiCollectionIds);
            String roleFromTask = task.getEndpointLogicalGroupName().substring(0, task.getEndpointLogicalGroupName().length()-EndpointLogicalGroup.GROUP_NAME_SUFFIX.length());
            loggerMaker.infoAndAddToDb("Role found: " + roleFromTask, LogDb.TESTING);
            List<TestRoles> testRoles = dataActor.fetchTestRolesForRoleName(roleFromTask);

            AuthMechanismStore authMechanismStore = AuthMechanismStore.create();
            AuthMechanism authMechanism = authMechanismStore.getAuthMechanism();
            List<CustomAuthType> customAuthTypes = dataActor.fetchCustomAuthTypes();
            TestingUtil testingUtil = new TestingUtil(authMechanism,sampleMessageStore, testRoles,"", customAuthTypes);

            BFLATest bflaTest = new BFLATest();

            if(endpoints!=null && !endpoints.isEmpty()){
                for(ApiInfoKey endpoint: endpoints){
                    loggerMaker.infoAndAddToDb("Started checking for " + task.getId() + " " + endpoint.getMethod() + " " + endpoint.getUrl(), LogDb.TESTING);

                    List<RawApi> messages = sampleMessageStore.fetchAllOriginalMessages(endpoint);
                    if (messages!=null){

                        for (RawApi rawApi: messages) {
                            bflaTest.updateAllowedRoles(rawApi, endpoint, testingUtil);
                        }
                    }
                    loggerMaker.infoAndAddToDb("Finished checking for " + task.getId() + " " + endpoint.getMethod() + " " + endpoint.getUrl(), LogDb.TESTING);
                }
            }
            dataActor.updateAccessMatrixInfo(task.getId().toHexString(), task.getFrequencyInSeconds());
            loggerMaker.infoAndAddToDb("Matrix analyzer task " + task.getId() + "  completed successfully", LogDb.TESTING);
        }
    }
}
