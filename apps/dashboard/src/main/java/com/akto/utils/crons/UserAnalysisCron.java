package com.akto.utils.crons;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.agentic_sessions.UserAnalysisDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.gpt.handlers.gpt_prompts.UserQueryTopicClassifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.akto.util.LastCronRunInfo;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class UserAnalysisCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UserAnalysisCron.class, LogDb.DASHBOARD);

    private static final int CRON_INTERVAL_MINUTES = 10;
    private static final int SLACK_SECONDS = 60;
    private static final int PAGE_SIZE = 10;
    private static final int MAX_DOCS_PER_ACCOUNT_PER_TICK = 100;
    private static final int MIN_QUERY_LENGTH = 20;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpUserAnalysisCronScheduler() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                if (!ElasticSearchClient.instance().isConfigured()) {
                    loggerMaker.error("UserAnalysisCron: ES not configured, skipping tick.");
                    return;
                }
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        try {
                            loggerMaker.infoAndAddToDb("Starting user analysis cron for account " + account.getId());
                            processAccount(account.getId());
                        } catch (Exception e) {
                            loggerMaker.error("UserAnalysisCron: failure for account "
                                + account.getId() + ": " + e.getMessage());
                        }
                    }
                }, "user-analysis-cron-info");
            }
        }, 0, CRON_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    private void processAccount(int accountId) {
        Context.accountId.set(accountId);
        AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        long nowMs = System.currentTimeMillis();
        long endTs = nowMs - SLACK_SECONDS * 1000L;
        long startTs = resolveStartTs(settings);

        Map<AggregateKey, String> rollingSummaries = new HashMap<>();
        UserQueryTopicClassifier classifier = new UserQueryTopicClassifier();
        List<ElasticSearchClient.TopicUpdate> topicUpdates = new ArrayList<>();

        ElasticSearchClient.instance().scrollQueryData(accountId, startTs, endTs, PAGE_SIZE,
            MAX_DOCS_PER_ACCOUNT_PER_TICK,
            rec -> processRecord(rec, accountId, rollingSummaries, classifier, topicUpdates));

        if (!topicUpdates.isEmpty()) {
            try {
                ElasticSearchClient.instance().bulkUpdateTopics(topicUpdates);
            } catch (Exception e) {
                loggerMaker.error("UserAnalysisCron: bulkUpdateTopics failed for accountId " + accountId + ": " + e.getMessage());
            }
        }

        AccountSettingsDao.instance.updateOne(
            Filters.eq(Constants.ID, accountId),
            Updates.set(AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.LAST_USER_ANALYSIS_CRON, (endTs / 1000))
        );
    }

    private long resolveStartTs(AccountSettings settings) {
        if (settings == null) return 0;
        LastCronRunInfo info = settings.getLastUpdatedCronInfo();
        if (info == null) return 0;
        return info.getLastUserAnalysisCron() * 1000L;
    }

    private void processRecord(AgentQueryRecord rec, int accountId,
                               Map<AggregateKey, String> rollingSummaries,
                               UserQueryTopicClassifier classifier,
                               List<ElasticSearchClient.TopicUpdate> topicUpdates) {
        if (rec.getServiceId() == null || rec.getServiceId().isEmpty()) return;
        if (rec.getDeviceId() == null || rec.getDeviceId().isEmpty()) return;
        if (rec.getQueryPayload() == null || rec.getQueryPayload().length() < MIN_QUERY_LENGTH) return;

        AggregateKey key = new AggregateKey(rec.getServiceId(), rec.getDeviceId());
        String existingSummary = rollingSummaries.getOrDefault(key, "");

        BasicDBObject classifierInput = new BasicDBObject()
            .append(UserQueryTopicClassifier.QUERY_PAYLOAD, rec.getQueryPayload())
            .append(UserQueryTopicClassifier.RESPONSE_PAYLOAD, rec.getResponsePayload() != null ? rec.getResponsePayload() : "")
            .append(UserQueryTopicClassifier.EXISTING_SUMMARY, existingSummary);

        BasicDBObject result;
        try {
            result = classifier.handle(classifierInput);
        } catch (Exception e) {
            loggerMaker.error("UserAnalysisCron: classifier error for accountId " + accountId + ": " + e.getMessage());
            return;
        }
        if (result == null) return;

        Map<String, Integer> topicDeltas = new HashMap<>();
        applyTopics(result, topicDeltas);

        // Write the primary topic back to the ES document.
        Object topicsObj = result.get("topics");
        if (topicsObj instanceof List && !((List<?>) topicsObj).isEmpty()) {
            String primaryTopic = String.valueOf(((List<?>) topicsObj).get(0)).trim();
            if (!primaryTopic.isEmpty() && rec.getDocId() != null && !rec.getDocId().isEmpty()) {
                topicUpdates.add(new ElasticSearchClient.TopicUpdate(rec.getDocId(), primaryTopic));
            }
        }

        Map<String, Object> harmfulMerge = new HashMap<>();
        applyHarmful(result, rec.getTimeStampMs(), harmfulMerge);

        String newSummary = result.getString("summary", "");
        if (newSummary != null && !newSummary.isEmpty()) {
            rollingSummaries.put(key, newSummary);
        }

        // Use token counts from the ES record; fall back to payload-length heuristic (~4 chars/token).
        long inputTokens = rec.getInputTokens();
        long outputTokens = rec.getOutputTokens();
        if (inputTokens == 0 && rec.getQueryPayload() != null) {
            inputTokens = rec.getQueryPayload().length() / 4;
        }
        if (outputTokens == 0 && rec.getResponsePayload() != null) {
            outputTokens = rec.getResponsePayload().length() / 4;
        }

        String userName = rec.getUserName() != null ? rec.getUserName() : "";
        String summaryToWrite = rollingSummaries.get(key);

        try {
            UserAnalysisDataDao.instance.upsertAggregates(
                key.serviceId, key.deviceId, userName,
                topicDeltas, inputTokens, outputTokens,
                harmfulMerge, summaryToWrite, System.currentTimeMillis()
            );
        } catch (Exception ex) {
            loggerMaker.error("UserAnalysisCron: upsert failed for (" + key.serviceId + ", " + key.deviceId + "): " + ex.getMessage());
        }
    }

    private void applyTopics(BasicDBObject result, Map<String, Integer> topicDeltas) {
        Object topicsObj = result.get("topics");
        if (!(topicsObj instanceof List)) return;
        for (Object t : (List<?>) topicsObj) {
            String topic = String.valueOf(t).trim();
            if (!topic.isEmpty()) {
                topicDeltas.merge(topic, 1, Integer::sum);
            }
        }
    }

    private void applyHarmful(BasicDBObject result, long timestampMs, Map<String, Object> harmfulMerge) {
        if (!result.getBoolean("harmful", false)) return;
        String category = result.getString("harmfulCategory", "general");
        if (category == null || category.isEmpty()) category = "general";
        String reason = result.getString("harmfulReason", "");

        BasicDBObject entry = (BasicDBObject) harmfulMerge.get(category);
        if (entry == null) {
            entry = new BasicDBObject()
                .append("count", 0)
                .append("lastSeenAt", timestampMs)
                .append("lastReason", reason);
            harmfulMerge.put(category, entry);
        }
        entry.put("count", entry.getInt("count", 0) + 1);
        entry.put("lastSeenAt", timestampMs);
        if (reason != null && !reason.isEmpty()) entry.put("lastReason", reason);
    }

    private static final class AggregateKey {
        final String serviceId;
        final String deviceId;

        AggregateKey(String serviceId, String deviceId) {
            this.serviceId = serviceId;
            this.deviceId = deviceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AggregateKey)) return false;
            AggregateKey that = (AggregateKey) o;
            return Objects.equals(serviceId, that.serviceId) && Objects.equals(deviceId, that.deviceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceId, deviceId);
        }
    }
}
