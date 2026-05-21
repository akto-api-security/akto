package com.akto.utils.crons;

import com.akto.dao.AccountSettingsDao;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.agentic_sessions.UserAnalysisDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.gpt.handlers.gpt_prompts.UserQueryTopicClassifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.LastCronRunInfo;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Updates;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static com.akto.task.Cluster.callDibs;

/**
 * Scrolls Elasticsearch for agentic query records, classifies each via LLM, and aggregates
 * topic counts + token sums + harmful flags into UserAnalysisData per (serviceId, deviceId).
 * Per-account ts-range watermark stored in LastCronRunInfo.lastUserAnalysisCron.
 */
public class UserAnalysisCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UserAnalysisCron.class, LogDb.DASHBOARD);

    private static final int CRON_INTERVAL_MINUTES = 10;
    private static final int SLACK_SECONDS = 60;
    private static final int PAGE_SIZE = 500;
    private static final int MAX_DOCS_PER_ACCOUNT_PER_TICK = 200;
    private static final int MIN_QUERY_LENGTH = 20;
    private static final long INITIAL_LOOKBACK_MS = 10L * 60 * 1000;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Set<Integer> indicesEnsured = new HashSet<>();

    public void setUpUserAnalysisCronScheduler() {
        scheduler.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                if (!ElasticSearchClient.instance().isConfigured()) {
                    loggerMaker.debug("UserAnalysisCron: ES not configured, skipping tick.");
                    return;
                }
                Context.accountId.set(1_000_000);
                if (!callDibs(Cluster.USER_ANALYSIS_CRON_INFO, 600, 60)) {
                    loggerMaker.debug("UserAnalysisCron: dibs not acquired, skipping tick.");
                    return;
                }
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account account) {
                        try {
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
        if (indicesEnsured.add(accountId)) {
            try {
                UserAnalysisDataDao.instance.createIndicesIfAbsent();
                AgentUsersDao.instance.createIndicesIfAbsent();
            } catch (Exception e) {
                loggerMaker.error("UserAnalysisCron: failed to create indices for account "
                    + accountId + ": " + e.getMessage());
            }
        }
        AccountSettings settings = AccountSettingsDao.instance.findOne(AccountSettingsDao.generateFilter());
        long nowMs = System.currentTimeMillis();
        long endTs = nowMs - SLACK_SECONDS * 1000L;
        long startTs = endTs - INITIAL_LOOKBACK_MS;
        if (settings != null) {
            LastCronRunInfo info = settings.getLastUpdatedCronInfo();
            if (info != null && info.getLastUserAnalysisCron() > 0) {
                startTs = info.getLastUserAnalysisCron() * 1000L;
            }
        }
        if (startTs >= endTs) return;

        Map<AggregateKey, Aggregate> aggregates = new HashMap<>();
        UserQueryTopicClassifier classifier = new UserQueryTopicClassifier();
        final int[] processed = {0};
        final long[] lastDocTs = {startTs};

        ElasticSearchClient.instance().scrollQueryData(accountId, startTs, endTs, PAGE_SIZE,
            MAX_DOCS_PER_ACCOUNT_PER_TICK,
            new Consumer<AgentQueryRecord>() {
                @Override
                public void accept(AgentQueryRecord rec) {
                    if (rec.getServiceId() == null || rec.getServiceId().isEmpty()) return;
                    if (rec.getDeviceId() == null || rec.getDeviceId().isEmpty()) return;
                    if (rec.getQueryPayload() == null || rec.getQueryPayload().length() < MIN_QUERY_LENGTH) {
                        return;
                    }

                    AggregateKey key = new AggregateKey(rec.getServiceId(), rec.getDeviceId());
                    Aggregate agg = aggregates.computeIfAbsent(key, k -> new Aggregate(rec.getUserName()));
                    agg.inputTokens += rec.getInputTokens();
                    agg.outputTokens += rec.getOutputTokens();
                    if (rec.getUserName() != null && !rec.getUserName().isEmpty()) {
                        agg.userName = rec.getUserName();
                    }

                    BasicDBObject q = new BasicDBObject()
                        .append(UserQueryTopicClassifier.QUERY_PAYLOAD, rec.getQueryPayload())
                        .append(UserQueryTopicClassifier.RESPONSE_PAYLOAD, rec.getResponsePayload() == null ? "" : rec.getResponsePayload());

                    BasicDBObject result;
                    try {
                        result = classifier.handle(q);
                    } catch (Exception e) {
                        loggerMaker.error("UserAnalysisCron: classifier error for accountId "
                            + accountId + ": " + e.getMessage());
                        return;
                    }
                    if (result == null) return;

                    Object topicsObj = result.get("topics");
                    if (topicsObj instanceof List) {
                        for (Object t : (List<?>) topicsObj) {
                            String topic = String.valueOf(t).trim();
                            if (topic.isEmpty()) continue;
                            agg.topicDeltas.merge(topic, 1, Integer::sum);
                        }
                    }

                    boolean harmful = result.getBoolean("harmful", false);
                    if (harmful) {
                        String category = result.getString("harmfulCategory", "general");
                        String reason = result.getString("harmfulReason", "");
                        if (category == null || category.isEmpty()) category = "general";
                        BasicDBObject entry = (BasicDBObject) agg.harmfulMerge.get(category);
                        if (entry == null) {
                            entry = new BasicDBObject()
                                .append("count", 0)
                                .append("lastSeenAt", rec.getTimeStampMs())
                                .append("lastReason", reason);
                            agg.harmfulMerge.put(category, entry);
                        }
                        entry.put("count", entry.getInt("count", 0) + 1);
                        entry.put("lastSeenAt", rec.getTimeStampMs());
                        if (reason != null && !reason.isEmpty()) entry.put("lastReason", reason);
                    }

                    processed[0]++;
                    if (rec.getTimeStampMs() > lastDocTs[0]) {
                        lastDocTs[0] = rec.getTimeStampMs();
                    }
                }
            });

        if (processed[0] == 0) {
            // Nothing in window — advance watermark to avoid re-scanning the empty range next tick.
            advanceWatermark(endTs);
            return;
        }

        long flushTs = System.currentTimeMillis();
        for (Map.Entry<AggregateKey, Aggregate> e : aggregates.entrySet()) {
            try {
                UserAnalysisDataDao.instance.upsertAggregates(
                    e.getKey().serviceId,
                    e.getKey().deviceId,
                    e.getValue().userName,
                    e.getValue().topicDeltas,
                    e.getValue().inputTokens,
                    e.getValue().outputTokens,
                    e.getValue().harmfulMerge,
                    null,
                    flushTs
                );
            } catch (Exception ex) {
                loggerMaker.error("UserAnalysisCron: upsert failed for ("
                    + e.getKey().serviceId + ", " + e.getKey().deviceId + "): " + ex.getMessage());
            }
        }

        // If we hit the cap, advance watermark only to the last processed doc's ts so the
        // remainder is picked up next tick. Otherwise advance to endTs.
        long newWatermark = processed[0] >= MAX_DOCS_PER_ACCOUNT_PER_TICK ? lastDocTs[0] : endTs;
        advanceWatermark(newWatermark);
    }

    private void advanceWatermark(long tsMs) {
        try {
            AccountSettingsDao.instance.updateOne(
                AccountSettingsDao.generateFilter(),
                Updates.set(
                    AccountSettings.LAST_UPDATED_CRON_INFO + "." + LastCronRunInfo.LAST_USER_ANALYSIS_CRON,
                    (int) (tsMs / 1000)
                )
            );
        } catch (Exception e) {
            loggerMaker.error("UserAnalysisCron: failed to advance watermark: " + e.getMessage());
        }
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

    private static final class Aggregate {
        String userName;
        long inputTokens;
        long outputTokens;
        final Map<String, Integer> topicDeltas = new HashMap<>();
        final Map<String, Object> harmfulMerge = new HashMap<>();

        Aggregate(String userName) {
            this.userName = userName;
        }
    }
}
