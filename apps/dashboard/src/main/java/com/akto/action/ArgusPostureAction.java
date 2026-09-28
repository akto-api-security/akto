package com.akto.action;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.context.Context;
import com.akto.dao.testing.AgentConversationResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightService;
import com.akto.service.posture.ArgusPostureService;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.search.SearchClientFactory;
import com.mongodb.BasicDBObject;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


public class ArgusPostureAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ArgusPostureAction.class, LogDb.DASHBOARD);

    // Malicious/guardrail events: a capped raw-row fetch (no server-side {policy,agent}
    // aggregation exists on the threat-detection-backend), same limit SecurityPostureAction's own
    // trendWindowEventsFuture already uses.
    private static final int MAX_THREAT_FETCH_LIMIT = 100000;
    private static final int URLS_PER_ISSUE_GROUP_CAP = 3;
    private static final int TOPICS_CAP = 5;
    private static final int SUB_TOPICS_CAP = 5;
    private static final int ATTACK_FLOW_ISSUE_COUNT = 2;
    private static final int CONVERSATION_IDS_PER_ISSUE_CAP = 3;

    // The 4 independent card-data reads (+ the attack-flow conversation chain, which only depends
    // on the first of them) run concurrently here rather than one after another — none of them
    // depends on another's result except that one chain, so there is no reason for this to be a
    // waterfall.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(6);
    private static final int FETCH_TIMEOUT_SECONDS = 20;

    private final ArgusPostureService argusPostureService = new ArgusPostureService();
    private final InsightService insightService = new InsightService();

    @Getter @Setter private int startTimestamp;
    @Getter @Setter private int endTimestamp;
    @Getter @Setter private String environment;

    @Getter private BasicDBObject response = new BasicDBObject();
    @Getter private List<BasicDBObject> insights = new ArrayList<>();
    @Getter private Map<String, BasicDBObject> insightSummaries;

    public String fetchArgusPostureSummary() {
        try {
            if (endTimestamp == 0) endTimestamp = Context.now();

            final int accountId = Context.accountId.get();
            final Integer userId = Context.userId.get();
            final CONTEXT_SOURCE contextSource = Context.contextSource.get();

            InsightContext ctx = new InsightContext(accountId, userId, contextSource, startTimestamp, endTimestamp);
            InsightDataBundle bundle = insightService.getOrLoadBundle(ctx);

            this.response = argusPostureService.buildSummary(bundle, environment);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Argus posture summary: " + e.getMessage());
            addActionError("Failed to build Argus posture summary");
            return ERROR.toUpperCase();
        }
    }

    /**
     * The 5 Argus posture insight cards (red-team breakdown, attack-flow analysis, guardrail
     * breakdown/hotspot, observability) — fast, Java/aggregation-only data, no LLM call. See
     * fetchArgusPostureInsightSummaries for the AI write-up half, fetched separately so a slow/
     * cold-cache LLM call never blocks this card data from rendering.
     */
    public String fetchArgusPostureInsights() {
        try {
            if (endTimestamp == 0) endTimestamp = Context.now();
            InsightContext ctx = buildCtx();
            this.insights = buildCards(ctx);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Argus posture insights: " + e.getMessage());
            addActionError("Failed to build Argus posture insights");
            return ERROR.toUpperCase();
        }
    }

    /** Meant to be called asynchronously, after fetchArgusPostureInsights has already rendered its
     *  fast card data — this one does a real LLM call per card (in parallel — see
     *  ArgusPostureService#buildInsightCardSummaries) on a cache miss, so it must never block the
     *  cards themselves. Recomputes the same cards (cheap, no LLM) so it stays a standalone call
     *  the frontend can fire independently. */
    public String fetchArgusPostureInsightSummaries() {
        try {
            if (endTimestamp == 0) endTimestamp = Context.now();
            InsightContext ctx = buildCtx();
            List<BasicDBObject> cards = buildCards(ctx);
            this.insightSummaries = argusPostureService.buildInsightCardSummaries(ctx, cards, false);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error building Argus posture insight summaries: " + e.getMessage());
            addActionError("Failed to build Argus posture insight summaries");
            return ERROR.toUpperCase();
        }
    }

    private InsightContext buildCtx() {
        return new InsightContext(Context.accountId.get(), Context.userId.get(), Context.contextSource.get(),
                startTimestamp, endTimestamp);
    }

    /**
     * Fetches everything the 5 cards need, in parallel: the bundle load (collections/policies),
     * red-team open issues, guardrail/malicious events, and the two ElasticSearch observability
     * calls all run concurrently. The attack-flow card's real-conversation grounding is fetched
     * right after openIssueGroups resolves (it needs to know which issues are most critical
     * first), but that chain itself still runs concurrently with the other three independent
     * fetches, not after them.
     */
    private List<BasicDBObject> buildCards(InsightContext ctx) {
        final int accountId = ctx.getAccountId();
        final Integer userId = ctx.getUserId();
        final CONTEXT_SOURCE contextSource = ctx.getContextSource();
        long startMs = startTimestamp * 1000L;
        long endMs = endTimestamp * 1000L;

        Future<InsightDataBundle> bundleFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                () -> insightService.getOrLoadBundle(ctx)));
        Future<List<AgentFindingGroup>> openIssueGroupsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                () -> TestingRunIssuesDao.instance.openIssueGroupsForDashboard(startTimestamp, endTimestamp, URLS_PER_ISSUE_GROUP_CAP)));
        Future<List<DashboardMaliciousEvent>> maliciousEventsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                () -> insightService.fetchArgusMaliciousEvents(ctx, MAX_THREAT_FETCH_LIMIT)));
        Future<List<UserAnalysisData>> serviceObservabilityFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                () -> SearchClientFactory.instance().fetchAgenticServiceObservability(accountId, startMs, endMs, TOPICS_CAP)));
        Future<Map<String, Map<String, Integer>>> globalTopicsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                () -> SearchClientFactory.instance().fetchAgenticGlobalTopicHierarchy(accountId, startMs, endMs, TOPICS_CAP, SUB_TOPICS_CAP)));

        List<AgentFindingGroup> openIssueGroups = getOrEmpty(openIssueGroupsFuture, new ArrayList<>(), "openIssueGroups");
        Future<Map<String, AgentConversationResult>> criticalConversationsFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                () -> fetchCriticalIssueConversations(openIssueGroups)));

        InsightDataBundle bundle = getOrEmpty(bundleFuture, null, "bundle");
        List<DashboardMaliciousEvent> maliciousEvents = getOrEmpty(maliciousEventsFuture, new ArrayList<>(), "maliciousEvents");
        List<UserAnalysisData> serviceObservability = getOrEmpty(serviceObservabilityFuture, new ArrayList<>(), "serviceObservability");
        Map<String, Map<String, Integer>> globalTopics = getOrEmpty(globalTopicsFuture, new HashMap<>(), "globalTopics");
        Map<String, AgentConversationResult> criticalConversations = getOrEmpty(criticalConversationsFuture, new HashMap<>(), "criticalConversations");

        return argusPostureService.buildInsightCards(bundle, openIssueGroups, criticalConversations,
                maliciousEvents, serviceObservability, globalTopics);
    }

    /** The real validated conversations behind the account's most critical open issues — the
     *  "attack flow" card's grounding. A handful of conversationIds per issue, then one batched
     *  findValidatedSummaries call (never per-issue), same "never send an empty/unscoped $in"
     *  discipline the rest of this read chain already follows. */
    private Map<String, AgentConversationResult> fetchCriticalIssueConversations(List<AgentFindingGroup> openIssueGroups) {
        List<AgentFindingGroup> topCritical = argusPostureService.pickTopCriticalIssues(openIssueGroups, ATTACK_FLOW_ISSUE_COUNT);
        List<String> conversationIds = new ArrayList<>();
        for (AgentFindingGroup g : topCritical) {
            conversationIds.addAll(VulnerableTestingRunResultDao.instance
                    .conversationIdsForIssue(g.getCollectionId(), g.getType(), CONVERSATION_IDS_PER_ISSUE_CAP));
        }
        Map<String, AgentConversationResult> byId = new HashMap<>();
        if (conversationIds.isEmpty()) return byId;
        for (AgentConversationResult c : AgentConversationResultDao.instance.findValidatedSummaries(conversationIds)) {
            byId.put(c.getConversationId(), c);
        }
        return byId;
    }

    private <T> Callable<T> withContext(int accountId, Integer userId, CONTEXT_SOURCE contextSource, Callable<T> body) {
        return () -> {
            Context.accountId.set(accountId);
            Context.userId.set(userId);
            Context.contextSource.set(contextSource);
            try {
                return body.call();
            } finally {
                Context.accountId.remove();
                Context.userId.remove();
                Context.contextSource.remove();
            }
        };
    }

    private <T> T getOrEmpty(Future<T> future, T empty, String label) {
        try {
            return future.get(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("ArgusPostureAction: " + label + " future failed/timed out: " + e.getMessage());
            return empty;
        }
    }

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }
}
