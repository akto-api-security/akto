package com.akto.threat.backend.cron;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.ahocorasick.trie.Trie;
import org.bson.conversions.Bson;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.RuntimeUtil;
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
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import static com.akto.billing.UsageMetricUtils.getFeatureAccessSaas;
import static com.akto.threat_utils.Utils.generateTrie;
import static com.akto.threat_utils.Utils.isMatchingUrl;

public class RiskScoreSyncCron {
    
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(RiskScoreSyncCron.class, LogDb.THREAT_DETECTION);
    
    private static Trie lfiTrie;
    private static Trie osCommandInjectionTrie;
    private static Trie ssrfTrie;

    static {
        try {
            lfiTrie = generateTrie(Constants.LFI_OS_FILES_DATA);
            osCommandInjectionTrie = generateTrie(Constants.OS_COMMAND_INJECTION_DATA);
            ssrfTrie = generateTrie(Constants.SSRF_DATA);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error initializing static tries: " + e.getMessage());
        }
    }


    public void setUpRiskScoreSyncCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        int accountId = t.getId();
                        AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                        FeatureAccess featureAccess = getFeatureAccessSaas(accountId, "THREAT_DETECTION");
                        // if(!featureAccess.getIsGranted()){
                        //     loggerMaker.debugAndAddToDb("Feature access not granted for account " + accountId);
                        //     return;
                        // }
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

                        BasicDBObject groupedId = new BasicDBObject("apiCollectionId", "$latestApiCollectionId").append("method", "$latestApiMethod").append("endpoint", "$latestApiEndpoint");
                        List<Bson> pipeline = new ArrayList<>();
                        pipeline.add(Aggregates.sort(Sorts.descending("detectedAt")));
                        pipeline.add(Aggregates.match(Filters.and(Filters.gte("detectedAt", deltaStarTime), Filters.lte("detectedAt", deltaEndTime), Filters.eq("successfulExploit", true))));
                        pipeline.add(Aggregates.group(groupedId, Accumulators.addToSet("severities", "$severity")));
                        MongoCursor<BasicDBObject> cursor = MaliciousEventDao.instance.getCollection(String.valueOf(accountId)).aggregate(pipeline, BasicDBObject.class).cursor();

                        Map<String, List<String>> apiInfoKeyToSeverities = new HashMap<>();
                        Set<Integer> apiCollectionIdsFromEvents = new HashSet<>();
                        while(cursor.hasNext()){
                            BasicDBObject document = cursor.next();
                            BasicDBObject id = (BasicDBObject) document.get("_id");
                            int apiCollectionIdFromDoc = id.getInt("apiCollectionId");
                            String method = id.getString("method");
                            String endpoint = id.getString("endpoint");
                            apiCollectionIdsFromEvents.add(apiCollectionIdFromDoc);
                            List<String> severities = (List<String>) document.get("severities");
                            String key = apiCollectionIdFromDoc + " " + endpoint + " " + method;
                            apiInfoKeyToSeverities.put(key, severities);
                        }

                        loggerMaker.warnAndAddToDb("Malicious events count: " + apiInfoKeyToSeverities.size());

                        List<ApiInfo> apiInfos = ApiInfoDao.instance.getMCollection().find(Filters.in(ApiInfo.ID_API_COLLECTION_ID, apiCollectionIdsFromEvents)).into(new ArrayList<>());
                        Map<Integer, List<URLTemplate>> apiCollectionUrlTemplates = new HashMap<>();
                        Map<String, ApiInfoKey> apiInfoKeyToApiInfo = new HashMap<>();

                        RuntimeUtil.fillURLTemplatesMap(apiInfos, null, apiCollectionUrlTemplates, apiInfoKeyToApiInfo);
                        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
                        Map<ApiInfoKey, Float> apiInfoKeyToThreatScore = new HashMap<>();

                        for(String apiInfoKey : apiInfoKeyToSeverities.keySet()){
                            String[] parts = apiInfoKey.split(" ");
                            int apiCollectionId = Integer.parseInt(parts[0]);
                            String url = parts[1];
                            String method = parts[2];
                            URLTemplate urlTemplate = isMatchingUrl(apiCollectionId, url, method, apiCollectionUrlTemplates, lfiTrie, osCommandInjectionTrie, ssrfTrie);
                            List<String> severities = apiInfoKeyToSeverities.get(apiInfoKey);
                            float threatScore = MaliciousEventDao.getThreatScoreFromSeverities(severities);
                            if(urlTemplate != null || apiInfoKeyToApiInfo.containsKey(apiInfoKey)){
                                ApiInfoKey apiInfoKeyObj = null;
                                if(urlTemplate != null){
                                    apiInfoKeyObj = new ApiInfoKey(apiCollectionId, urlTemplate.getTemplateString(), urlTemplate.getMethod());
                                    loggerMaker.warnAndAddToDb("Updating risk score for " + urlTemplate.getTemplateString() + " " + urlTemplate.getMethod().name() + " " + apiCollectionId + " to " + threatScore);
                                }else{
                                    apiInfoKeyObj = apiInfoKeyToApiInfo.get(apiInfoKey);
                                    loggerMaker.warnAndAddToDb("Updating risk score for from normal api info " + url + " " + method + " " + apiCollectionId + " to " + threatScore);
                                }
                                apiInfoKeyToThreatScore.put(apiInfoKeyObj, Math.max(apiInfoKeyToThreatScore.getOrDefault(apiInfoKeyObj, 0.0f), threatScore));
                            }
                        }
                            
                        if(apiInfoKeyToThreatScore.size() > 0){
                            for(ApiInfoKey apiInfoKey : apiInfoKeyToThreatScore.keySet()){
                                float threatScore = apiInfoKeyToThreatScore.get(apiInfoKey);
                                Bson filter = ApiInfoDao.getFilter(apiInfoKey);
                                updates.add(new UpdateManyModel<>(filter, Updates.set(ApiInfo.THREAT_SCORE, threatScore)));
                            }
                        }
                        if(updates.size() > 0){
                            loggerMaker.warnAndAddToDb("Updating risk score for " + updates.size() + " api infos");
                            ApiInfoDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
                        }
                        AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), updateForLastCronRunInfo);
                        loggerMaker.warnAndAddToDb("Risk score sync cron completed for account " + accountId + " in " + (Context.now() - startTimestamp) + " seconds");
                    }
                }, "risk-score-sync-cron");
            }
        }, 0, 15, TimeUnit.MINUTES);
    }
}
