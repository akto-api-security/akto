package com.akto.threat.backend.cron;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.bson.conversions.Bson;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.billing.FeatureAccess;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import static com.akto.jobs.utils.Utils.getRiskScoreValueFromSeverityScore;
import static com.akto.billing.UsageMetricUtils.getFeatureAccessSaas;

public class RiskScoreSyncCron {
    
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreSyncCron.class, LogDb.THREAT_DETECTION);

    public void setUpRiskScoreSyncCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        int accountId = t.getId();
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        FeatureAccess featureAccess = getFeatureAccessSaas(accountId, "THREAT_DETECTION");
                        if(!featureAccess.getIsGranted()){
                            loggerMaker.debugAndAddToDb("Feature access not granted for account " + accountId);
                            return;
                        }
                        int startTimestamp = Context.now();
                        loggerMaker.debugAndAddToDb("Risk score sync cron started for account " + accountId + " at " + startTimestamp);
                        LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                        int deltaStarTime = 0;
                        int deltaEndTime = Context.now();
                        Bson updateForLastCronRunInfo = Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_THREAT_SCORE_SYNC), deltaEndTime);
                        if(lastRunTimerInfo != null){
                            if(deltaEndTime - lastRunTimerInfo.getLastInfoResetted() <= Constants.ONE_DAY_TIMESTAMP){
                                deltaStarTime = lastRunTimerInfo.getLastThreatScoreSync();
                            }else{
                                updateForLastCronRunInfo = Updates.combine(updateForLastCronRunInfo, Updates.set((AccountSettings.LAST_UPDATED_CRON_INFO + "."+ LastCronRunInfo.LAST_INFO_RESETTED), deltaEndTime));
                            }
                        }

                        float threatScore = 1;

                        List<ApiCollection> apiCollections = ApiCollectionsDao.fetchAllHosts();
                        Set<Integer> apiCollectionIds = apiCollections.stream().map(ApiCollection::getId).collect(Collectors.toSet());

                        // get successful exploits from malicious events in this time range, grouping by host, method, endpoint
                        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$latestApiCollectionId").append("method", "$latestApiMethod").append("endpoint", "$latestApiEndpoint");
                        List<Bson> pipeline = new ArrayList<>();
                        pipeline.add(Aggregates.sort(Sorts.descending("detectedAt")));
                        pipeline.add(Aggregates.match(Filters.and(Filters.gte("detectedAt", deltaStarTime), Filters.lte("detectedAt", deltaEndTime), Filters.in("latestApiCollectionId", apiCollectionIds), Filters.eq("successfulExploit", true))));
                        pipeline.add(Aggregates.group(groupedId, Accumulators.sum("count", 1)));
                        MongoCursor<BasicDBObject> cursor = MaliciousEventDao.instance.getCollection(String.valueOf(accountId)).aggregate(pipeline, BasicDBObject.class).cursor();
                        List<Bson> filters = new ArrayList<>();
                        while(cursor.hasNext()){
                            BasicDBObject document = cursor.next();
                            BasicDBObject id = (BasicDBObject) document.get("_id");
                            int apiCollectionIdFromDoc = id.getInt("apiCollectionId");
                            String method = id.getString("method");
                            String endpoint = id.getString("endpoint");
                            if(apiCollectionIds.contains(apiCollectionIdFromDoc)){
                                filters.add(ApiInfoDao.getFilter(endpoint, method, apiCollectionIdFromDoc));
                            }
                        }

                        List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.or(filters));
                        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
                        for(ApiInfo apiInfo: apiInfos){
                            float riskScoreFromSeverityScore = getRiskScoreValueFromSeverityScore(apiInfo.getSeverityScore());
                            Float riskScore = ApiInfoDao.getRiskScore(apiInfo, apiInfo.getIsSensitive(), riskScoreFromSeverityScore, true);
                            updates.add(new UpdateManyModel<>(ApiInfoDao.getFilter(apiInfo.getId()), Updates.combine(Updates.set(ApiInfo.RISK_SCORE, riskScore), Updates.set(ApiInfo.THREAT_SCORE, threatScore)), new UpdateOptions().upsert(false)));
                        }
                        if(updates.size() > 0){
                            loggerMaker.warnAndAddToDb("Updating risk score for " + updates.size() + " api infos");
                            ApiInfoDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
                        }
                        AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), updateForLastCronRunInfo);
                        loggerMaker.warnAndAddToDb("Risk score sync cron completed for account " + accountId + " in " + (Context.now() - startTimestamp) + "seconds");
                    }
                }, "risk-score-sync-cron");
            }
        }, 0, 5, TimeUnit.MINUTES);
    }
}
