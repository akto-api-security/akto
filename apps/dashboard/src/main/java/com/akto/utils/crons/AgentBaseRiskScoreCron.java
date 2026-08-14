package com.akto.utils.crons;

import java.util.ArrayList;
import java.util.Collections;
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

/**
 * Gives every AI agent (an ApiCollection with a non-empty serviceGraphEdges graph) a base risk
 * score via one LLM call per agent, based on what it's wired to. Runs every 30 minutes, capped
 * at PER_ACCOUNT_LIMIT agents per account per tick, newest-created first, and never re-scores an
 * agent within SEVEN_DAYS_SECONDS.
 *
 * The same logical agent can show up as multiple distinct ApiCollection docs (ApiCollection._id
 * is hashCode(hostName), and hostName encodes more than just the agent name), so scores are also
 * cached in a separate collection (see AgentBaseRiskScore), keyed by the agent's "bot-id" tag when
 * present, else its display name - a duplicate is filled in from that cache instead of spending
 * another LLM call.
 */
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
            // Expiry (25 min) is deliberately less than the 30-min cadence, so a stalled/crashed
            // run can never block the next tick from acquiring the lock - the 7-day DB gate, not
            // this lock, is what actually prevents double-scoring.
            if (!callDibs(Cluster.AGENT_BASE_RISK_SCORE_CRON_INFO, 1500, 60)) {
                loggerMaker.debugAndAddToDb("Agent base risk score cron dibs not acquired, thus skipping cron");
                return;
            }
            AccountTask.instance.executeTask(this::processAccount, "agent-base-risk-score-cron");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in agent base risk score cron: " + e.getMessage());
        }
    }

    /** Assumes Context.accountId is already set by the caller (AccountTask, or forceRunForAccount). */
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

            // Whole cache collection is small (one doc per unique agent in this account) - load
            // it once up front instead of a DB round trip per candidate.
            Map<String, AgentBaseRiskScore> cacheMap = loadCache();

            List<WriteModel<ApiCollection>> apiCollectionUpdates = Collections.synchronizedList(new ArrayList<>());
            List<WriteModel<AgentBaseRiskScore>> cacheUpdates = Collections.synchronizedList(new ArrayList<>());
            ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
            try {
                for (ApiCollection collection : candidates) {
                    pool.submit(() -> scoreCollection(accountId, collection, cacheMap, apiCollectionUpdates, cacheUpdates));
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

    /** Assumes Context.accountId is already set by the caller. */
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

    /**
     * Runs on a pool thread, so Context.accountId must be set here - it doesn't cross threads.
     * cacheMap is read-only here (built once in processAccount, never mutated during scoring) - safe
     * for concurrent reads from multiple pool threads. Both output lists are synchronized and only
     * ever appended to, then bulk-written once after the whole pool drains.
     */
    private void scoreCollection(int accountId, ApiCollection collection, Map<String, AgentBaseRiskScore> cacheMap,
            List<WriteModel<ApiCollection>> apiCollectionUpdates, List<WriteModel<AgentBaseRiskScore>> cacheUpdates) {
        int collectionId = collection.getId();
        try {
            Context.accountId.set(accountId);

            String agentKey = AgentBaseRiskScoreAnalyzer.extractAgentCacheKey(collection);
            AgentBaseRiskScore cached = agentKey != null ? cacheMap.get(agentKey) : null;
            boolean fresh = cached != null && cached.getBaseRiskScoreCalculatedAt() != null
                && cached.getBaseRiskScoreCalculatedAt() >= Context.now() - RECALC_THRSHOLD_SECONDS;

            if (fresh) {
                apiCollectionUpdates.add(new UpdateOneModel<>(
                    Filters.eq(ApiCollection.ID, collectionId),
                    Updates.combine(
                        Updates.set(ApiCollection.BASE_RISK_SCORE, cached.getBaseRiskScore()),
                        Updates.set(ApiCollection.BASE_RISK_SCORE_REASON, cached.getBaseRiskScoreReason()),
                        Updates.set(ApiCollection.BASE_RISK_SCORE_CALCULATED_AT, Context.now())
                    )
                ));
                return;
            }

            String agentContextJson = AgentBaseRiskScoreAnalyzer.buildAgentContextJson(collection);
            BasicDBObject queryData = new BasicDBObject();
            queryData.put(AgentBaseRiskScoreAnalyzer.AGENT_CONTEXT_JSON, agentContextJson);

            BasicDBObject response = new AgentBaseRiskScoreAnalyzer().handle(queryData);
            if (response == null || response.containsKey("error") || !response.containsKey(AgentBaseRiskScoreAnalyzer.SCORE)) {
                loggerMaker.debugAndAddToDb("Agent base risk score: skipping collection " + collectionId
                    + " (no usable LLM response), will retry next tick");
                return;
            }

            double score = response.getDouble(AgentBaseRiskScoreAnalyzer.SCORE);
            String reason = response.getString(AgentBaseRiskScoreAnalyzer.REASON);
            int now = Context.now();

            apiCollectionUpdates.add(new UpdateOneModel<>(
                Filters.eq(ApiCollection.ID, collectionId),
                Updates.combine(
                    Updates.set(ApiCollection.BASE_RISK_SCORE, score),
                    Updates.set(ApiCollection.BASE_RISK_SCORE_REASON, reason),
                    Updates.set(ApiCollection.BASE_RISK_SCORE_CALCULATED_AT, now)
                )
            ));

            if (agentKey != null) {
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
            loggerMaker.errorAndAddToDb(e, "Error scoring agent base risk for collection " + collectionId + ": " + e.getMessage());
        }
    }

    /**
     * Manual test entrypoint: runs the exact same processAccount logic for a single account,
     * bypassing callDibs/AccountTask's all-accounts loop. Not wired into any scheduler - call this
     * directly when needed.
     */
    public void forceRunForAccount(int accountId) {
        Context.accountId.set(accountId);
        Account account = AccountsDao.instance.findOne(Filters.eq(Constants.ID, accountId));
        if (account == null) {
            loggerMaker.errorAndAddToDb("forceRunForAccount: no account found for accountId=" + accountId);
            return;
        }
        processAccount(account);
    }
}
