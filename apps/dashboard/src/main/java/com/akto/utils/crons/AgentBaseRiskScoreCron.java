package com.akto.utils.crons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.bson.Document;
import org.bson.conversions.Bson;
import com.akto.DaoInit;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.AccountsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.agents.AgentBaseRiskScoreDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.agents.AgentBaseRiskScore;
import com.akto.dto.billing.FeatureAccess;
import com.akto.gpt.handlers.gpt_prompts.AgentBaseRiskScoreAnalyzer;
import com.akto.gpt.handlers.gpt_prompts.TestExecutorModifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.Constants;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import static com.akto.task.Cluster.callDibs;

public class AgentBaseRiskScoreCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentBaseRiskScoreCron.class, LogDb.DASHBOARD);

    private static final int PER_ACCOUNT_LIMIT = 200;
    private static final int RECALC_THRSHOLD_SECONDS = 7 * 24 * 60 * 60;
    private static final int CONCURRENCY = 8;

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpAgentBaseRiskScoreCronScheduler() {
        scheduler.scheduleWithFixedDelay(this::run, 0, 30, TimeUnit.MINUTES);
    }

    private void run() {
        try {
            Context.accountId.set(1_000_000);
            if (!callDibs(Cluster.AGENT_BASE_RISK_SCORE_CRON_INFO, 1500, 60)) {
                loggerMaker.debugAndAddToDb("Agent base risk score cron dibs not acquired, thus skipping cron");
                return;
            }
            AccountTask.instance.executeTask(this::processAccount, "agent-base-risk-score-cron");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in agent base risk score cron: " + e.getMessage());
        }
    }

    private void processAccount(Account account) {
        int accountId = account.getId();
        try {
            FeatureAccess featureAccess = UsageMetricUtils.getFeatureAccessSaas(accountId, TestExecutorModifier._AKTO_GPT_AI);
            if (featureAccess == null || !featureAccess.getIsGranted()) {
                return;
            }

            List<ApiCollection> candidates = findCandidates();
            if (candidates.isEmpty()) {
                return;
            }
            loggerMaker.infoAndAddToDb("Agent base risk score cron processing accountId=" + accountId
                + ", candidates=" + candidates.size());

            Map<String, AgentBaseRiskScore> cacheMap = loadCache();

            Map<String, List<ApiCollection>> byAgentKey = new LinkedHashMap<>();
            for (ApiCollection collection : candidates) {
                String agentKey = AgentBaseRiskScoreAnalyzer.extractAgentCacheKey(collection);
                byAgentKey.computeIfAbsent(agentKey, k -> new ArrayList<>()).add(collection);
            }

            List<WriteModel<ApiCollection>> apiCollectionUpdates = Collections.synchronizedList(new ArrayList<>());
            List<WriteModel<AgentBaseRiskScore>> cacheUpdates = Collections.synchronizedList(new ArrayList<>());
            ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
            try {
                for (Map.Entry<String, List<ApiCollection>> entry : byAgentKey.entrySet()) {
                    String agentKey = entry.getKey();
                    List<ApiCollection> group = entry.getValue();
                    pool.submit(() -> scoreAgentGroup(accountId, agentKey, group, cacheMap, apiCollectionUpdates, cacheUpdates));
                }
            } finally {
                pool.shutdown();
                try {
                    pool.awaitTermination(20, TimeUnit.MINUTES);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (!apiCollectionUpdates.isEmpty()) {
                ApiCollectionsDao.instance.bulkWrite(apiCollectionUpdates, new BulkWriteOptions().ordered(false));
                loggerMaker.infoAndAddToDb("Agent base risk score cron scored " + apiCollectionUpdates.size()
                    + "/" + candidates.size() + " collections for accountId=" + accountId);
            }
            if (!cacheUpdates.isEmpty()) {
                AgentBaseRiskScoreDao.instance.bulkWrite(cacheUpdates, new BulkWriteOptions().ordered(false));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in agent base risk score cron for accountId=" + accountId + ": " + e.getMessage());
        }
    }

    private List<ApiCollection> findCandidates() {
        Bson hasEdges = Filters.and(
            Filters.exists(ApiCollection.SERVICE_GRAPH_EDGES, true),
            Filters.ne(ApiCollection.SERVICE_GRAPH_EDGES, new Document())
        );
        Bson notDeactivated = Filters.or(
            Filters.exists(ApiCollection._DEACTIVATED, false),
            Filters.eq(ApiCollection._DEACTIVATED, false)
        );
        Bson staleGate = Filters.or(
            Filters.exists(ApiCollection.BASE_RISK_SCORE_CALCULATED_AT, false),
            Filters.lte(ApiCollection.BASE_RISK_SCORE_CALCULATED_AT, Context.now() - RECALC_THRSHOLD_SECONDS)
        );
        Bson filter = Filters.and(hasEdges, notDeactivated, staleGate);

        return ApiCollectionsDao.instance.findAll(
            filter, 0, PER_ACCOUNT_LIMIT, Sorts.descending(ApiCollection.START_TS),
            Projections.include(ApiCollection.ID, ApiCollection.SERVICE_GRAPH_EDGES,
                ApiCollection.BASE_RISK_SCORE_CALCULATED_AT, ApiCollection.TAGS_STRING)
        );
    }

    private Map<String, AgentBaseRiskScore> loadCache() {
        return AgentBaseRiskScoreDao.instance.findAll(Filters.empty()).stream()
                    .collect(Collectors.toMap(AgentBaseRiskScore::getId, Function.identity()));
    }

    private void scoreAgentGroup(int accountId, String agentKey, List<ApiCollection> group,
            Map<String, AgentBaseRiskScore> cacheMap,
            List<WriteModel<ApiCollection>> apiCollectionUpdates, List<WriteModel<AgentBaseRiskScore>> cacheUpdates) {
        try {
            Context.accountId.set(accountId);

            AgentBaseRiskScore cached = cacheMap.get(agentKey);
            boolean fresh = cached != null && cached.getBaseRiskScoreCalculatedAt() != null
                && cached.getBaseRiskScoreCalculatedAt() >= Context.now() - RECALC_THRSHOLD_SECONDS;

            double score;
            String reason;
            if (fresh) {
                score = cached.getBaseRiskScore();
                reason = cached.getBaseRiskScoreReason();
            } else {
                ApiCollection representative = group.get(0);
                String agentContextJson = AgentBaseRiskScoreAnalyzer.buildAgentContextJson(representative);
                BasicDBObject queryData = new BasicDBObject();
                queryData.put(AgentBaseRiskScoreAnalyzer.AGENT_CONTEXT_JSON, agentContextJson);

                BasicDBObject response = new AgentBaseRiskScoreAnalyzer().handle(queryData);
                if (response == null || response.containsKey("error") || !response.containsKey(AgentBaseRiskScoreAnalyzer.SCORE)) {
                    loggerMaker.debugAndAddToDb("Agent base risk score: skipping agentKey=" + agentKey
                        + " (no usable LLM response), will retry next tick");
                    return;
                }
                score = response.getDouble(AgentBaseRiskScoreAnalyzer.SCORE);
                reason = response.getString(AgentBaseRiskScoreAnalyzer.REASON);
            }

            int now = Context.now();
            for (ApiCollection collection : group) {
                apiCollectionUpdates.add(new UpdateOneModel<>(
                    Filters.eq(ApiCollection.ID, collection.getId()),
                    Updates.combine(
                        Updates.set(ApiCollection.BASE_RISK_SCORE, score),
                        Updates.set(ApiCollection.BASE_RISK_SCORE_REASON, reason),
                        Updates.set(ApiCollection.BASE_RISK_SCORE_CALCULATED_AT, now)
                    )
                ));
            }

            if (!fresh) {
                cacheUpdates.add(new UpdateOneModel<>(
                    Filters.eq(AgentBaseRiskScore.ID, agentKey),
                    Updates.combine(
                        Updates.set(AgentBaseRiskScore.BASE_RISK_SCORE, score),
                        Updates.set(AgentBaseRiskScore.BASE_RISK_SCORE_REASON, reason),
                        Updates.set(AgentBaseRiskScore.BASE_RISK_SCORE_CALCULATED_AT, now)
                    ),
                    new UpdateOptions().upsert(true)
                ));
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error scoring agent base risk for agentKey=" + agentKey + ": " + e.getMessage());
        }
    }

    public void forceRunForAccount(int accountId) {
        Context.accountId.set(accountId);
        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, accountId));
        if (account == null) {
            loggerMaker.errorAndAddToDb("forceRunForAccount: no account found for accountId=" + accountId);
            return;
        }
        processAccount(account);
    }

    public static void main(String[] args) {
        DaoInit.init(new com.mongodb.ConnectionString("mongodb://localhost:27017"));
        AgentBaseRiskScoreCron cron = new AgentBaseRiskScoreCron();
        cron.forceRunForAccount(1_000_000);
    }
}
