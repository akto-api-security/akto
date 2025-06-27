package com.akto.test_editor.strategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.YamlTestResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.Utils;

public class Strategy {
    
    private static final String AKTO_DISCOVERED_APIS_COLLECTION = "shadow_apis";
    private static final int AKTO_DISCOVERED_APIS_COLLECTION_ID = 1333333333;
    private static final LoggerMaker loggerMaker = new LoggerMaker(Strategy.class, LogDb.TESTING);

    public static void triggerStrategyInstructions(com.akto.dto.test_editor.Strategy strategy, YamlTestResult attempts) {
        
        if (strategy == null || !strategy.getInsertVulnApi()) {
            return;
        }

        String harPayload = null;

        try {
            for (GenericTestResult testRes: attempts.getTestResults()) {
                if (!testRes.getVulnerable()) {
                    continue;
                }
                if (testRes instanceof TestResult) {
                    TestResult res = (TestResult) testRes;
                    String message = res.getMessage();
                    try {
                        harPayload = Utils.convertToHarPayload(message, Context.accountId.get(), Context.now(), "", "HAR");
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb("Error while converting attempt to har payload " + e.getMessage());
                    }
                } else {
                    return;
                }
    
            }
            
            if (harPayload == null) {
                return;
            }
    
            List<String> result = new ArrayList<>();
            result.add(harPayload);
    
            ApiCollection sameNameCollection = ApiCollectionsDao.instance.findByName(AKTO_DISCOVERED_APIS_COLLECTION);
            if (sameNameCollection == null){
                ApiCollection apiCollection = new ApiCollection(AKTO_DISCOVERED_APIS_COLLECTION_ID, AKTO_DISCOVERED_APIS_COLLECTION, Context.now(),new HashSet<>(), null, AKTO_DISCOVERED_APIS_COLLECTION_ID, false, true);
                ApiCollectionsDao.instance.insertOne(apiCollection);
            }

            InsertDataUtil.insertDataInApiCollection(AKTO_DISCOVERED_APIS_COLLECTION_ID, harPayload, result, result);    
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in triggerMetaInstructions " + e.getMessage());
        }

    }

}
