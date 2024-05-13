package com.akto.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.dao.context.Context;
import com.akto.dto.ApiCollectionUsers;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.RiskScoreTestingEndpoints;
import com.akto.dto.testing.TestingEndpoints;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

public class RiskScoreTestingEndpointsUtils {
    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreTestingEndpointsUtils.class);

    private Map<RiskScoreTestingEndpoints.RiskScoreGroupType, List<ApiInfo>> removeApisFromRiskScoreGroupMap = new HashMap<RiskScoreTestingEndpoints.RiskScoreGroupType, List<ApiInfo>>() {{
        put(RiskScoreTestingEndpoints.RiskScoreGroupType.LOW, new ArrayList<>());
        put(RiskScoreTestingEndpoints.RiskScoreGroupType.MEDIUM, new ArrayList<>());
        put(RiskScoreTestingEndpoints.RiskScoreGroupType.HIGH, new ArrayList<>());
    }}; 

    
    private Map<RiskScoreTestingEndpoints.RiskScoreGroupType, List<ApiInfo>> addApisToRiskScoreGroupMap = new HashMap<RiskScoreTestingEndpoints.RiskScoreGroupType, List<ApiInfo>>() {{
        put(RiskScoreTestingEndpoints.RiskScoreGroupType.LOW, new ArrayList<>());
        put(RiskScoreTestingEndpoints.RiskScoreGroupType.MEDIUM, new ArrayList<>());
        put(RiskScoreTestingEndpoints.RiskScoreGroupType.HIGH, new ArrayList<>());
    }}; 
    
    private static final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public RiskScoreTestingEndpointsUtils() {
    }

    public void updateApiRiskScoreGroup(ApiInfo apiInfo, float updatedRiskScore) {
       float oldRiskScore = apiInfo.getRiskScore();

       RiskScoreTestingEndpoints.RiskScoreGroupType removeRiskScoreGroupType = RiskScoreTestingEndpoints.calculateRiskScoreGroup(oldRiskScore);
       removeApisFromRiskScoreGroupMap.get(removeRiskScoreGroupType).add(apiInfo);

       RiskScoreTestingEndpoints.RiskScoreGroupType addRiskScoreGroupType = RiskScoreTestingEndpoints.calculateRiskScoreGroup(updatedRiskScore);
       addApisToRiskScoreGroupMap.get(addRiskScoreGroupType).add(apiInfo);
    }

    private void updateRiskScoreApiGroups() {
        try {
            for(RiskScoreTestingEndpoints.RiskScoreGroupType riskScoreGroupType: RiskScoreTestingEndpoints.RiskScoreGroupType.values()) {
                RiskScoreTestingEndpoints riskScoreTestingEndpoints = new RiskScoreTestingEndpoints(riskScoreGroupType);
                
                List<TestingEndpoints> testingEndpoints = new ArrayList<>();
                testingEndpoints.add(riskScoreTestingEndpoints);
                int apiCollectionId = RiskScoreTestingEndpoints.getApiCollectionId(riskScoreGroupType);
    
                // Remove APIs from the original risk score group
                List<ApiInfo> removeApisFromRiskScoreGroupList = removeApisFromRiskScoreGroupMap.get(riskScoreGroupType);
                for (int start = 0; start < removeApisFromRiskScoreGroupList.size(); start += RiskScoreTestingEndpoints.BATCH_SIZE) {
                    int end = Math.min(start + RiskScoreTestingEndpoints.BATCH_SIZE, removeApisFromRiskScoreGroupList.size());
    
                    List<ApiInfo> batch = removeApisFromRiskScoreGroupList.subList(start, end);
    
                    riskScoreTestingEndpoints.setFilterRiskScoreGroupApis(batch);
                    ApiCollectionUsers.removeFromCollectionsForCollectionId(testingEndpoints, apiCollectionId, true);
                }
    
                // Add APIs to the new risk score group
                List<ApiInfo> addApisToRiskScoreGroupList = addApisToRiskScoreGroupMap.get(riskScoreGroupType);
                for (int start = 0; start < addApisToRiskScoreGroupList.size(); start += RiskScoreTestingEndpoints.BATCH_SIZE) {
                    int end = Math.min(start + RiskScoreTestingEndpoints.BATCH_SIZE, addApisToRiskScoreGroupList.size());
    
                    List<ApiInfo> batch = addApisToRiskScoreGroupList.subList(start, end);
    
                    riskScoreTestingEndpoints.setFilterRiskScoreGroupApis(batch);
                    ApiCollectionUsers.addToCollectionsForCollectionId(testingEndpoints, apiCollectionId);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating risk score group APIs - " + e.getMessage(), LogDb.DASHBOARD);
        }
    }

    public void syncRiskScoreGroupApis() {
        int accountId = Context.accountId.get();
        executorService.schedule( new Runnable() {
            public void run() {
                Context.accountId.set(accountId);
                updateRiskScoreApiGroups();
            }
        }, 0, TimeUnit.SECONDS);
    }
}