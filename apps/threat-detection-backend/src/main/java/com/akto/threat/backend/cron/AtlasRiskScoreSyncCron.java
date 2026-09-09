package com.akto.threat.backend.cron;

import java.util.ArrayList;
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

// Handles ENDPOINT-context malicious events that are NOT skill events (those are owned by
// SkillsRiskScoreSyncCron, matched on endpoint prefix "/skill" + category "malicious_skill_detected").
// Resolves apiCollectionId via host (same as SkillsRiskScoreSyncCron, since these events don't carry a
// reliable apiCollectionId), then scores via RiskScoreSyncCron's shared url-template matching. No tag.
public class AtlasRiskScoreSyncCron {

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final LoggerMaker loggerMaker = new LoggerMaker(AtlasRiskScoreSyncCron.class, LogDb.THREAT_DETECTION);

    public void setUp() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        try {
                            int accountId = t.getId();
                            if (accountId != 1783981503) {
                                loggerMaker.infoAndAddToDb("Skipping Atlas risk score sync cron for account " + accountId);
                                return;
                            }
                            int startTimestamp = Context.now();
                            loggerMaker.infoAndAddToDb("Atlas risk score sync cron started for account " + accountId + " at " + startTimestamp);

                            AccountSettings accountSettings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
                            LastCronRunInfo lastRunTimerInfo = accountSettings.getLastUpdatedCronInfo();
                            int deltaEndTime = Context.now();
                            int deltaStartTime = deltaEndTime - Constants.ONE_DAY_TIMESTAMP;

                            Bson updateForLastCronRunInfo = Updates.set(
                                AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.LAST_NON_SKILL_THREAT_SCORE_SYNC,
                                deltaEndTime
                            );

                            if (lastRunTimerInfo != null) {
                                if (deltaEndTime - lastRunTimerInfo.getLastInfoResetted() <= Constants.ONE_DAY_TIMESTAMP) {
                                    int last = lastRunTimerInfo.getLastNonSkillThreatScoreSync();
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

                            Map<String, Integer> hostToCollectionId = null;

                            Map<String, List<String>> apiInfoKeyToSeverities = new HashMap<>();
                            while (cursor.hasNext()) {
                                BasicDBObject document = cursor.next();
                                BasicDBObject id = (BasicDBObject) document.get("_id");
                                String host = id.getString("host");
                                String method = id.getString("method");
                                String endpoint = id.getString("endpoint");

                                if (endpoint == null) {
                                    continue;
                                }

                                // /skill-prefixed endpoints belong entirely to SkillsRiskScoreSyncCron's bucket
                                if (endpoint.startsWith("/skill")) {
                                    continue;
                                }

                                if (hostToCollectionId == null) {
                                    hostToCollectionId = new HashMap<>();
                                    for (ApiCollection col : ApiCollectionsDao.fetchAllHosts()) {
                                        if (col.getHostName() != null) {
                                            hostToCollectionId.put(col.getHostName().toLowerCase(), col.getId());
                                        }
                                    }
                                }

                                if (host == null) {
                                    continue;
                                }

                                Integer collectionId = hostToCollectionId.get(host.toLowerCase());
                                if (collectionId == null) {
                                    loggerMaker.infoAndAddToDb("No collection found for host: " + host);
                                    continue;
                                }

                                @SuppressWarnings("unchecked")
                                List<String> severities = (List<String>) document.get("severities");
                                if (severities == null || severities.isEmpty()) continue;
                                if (method == null) continue;

                                String key = collectionId + " " + endpoint + " " + method;
                                apiInfoKeyToSeverities.put(key, severities);
                            }

                            loggerMaker.infoAndAddToDb("Atlas malicious events count: " + apiInfoKeyToSeverities.size());

                            Map<ApiInfoKey, Float> apiInfoKeyToThreatScore = RiskScoreSyncCron.resolveThreatScores(
                                apiInfoKeyToSeverities, AtlasRiskScoreSyncCron::computeRiskScore);

                            List<WriteModel<ApiInfo>> updates = new ArrayList<>();
                            for (Map.Entry<ApiInfoKey, Float> entry : apiInfoKeyToThreatScore.entrySet()) {
                                Bson filter = ApiInfoDao.getFilter(entry.getKey());
                                updates.add(new UpdateManyModel<>(filter, Updates.set(ApiInfo.THREAT_SCORE, entry.getValue())));
                            }

                            if (!updates.isEmpty()) {
                                loggerMaker.infoAndAddToDb("Updating risk score for " + updates.size() + " api infos from atlas events");
                                ApiInfoDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
                            }

                            AccountSettingsDao.instance.updateOne(AccountSettingsDao.generateFilter(), updateForLastCronRunInfo);
                            loggerMaker.infoAndAddToDb("Atlas risk score sync cron completed for account " + accountId + " in " + (Context.now() - startTimestamp) + " seconds");
                        } catch (Exception e) {
                            loggerMaker.errorAndAddToDb(e, "Error in atlas risk score sync cron: " + e.getMessage());
                        }
                    }
                }, "atlas-risk-score-sync-cron");
            }
        }, 0, 15, TimeUnit.MINUTES);
    }

    private static float computeRiskScore(List<String> severities) {
        float max = 0f;
        for (String s : severities) {
            float score = 0f;
            switch (s.toUpperCase()) {
                case "CRITICAL": score = 4f; break;
                case "HIGH":     score = 3f; break;
                case "MEDIUM":   score = 2f; break;
                case "LOW":      score = 1f; break;
                default:         break;
            }
            max = Math.max(max, score);
        }
        return max;
    }
}
