package com.akto.utils.crons;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;

import com.akto.action.observe.InventoryAction;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ActivitiesDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.AccountSettings;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.akto.utils.RiskScoreOfCollections;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.akto.task.Cluster.callDibs;

public class SyncCron {
    private static final LoggerMaker loggerMaker = new LoggerMaker(SyncCron.class, LogDb.DASHBOARD);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpUpdateCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {

                Context.accountId.set(1000_000);
                boolean dibs = callDibs(Cluster.SYNC_CRON_INFO, 300, 60);
                if(!dibs){
                    loggerMaker.debugAndAddToDb("Cron for updating new parameters, new endpoints and severity score dibs not acquired, thus skipping cron", LogDb.DASHBOARD);
                    return;
                }
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                        loggerMaker.debugAndAddToDb("Cron for updating new parameters, new endpoints and severity score picked up " + accountSettings.getId(), LogDb.DASHBOARD);
                        try {
                            int endTs = Context.now();
                            int startTs = endTs - 600 ;
                            int startTsSeverity = 0;
                            int resetTs = 0;
                            if(lastRunTimerInfo != null){
                                startTs = lastRunTimerInfo.getLastSyncedCron();
                                startTsSeverity = lastRunTimerInfo.getLastUpdatedSeverity();
                                resetTs = lastRunTimerInfo.getLastInfoResetted();
                            }
                            
                            // synced new parameters from STI and then inserted into activities
                            Bson filter = Filters.and(Filters.gte(SingleTypeInfo._TIMESTAMP, startTs),Filters.lte(SingleTypeInfo._TIMESTAMP, endTs));
                            long newParams = SingleTypeInfoDao.instance.getMCollection().countDocuments(filter);
                            
                            if(newParams > 0){
                                ActivitiesDao.instance.insertActivity("Parameters detected",newParams + " new parameters detected");
                            }

                             // synced new endpoints from APIinfo and then inserted into activities
                            long newEndpoints  = new InventoryAction().fetchRecentEndpoints(startTs,endTs).size();
                            if(newEndpoints > 0){
                                ActivitiesDao.instance.insertActivity("Endpoints detected",newEndpoints + " new endpoints detected");
                            }

                            // updated {Severity score field in APIinfo}
                            RiskScoreOfCollections updateRiskScore = new RiskScoreOfCollections();

                            Bson update = Updates.combine(
                                Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_SYNCED_CRON), endTs),
                                Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_UPDATED_SEVERITY), endTs)
                            );

                            // invoke reset once everyday
                            int resetTime = 24 * 60 * 60;

                            if((endTs - resetTs) >= resetTime){
                                // if reset is called, calculate riskScore for each api whose last updated is here while resetting isSensitive false and severityScore 0
                                updateRiskScore.calculateRiskScoreForAllApis();

                                // update account settings docs
                                update = Updates.combine(update,  
                                        Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_INFO_RESETTED), endTs)
                                    );
                            }else{
                                updateRiskScore.updateSeverityScoreInApiInfo(startTsSeverity);
                            }

                            // Update malicious-mcp-server tags based on HIGH severity OPEN issues
                            updateMaliciousMcpServerTags();

                            AccountSettingsDao.instance.getMCollection().updateOne(
                                AccountSettingsDao.generateFilter(),
                                update,
                                new UpdateOptions().upsert(true)
                            );
                        } catch (Exception e) {
                           e.printStackTrace();
                        }
                    }
                }, "sync-cron-info");
            }
        }, 0, 5, TimeUnit.MINUTES);
    }

    private void updateMaliciousMcpServerTags() {
        try {
            // Get all MCP collections
            List<ApiCollection> allCollections = ApiCollectionsDao.instance.findAll(
                Filters.elemMatch(ApiCollection.TAGS_STRING, 
                    Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_MCP_SERVER_TAG))
            );

            if (allCollections == null || allCollections.isEmpty()) {
                return;
            }

            // Get all collection IDs that are MCP collections
            Set<Integer> mcpCollectionIds = new HashSet<>();
            for (ApiCollection collection : allCollections) {
                if (collection.isMcpCollection()) {
                    mcpCollectionIds.add(collection.getId());
                }
            }

            if (mcpCollectionIds.isEmpty()) {
                return;
            }

            // Find MCP collections with OPEN HIGH severity issues
            List<Bson> pipeline = new ArrayList<>();
            pipeline.add(Aggregates.match(Filters.and(
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN),
                Filters.eq(TestingRunIssues.KEY_SEVERITY, Severity.HIGH),
                Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, mcpCollectionIds)
            )));
            
            // Group by collection ID to get unique collection IDs with HIGH severity issues
            BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$" + TestingRunIssues.ID_API_COLLECTION_ID);
            pipeline.add(Aggregates.group(groupedId));

            Set<Integer> collectionsWithHighSeverityIssues = new HashSet<>();
            TestingRunIssuesDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .forEach(doc -> {
                    BasicDBObject id = (BasicDBObject) doc.get("_id");
                    if (id != null) {
                        int collectionId = id.getInt("apiCollectionId");
                        collectionsWithHighSeverityIssues.add(collectionId);
                    }
                });

            // Update tags for all MCP collections
            for (Integer collectionId : mcpCollectionIds) {
                try {
                    Bson filter = Filters.eq(ApiCollection.ID, collectionId);
                    BasicDBObject pullQuery = new BasicDBObject(CollectionTags.KEY_NAME, Constants.AKTO_MALICIOUS_MCP_SERVER_TAG);
                    
                    if (collectionsWithHighSeverityIssues.contains(collectionId)) {
                        // Add tag if collection has HIGH severity OPEN issues
                        CollectionTags maliciousTag = new CollectionTags(
                            Context.now(),
                            Constants.AKTO_MALICIOUS_MCP_SERVER_TAG,
                            "true",
                            CollectionTags.TagSource.USER
                        );
                        
                        Bson tagUpdate = Updates.combine(
                            Updates.pull(ApiCollection.TAGS_STRING, pullQuery),
                            Updates.addToSet(ApiCollection.TAGS_STRING, maliciousTag)
                        );
                        
                        ApiCollectionsDao.instance.updateOne(filter, tagUpdate);
                        loggerMaker.debugAndAddToDb(String.format("Added malicious-mcp-server tag to collection %d", collectionId), LogDb.DASHBOARD);
                    } else {
                        // Remove tag if collection has no HIGH severity OPEN issues
                        Bson tagUpdate = Updates.pull(ApiCollection.TAGS_STRING, pullQuery);
                        ApiCollectionsDao.instance.updateOne(filter, tagUpdate);
                        loggerMaker.debugAndAddToDb(String.format("Removed malicious-mcp-server tag from collection %d", collectionId), LogDb.DASHBOARD);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(String.format("Error updating malicious-mcp-server tag for collection %d: %s", collectionId, e.getMessage()), LogDb.DASHBOARD);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error in updateMaliciousMcpServerTags: " + e.getMessage(), LogDb.DASHBOARD);
        }
    }
}
