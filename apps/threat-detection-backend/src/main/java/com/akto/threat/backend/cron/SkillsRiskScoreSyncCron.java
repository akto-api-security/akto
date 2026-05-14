package com.akto.threat.backend.cron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.bson.conversions.Bson;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.type.URLMethods;
import com.akto.billing.UsageMetricUtils;
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
import com.mongodb.client.model.UpdateManyModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class SkillsRiskScoreSyncCron {

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(SkillsRiskScoreSyncCron.class, LogDb.THREAT_DETECTION);

    public void setUp() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            int accountId = t.getId();
                            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, "ENDPOINT_SECURITY");
                            if (!featureAccess.getIsGranted()) {
                                loggerMaker.debugAndAddToDb("ENDPOINT_SECURITY feature not granted for account " + accountId + ", skipping skills risk score sync");
                                return;
                            }
                            int startTimestamp = Context.now();
                            loggerMaker.debugAndAddToDb("Skills risk score sync cron started for account " + accountId + " at " + startTimestamp);

                            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                            LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                            int deltaEndTime = Context.now();
                            int deltaStartTime = deltaEndTime - Constants.ONE_DAY_TIMESTAMP;

                            Bson updateForLastCronRunInfo = Updates.set(
                                AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.LAST_ATLAS_THREAT_SCORE_SYNC,
                                deltaEndTime
                            );

                            if (lastRunTimerInfo != null) {
                                if (deltaEndTime - lastRunTimerInfo.getLastInfoResetted() <= Constants.ONE_DAY_TIMESTAMP) {
                                    int last = lastRunTimerInfo.getLastAtlasThreatScoreSync();
                                    deltaStartTime = (last > 0) ? last : (deltaEndTime - Constants.ONE_DAY_TIMESTAMP);
                                } else {
                                    updateForLastCronRunInfo = Updates.combine(
                                        updateForLastCronRunInfo,
                                        Updates.set(AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.LAST_INFO_RESETTED, deltaEndTime)
                                    );
                                }
                            }

                            BasicDBObject groupedId = new BasicDBObject("host", "$host")
                                .append("method", "$latestApiMethod")
                                .append("endpoint", "$latestApiEndpoint");

                            List<Bson> pipeline = new ArrayList<>();
                            pipeline.add(Aggregates.match(Filters.and(
                                Filters.gte("detectedAt", deltaStartTime),
                                Filters.lte("detectedAt", deltaEndTime),
                                Filters.eq("successfulExploit", true),
                                Filters.eq("contextSource", "ENDPOINT")
                            )));
                            pipeline.add(Aggregates.group(groupedId, Accumulators.addToSet("severities", "$severity")));

                            MongoCursor<BasicDBObject> cursor = MaliciousEventDao.instance
                                .getCollection(String.valueOf(accountId))
                                .aggregate(pipeline, BasicDBObject.class)
                                .cursor();

                            // Build host -> apiCollectionId map
                            List<ApiCollection> allCollections = ApiCollectionsDao.fetchAllHosts();
                            Map<String, Integer> hostToCollectionId = new HashMap<>();
                            for (ApiCollection col : allCollections) {
                                if (col.getHostName() != null) {
                                    hostToCollectionId.put(col.getHostName(), col.getId());
                                }
                            }

                            Map<ApiInfoKey, Float> apiInfoKeyToRiskScore = new HashMap<>();
                            while (cursor.hasNext()) {
                                BasicDBObject document = cursor.next();
                                BasicDBObject id = (BasicDBObject) document.get("_id");
                                String host = id.getString("host");
                                String method = id.getString("method");
                                String endpoint = id.getString("endpoint");

                                if (endpoint == null || !endpoint.startsWith("/skill")) {
                                    continue;
                                }

                                Integer collectionId = hostToCollectionId.get(host);
                                if (collectionId == null) {
                                    loggerMaker.debugAndAddToDb("No collection found for host: " + host);
                                    continue;
                                }

                                @SuppressWarnings("unchecked")
                                List<String> severities = (List<String>) document.get("severities");
                                float riskScore = computeRiskScore(severities);

                                ApiInfoKey apiInfoKey = new ApiInfoKey(collectionId, endpoint, URLMethods.Method.valueOf(method));
                                apiInfoKeyToRiskScore.put(apiInfoKey, Math.max(apiInfoKeyToRiskScore.getOrDefault(apiInfoKey, 0.0f), riskScore));
                            }

                            loggerMaker.debugAndAddToDb("Skills malicious events count: " + apiInfoKeyToRiskScore.size());

                            List<ApiInfo.ApiInfoTag> maliciousTags = Collections.singletonList(
                                new ApiInfo.ApiInfoTag("malicious", "true")
                            );

                            List<WriteModel<ApiInfo>> updates = new ArrayList<>();
                            for (Map.Entry<ApiInfoKey, Float> entry : apiInfoKeyToRiskScore.entrySet()) {
                                Bson filter = ApiInfoDao.getFilter(entry.getKey());
                                updates.add(new UpdateManyModel<>(filter, Updates.combine(
                                    Updates.set(ApiInfo.RISK_SCORE, entry.getValue()),
                                    Updates.set(ApiInfo.TAGS_LIST, maliciousTags)
                                )));
                            }

                            if (!updates.isEmpty()) {
                                loggerMaker.warnAndAddToDb("Updating risk score for " + updates.size() + " api infos from skills events");
                                ApiInfoDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
                            }

                            AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), updateForLastCronRunInfo);
                            loggerMaker.warnAndAddToDb("Skills risk score sync cron completed for account " + accountId + " in " + (Context.now() - startTimestamp) + " seconds");
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error in skills risk score sync cron: " + e.getMessage());
                        }
                    }
                }, "skills-risk-score-sync-cron");
            }
        }, 0, 15, TimeUnit.MINUTES);
    }

    private static float computeRiskScore(List<String> severities) {
        float max = 0f;
        for (String s : severities) {
            float score = 0f;
            switch (s.toUpperCase()) {
                case "CRITICAL": score = 5f; break;
                case "HIGH":     score = 4f; break;
                case "MEDIUM":   score = 3f; break;
                default:         break;
            }
            max = Math.max(max, score);
        }
        return max;
    }
}
