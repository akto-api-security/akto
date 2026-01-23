package com.akto.utils.jobs;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.APICatalogSync;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import static com.akto.utils.Utils.deleteApis;

public class JobUtils {

    private static final LoggerMaker logger = new LoggerMaker(JobUtils.class, LogDb.DASHBOARD);
    private static final int limit = 500;
    private static final Bson sort = Sorts.ascending(ApiInfo.ID_API_COLLECTION_ID, ApiInfo.ID_URL, ApiInfo.ID_METHOD);

    public static final int JOB_MODE_NONE = 0;
    public static final int JOB_MODE_CATEGORY_1 = 1;
    public static final int JOB_MODE_CATEGORY_2 = 2;

    public static Set<Integer> getJobModes() {
        try {
            String envValue = System.getenv().getOrDefault("AKTO_RUN_JOB", "0");

            if ("true".equalsIgnoreCase(envValue)) {
                Set<Integer> allModes = new HashSet<>();
                allModes.add(JOB_MODE_CATEGORY_1);
                allModes.add(JOB_MODE_CATEGORY_2);
                return allModes;
            }
            if ("false".equalsIgnoreCase(envValue)) {
                return Collections.emptySet();
            }

            Set<Integer> modes = new HashSet<>();
            for (String part : envValue.split(",")) {
                try {
                    int mode = Integer.parseInt(part.trim());
                    if (mode == JOB_MODE_CATEGORY_1 || mode == JOB_MODE_CATEGORY_2) {
                        modes.add(mode);
                    }
                } catch (NumberFormatException e) {
                    logger.error("Invalid job mode value: " + part);
                }
            }
            return modes;
        } catch (Exception e) {
            logger.error("Error parsing AKTO_RUN_JOB: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    public static boolean shouldRunMode(int mode) {
        return getJobModes().contains(mode) || getRunJobFunctionsAnyway();
    }

    public static boolean shouldRunCategory1Jobs() {
        return shouldRunMode(JOB_MODE_CATEGORY_1);
    }

    public static boolean shouldRunCategory2Jobs() {
        return shouldRunMode(JOB_MODE_CATEGORY_2);
    }

    public static boolean shouldRunAnyJobs() {
        return !getJobModes().isEmpty() || getRunJobFunctionsAnyway();
    }

    public static String getJobModeDescription() {
        Set<Integer> modes = getJobModes();
        if (modes.isEmpty() && !getRunJobFunctionsAnyway()) return "NONE (0)";
        if (getRunJobFunctionsAnyway()) return "ALL (on-prem/non-SaaS override)";

        List<String> descriptions = new ArrayList<>();
        if (modes.contains(JOB_MODE_CATEGORY_1)) descriptions.add("CATEGORY_1");
        if (modes.contains(JOB_MODE_CATEGORY_2)) descriptions.add("CATEGORY_2");
        return String.join("+", descriptions) + " (" + modes + ")";
    }

    @Deprecated
    public static boolean getRunJobFunctions() {
        return shouldRunAnyJobs();
    }

    public static boolean getRunJobFunctionsAnyway() {
        try {
            return DashboardMode.isOnPremDeployment() || !DashboardMode.isSaasDeployment();
        } catch (Exception e) {
            return true;
        }
    }

    public static void removeVersionedAPIs(){
        // here for all hosts collections, we are removing the versioned APIs
        logger.info("Starting to remove versioned APIs");
        List<ApiCollection> apiCollections = ApiCollectionsDao.fetchAllActiveHosts();
        List<SampleData> sampleDataList = new ArrayList<>();
        logger.info("Total APICollections: " + apiCollections.size()); 
        List<Key> toMove = new ArrayList<>();
        int totalCount = 0;
        for (ApiCollection apiCollection: apiCollections) {
            int skip = 0;
            while(true){
                sampleDataList = SampleDataDao.instance.findAll(Filters.eq(ApiInfo.ID_API_COLLECTION_ID, apiCollection.getId()), skip, limit, sort, Projections.include(Constants.ID));
            
                if(sampleDataList == null || sampleDataList.isEmpty()) {
                   break;
                }

                for(SampleData sampleData: sampleDataList) {
                    if(!sampleData.getId().getUrl().startsWith("/") || APICatalogSync.VERSION_PATTERN.matcher((sampleData.getId().getUrl())).find()){
                        toMove.add(sampleData.getId());
                    }
                }



                if(toMove.size() > 1000){
                    logger.info("starting moving APIs");
                    CleanInventory.moveApisFromSampleData(toMove, true);
                    try {
                        Thread.sleep(1000);
                        totalCount += toMove.size();
                        logger.infoAndAddToDb("Total APIs moved till now: " + totalCount + " at: " + Context.now());
                        deleteApis(toMove);
                    } catch (InterruptedException e) {
                        logger.errorAndAddToDb("Error during moving APIs: " + e.getMessage());
                        e.printStackTrace();
                    }
                    toMove.clear();
                }
                skip += limit;
            }
            
        }

        if(!toMove.isEmpty()){
            logger.info("starting moving APIs");
            CleanInventory.moveApisFromSampleData(toMove, true);
            try {
                Thread.sleep(1000);
                totalCount += toMove.size();
                logger.infoAndAddToDb("Total APIs moved till now: " + totalCount + " at: " + Context.now());
                deleteApis(toMove);
            } catch (InterruptedException e) {
                logger.errorAndAddToDb("Error during moving APIs: " + e.getMessage());
                e.printStackTrace();
            }
            toMove.clear();
        }

    }
}