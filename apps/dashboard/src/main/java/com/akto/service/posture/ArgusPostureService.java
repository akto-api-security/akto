package com.akto.service.posture;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.insights.InsightNarrativeCacheDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.traffic.CollectionTags;
import com.akto.gpt.handlers.gpt_prompts.AbstractGroundedNarrativeHandler;
import com.akto.gpt.handlers.gpt_prompts.ArgusAttackFlowNarrativeHandler;
import com.akto.gpt.handlers.gpt_prompts.ArgusInsightCardNarrativeHandler;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.InsightContext;
import com.akto.service.insights.InsightDataBundle;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ArgusPostureService {

    private static final String KEY_KPIS = "kpis";
    private static final String KEY_ENVIRONMENTS = "environments";

    private static final String KPI_ASSETS              = "assets";
    private static final String KPI_HIGH_RISK_AGENTS    = "highRiskAgents";
    private static final String KPI_IDENTITY_ACCESS     = "identityAccess";
    private static final String KPI_PRIVILEGED_TOOLS    = "privilegedTools";
    private static final String KPI_SENSITIVE_DATA      = "sensitiveData";
    private static final String KPI_PROTECTION_COVERAGE = "protectionCoverage";

    // Display buckets/tag-value lists moved to InsightUtil (environmentBucket/envTagValue) so any
    // AGENTIC insight provider can group by environment too, without depending on this package.
    private static final String ENV_PRODUCTION  = InsightUtil.ENV_PRODUCTION;
    private static final String ENV_STAGING     = InsightUtil.ENV_STAGING;
    private static final String ENV_DEVELOPMENT = InsightUtil.ENV_DEVELOPMENT;

    private static final String ENV_ID_ALL         = "all";
    private static final String ENV_ID_PRODUCTION  = "production";
    private static final String ENV_ID_STAGING     = "staging";
    private static final String ENV_ID_DEVELOPMENT = "development";

    private static final List<String> DEV_ENVS     = Arrays.asList("DEV");
    private static final List<String> STAGING_ENVS = Arrays.asList("STAGING", "PREPROD", "UAT", "QA", "INTEG");

    private static final double TONE_SUCCESS_AT = 100d;
    private static final double TONE_WARNING_AT = 70d;

    public BasicDBObject buildSummary(InsightDataBundle bundle, String environment) {
        List<ApiCollection> assets = new ArrayList<>();
        List<Integer> deactivatedIds = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c == null) continue;
            if (c.isDeactivated()) deactivatedIds.add(c.getId());
            else assets.add(c);
        }
        List<ApiCollection> scoped = assetsIn(assets, environment);

        List<BasicDBObject> kpis = new ArrayList<>();
        kpis.add(assetsKpi(scoped, environment));
        kpis.add(highRiskAgentsKpi());
        kpis.add(identityAccessKpi());
        kpis.add(privilegedToolsKpi(scoped, environment, deactivatedIds));
        kpis.add(sensitiveDataKpi(scoped, bundle.sensitiveByCollection));
        kpis.add(protectionCoverageKpi(scoped, bundle.policies));

        BasicDBObject response = new BasicDBObject();
        response.put(KEY_ENVIRONMENTS, environments(countByEnvironment(assets)));
        response.put(KEY_KPIS, kpis);
        return response;
    }

    // ── Argus posture insight cards ──────────────────────────────────────────────────────────
    //
    // Five fixed cards, each a small, already-aggregated breakdown (never a raw event list) plus
    // an AI write-up generated/cached separately (see buildInsightCardSummaries) — the same
    // "fast Java data first, AI summary loaded after" split the rest of this page already uses.
    // No InsightProvider/Finding involved: these read testing_run_issues (via the DAO-level
    // AgentFindingGroup shape), vulnerable_testing_run_results/agent_conversation_results (for the
    // attack-flow card), and the threat-detection-backend's malicious_events directly, through
    // InsightService's own public passthroughs, and shape the result here — mirroring
    // buildSummary's own "pure function over pre-fetched data" style above.

    private static final LoggerMaker logger = new LoggerMaker(ArgusPostureService.class, LogDb.DASHBOARD);

    private static final String CARD_RED_TEAM_BREAKDOWN = "RED_TEAM_BREAKDOWN";
    private static final String CARD_ATTACK_FLOW = "ATTACK_FLOW_ANALYSIS";
    private static final String CARD_GUARDRAIL_BREAKDOWN = "GUARDRAIL_BREAKDOWN";
    private static final String CARD_GUARDRAIL_HOTSPOT = "GUARDRAIL_HOTSPOT";
    private static final String CARD_OBSERVABILITY = "OBSERVABILITY";

    private static final int BREAKDOWN_TOP_N = 10;
    private static final int TOPICS_CAP = 5;
    private static final int ATTACK_FLOW_ISSUE_COUNT = 2;

    private static final long CARD_NARRATIVE_TTL_DAYS = 7;
    private static final int CARD_NARRATIVE_VERSION = 2; // bumped: summary shape gained impact/recommendation

    // I/O-bound (LLM calls) — separate from any Mongo/ES-fetch executor, sized for up to 5
    // concurrent card summaries (4 regular + 1 attack-flow) so the async summaries endpoint's
    // total wait is ~max(one card's latency), not the sum of all five.
    private static final ExecutorService SUMMARY_EXECUTOR = Executors.newFixedThreadPool(5);
    private static final int SUMMARY_TIMEOUT_SECONDS = 30;

    private final ArgusInsightCardNarrativeHandler cardNarrativeHandler = new ArgusInsightCardNarrativeHandler();
    private final ArgusAttackFlowNarrativeHandler attackFlowHandler = new ArgusAttackFlowNarrativeHandler();

    /**
     * The 5 cards' Java-computed data — fast, no LLM call. `openIssueGroups` comes from
     * TestingRunIssuesDao#openIssueGroupsForDashboard, `criticalIssueConversations` from
     * conversationIdsForIssue/findValidatedSummaries scoped to just the 2 most critical open
     * issues (see pickTopCriticalIssues), `maliciousEvents` from
     * InsightService#fetchArgusMaliciousEvents, `serviceObservability`/`globalTopics` from
     * SearchClientFactory's fetchAgenticServiceObservability/fetchAgenticGlobalTopicHierarchy —
     * the caller (ArgusPostureAction) fetches all of these (in parallel), this method only shapes
     * them.
     */
    public List<BasicDBObject> buildInsightCards(InsightDataBundle bundle, List<AgentFindingGroup> openIssueGroups,
                                                  Map<String, AgentConversationResult> criticalIssueConversations,
                                                  List<DashboardMaliciousEvent> maliciousEvents,
                                                  List<UserAnalysisData> serviceObservability,
                                                  Map<String, Map<String, Integer>> globalTopics) {
        Map<Integer, ApiCollection> collectionsById = new HashMap<>();
        for (ApiCollection c : bundle.collections) {
            if (c != null) collectionsById.put(c.getId(), c);
        }

        RedTeamStats redTeam = computeRedTeamStats(openIssueGroups);
        GuardrailStats guardrail = computeGuardrailStats(maliciousEvents);
        List<AgentFindingGroup> topCritical = pickTopCriticalIssues(openIssueGroups, ATTACK_FLOW_ISSUE_COUNT);

        List<BasicDBObject> cards = new ArrayList<>();
        cards.add(redTeamBreakdownCard(redTeam, collectionsById));
        cards.add(attackFlowCard(topCritical, collectionsById, criticalIssueConversations));
        cards.add(guardrailBreakdownCard(guardrail, collectionsById));
        cards.add(guardrailHotspotCard(guardrail, collectionsById));
        cards.add(observabilityCard(serviceObservability, bundle, globalTopics));
        return cards;
    }

    /**
     * The account's most critical open issues, worst-first (severity, then count) — used both to
     * decide which conversationIds the caller needs to fetch (attack-flow grounding) and by
     * attackFlowCard itself. Pure/no I/O — safe to call before the caller has fetched anything
     * else.
     */
    public List<AgentFindingGroup> pickTopCriticalIssues(List<AgentFindingGroup> openIssueGroups, int n) {
        List<AgentFindingGroup> sorted = new ArrayList<>(safe(openIssueGroups));
        sorted.sort(Comparator
                .comparingInt((AgentFindingGroup g) -> InsightUtil.severityRank(g.getSecondary()))
                .thenComparing(Comparator.comparingLong(AgentFindingGroup::getCount).reversed()));
        return sorted.size() > n ? new ArrayList<>(sorted.subList(0, n)) : sorted;
    }

    /**
     * One AI write-up per card, keyed by card id — meant to be fetched asynchronously, after
     * buildInsightCards has already rendered, since a cache miss here is a real LLM round-trip
     * (see ArgusPostureAction#fetchArgusPostureInsightSummaries). Every card generates
     * concurrently (SUMMARY_EXECUTOR) so a cold cache doesn't serialize 5 LLM calls. Each regular
     * card's own "facts" array (added by its builder method) is exactly what gets shown to the
     * model; the attack-flow card instead carries an "issues" array with real conversation
     * grounding — see attackFlowCard/generateAttackFlowSummary.
     */
    public Map<String, BasicDBObject> buildInsightCardSummaries(InsightContext ctx, List<BasicDBObject> cards, boolean forceRefresh) {
        final int accountId = ctx.getAccountId();
        final Integer userId = ctx.getUserId();
        final CONTEXT_SOURCE contextSource = ctx.getContextSource();

        Map<String, Future<BasicDBObject>> futures = new LinkedHashMap<>();
        for (BasicDBObject card : cards) {
            String cardId = card.getString("id");
            if (cardId == null) continue;
            futures.put(cardId, SUMMARY_EXECUTOR.submit(withContext(accountId, userId, contextSource, () ->
                    CARD_ATTACK_FLOW.equals(cardId)
                            ? generateAttackFlowSummary(ctx, card, forceRefresh)
                            : generateCardSummary(ctx, cardId, card, forceRefresh))));
        }

        Map<String, BasicDBObject> summaries = new LinkedHashMap<>();
        for (Map.Entry<String, Future<BasicDBObject>> e : futures.entrySet()) {
            try {
                BasicDBObject result = e.getValue().get(SUMMARY_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (result != null) summaries.put(e.getKey(), result);
            } catch (Exception ex) {
                logger.error("Argus insight card summary timed out/failed for " + e.getKey() + ": " + ex.getMessage());
            }
        }
        return summaries;
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

    private BasicDBObject generateCardSummary(InsightContext ctx, String cardId, BasicDBObject card, boolean forceRefresh) {
        Object facts = card.get("facts");
        BasicDBObject narrativeInput = new BasicDBObject("facts", facts != null ? facts : new ArrayList<>());
        String fingerprint = cardFingerprint(ctx, cardId, narrativeInput);

        if (!forceRefresh) {
            InsightNarrativeCache cached = InsightNarrativeCacheDao.instance.get(fingerprint);
            if (cached != null) {
                return new BasicDBObject("summary", cached.getNarrativeMarkdown())
                        .append("impact", cached.getNarrativeImpact())
                        .append("recommendation", cached.getNarrativeRemediation());
            }
        }

        BasicDBObject input = new BasicDBObject(AbstractGroundedNarrativeHandler.NARRATIVE_INPUT, narrativeInput.toJson());
        BasicDBObject out = cardNarrativeHandler.handle(input);
        if (out.containsField("error")) {
            logger.error("Argus insight card summary failed for " + cardId + ": " + out.getString("error"));
            return null;
        }
        String summary = out.getString("summary");
        String impact = out.getString("impact");
        String recommendation = out.getString("recommendation");

        cacheNarrative(fingerprint, cardId, summary, impact, recommendation);
        return new BasicDBObject("summary", summary).append("impact", impact).append("recommendation", recommendation);
    }

    /**
     * The attack-flow card's summary is structurally different (a list of flows, not one
     * summary/impact/recommendation triad) — stored as a JSON blob in the shared cache's
     * narrativeMarkdown field (a plain String; no schema change needed) rather than reusing the
     * concern/impact/remediation columns, which don't fit a list shape.
     */
    private BasicDBObject generateAttackFlowSummary(InsightContext ctx, BasicDBObject card, boolean forceRefresh) {
        Object issues = card.get("issues");
        if (!(issues instanceof List) || ((List<?>) issues).isEmpty()) return null;

        BasicDBObject narrativeInput = new BasicDBObject("issues", issues);
        String fingerprint = cardFingerprint(ctx, CARD_ATTACK_FLOW, narrativeInput);

        if (!forceRefresh) {
            InsightNarrativeCache cached = InsightNarrativeCacheDao.instance.get(fingerprint);
            if (cached != null && cached.getNarrativeMarkdown() != null) {
                try {
                    return new BasicDBObject(Document.parse(cached.getNarrativeMarkdown()));
                } catch (Exception ignored) {
                    // A cached blob that fails to parse just regenerates below, same as a cache miss.
                }
            }
        }

        BasicDBObject input = new BasicDBObject(AbstractGroundedNarrativeHandler.NARRATIVE_INPUT, narrativeInput.toJson());
        BasicDBObject out = attackFlowHandler.handle(input);
        if (out.containsField("error")) {
            logger.error("Argus attack-flow summary failed: " + out.getString("error"));
            return null;
        }
        BasicDBObject result = new BasicDBObject("flows", out.get("flows"));
        cacheNarrative(fingerprint, CARD_ATTACK_FLOW, result.toJson(), null, null);
        return result;
    }

    private String cardFingerprint(InsightContext ctx, String cardId, BasicDBObject narrativeInput) {
        String raw = ctx.getAccountId() + "|" + ctx.getContextSource() + "|" + cardId + "|"
                + CARD_NARRATIVE_VERSION + "|" + narrativeInput.toJson();
        return InsightUtil.md5(raw);
    }

    private void cacheNarrative(String fingerprint, String cardId, String markdown, String impact, String remediation) {
        long now = System.currentTimeMillis() / 1000;
        InsightNarrativeCache cache = new InsightNarrativeCache(fingerprint, cardId, CARD_NARRATIVE_VERSION,
                markdown, null, impact, remediation, now, new Date((now + TimeUnit.DAYS.toSeconds(CARD_NARRATIVE_TTL_DAYS)) * 1000L));
        InsightNarrativeCacheDao.instance.put(cache);
    }

    // ── Red-team cards ────────────────────────────────────────────────────────────────────────

    private static final class RedTeamStats {
        long totalOpenIssues;
        final Map<String, Long> bySeverity = new LinkedHashMap<>();
        AgentFindingGroup topFinding; // the single {agent,vulnType} group with the highest count
        final Map<Integer, Long> byAgent = new HashMap<>();   // total open-issue count per agent
        final Map<String, Long> byType = new HashMap<>();     // total open-issue count per vuln type
    }

    private RedTeamStats computeRedTeamStats(List<AgentFindingGroup> openIssueGroups) {
        RedTeamStats stats = new RedTeamStats();
        for (String sev : new String[] { "CRITICAL", "HIGH", "MEDIUM", "LOW" }) stats.bySeverity.put(sev, 0L);
        for (AgentFindingGroup g : safe(openIssueGroups)) {
            if (g == null) continue;
            long count = g.getCount();
            stats.totalOpenIssues += count;
            String sev = g.getSecondary() != null ? g.getSecondary().toUpperCase(Locale.ROOT) : null;
            if (sev != null && stats.bySeverity.containsKey(sev)) stats.bySeverity.merge(sev, count, Long::sum);
            stats.byAgent.merge(g.getCollectionId(), count, Long::sum);
            if (g.getType() != null) stats.byType.merge(g.getType(), count, Long::sum);
            if (stats.topFinding == null || count > stats.topFinding.getCount()) stats.topFinding = g;
        }
        return stats;
    }

    private BasicDBObject redTeamBreakdownCard(RedTeamStats stats, Map<Integer, ApiCollection> collectionsById) {
        BasicDBObject card = card(CARD_RED_TEAM_BREAKDOWN, "Red-Team Issue Breakdown");
        card.put("totalOpenIssues", stats.totalOpenIssues);
        card.put("bySeverity", stats.bySeverity);
        card.put("cta", cta("view_issues", "Open issues", InsightRoutes.ISSUES));

        List<BasicDBObject> facts = new ArrayList<>();
        facts.add(fact("totalOpenIssues", "Open red-team issues", InsightUtil.grouped(stats.totalOpenIssues)));
        for (Map.Entry<String, Long> e : stats.bySeverity.entrySet()) {
            if (e.getValue() > 0) facts.add(fact("severity_" + e.getKey(), e.getKey() + " severity issues", InsightUtil.grouped(e.getValue())));
        }

        if (stats.topFinding != null) {
            String agentName = agentName(stats.topFinding.getCollectionId(), collectionsById);
            BasicDBObject top = new BasicDBObject("agentName", agentName)
                    .append("vulnType", stats.topFinding.getType())
                    .append("severity", stats.topFinding.getSecondary())
                    .append("count", stats.topFinding.getCount());
            card.put("topFinding", top);
            facts.add(fact("topFindingAgent", "Agent with the most common issue", agentName));
            facts.add(fact("topFindingType", "Most common issue type", stats.topFinding.getType()));
            facts.add(fact("topFindingCount", "Occurrences of that issue", InsightUtil.grouped(stats.topFinding.getCount())));
        } else {
            card.put("topFinding", null);
        }
        card.put("facts", facts);
        return card;
    }

    /**
     * "How Agents Were Compromised" — the account's ATTACK_FLOW_ISSUE_COUNT most critical open
     * issues, each grounded in a real validated red-team conversation (AgentConversationResult's
     * own validationMessage/remediationMessage — the human-judged outcome of an actual attempt),
     * not just aggregate counts. The AI step (generateAttackFlowSummary) turns each issue's real
     * verdict into a short ordered flow of what the attacker attempted and what actually happened
     * — never a raw request/response dump (see ArgusAttackFlowNarrativeHandler's own hard rules).
     * An issue with no real conversation behind it (no AgentConversationResult resolved) is
     * skipped — this card only ever narrates a REAL verdict, never a synthesized one.
     */
    private BasicDBObject attackFlowCard(List<AgentFindingGroup> topCritical, Map<Integer, ApiCollection> collectionsById,
                                          Map<String, AgentConversationResult> conversationsById) {
        BasicDBObject card = card(CARD_ATTACK_FLOW, "How Agents Were Compromised");
        card.put("cta", cta("view_issues", "Open issues", InsightRoutes.ISSUES));

        List<BasicDBObject> issues = new ArrayList<>();
        for (AgentFindingGroup g : safe(topCritical)) {
            if (g == null || g.getSample() == null) continue;
            AgentConversationResult conversation = firstResolved(g.getSample(), conversationsById);
            if (conversation == null || conversation.getValidationMessage() == null) continue;

            String agentName = agentName(g.getCollectionId(), collectionsById);
            issues.add(new BasicDBObject("agentName", agentName)
                    .append("vulnType", g.getType())
                    .append("severity", g.getSecondary())
                    .append("conversationId", conversation.getConversationId())
                    .append("validationMessage", conversation.getValidationMessage())
                    .append("remediationMessage", conversation.getRemediationMessage()));
        }
        card.put("issues", issues);

        // A lightweight, immediately-visible preview (agent/type/severity only) — the real flow
        // narrative arrives async via buildInsightCardSummaries/generateAttackFlowSummary.
        List<BasicDBObject> preview = new ArrayList<>();
        for (BasicDBObject issue : issues) {
            preview.add(new BasicDBObject("agentName", issue.getString("agentName"))
                    .append("vulnType", issue.getString("vulnType"))
                    .append("severity", issue.getString("severity")));
        }
        card.put("issuePreview", preview);
        return card;
    }

    private AgentConversationResult firstResolved(List<String> conversationIds, Map<String, AgentConversationResult> conversationsById) {
        for (String id : conversationIds) {
            AgentConversationResult c = conversationsById.get(id);
            if (c != null) return c;
        }
        return null;
    }

    // ── Guardrail cards ───────────────────────────────────────────────────────────────────────

    private static final class GuardrailStats {
        long totalEvents;
        final Map<String, Long> byPolicy = new HashMap<>();   // DashboardMaliciousEvent#getFilterId()
        final Map<Integer, Long> byAgent = new HashMap<>();   // DashboardMaliciousEvent#getApiCollectionId()
    }

    /**
     * filterId is used as the policy label directly — the same convention
     * PostureService.criticalAlertsDrill already uses (row("policy", e.getFilterId())) — rather
     * than joining to GuardrailPolicies, since a malicious event's filterId is not reliably a
     * GuardrailPolicies._id (see MaliciousEventDto's own contextSource/filter-id scoping).
     */
    private GuardrailStats computeGuardrailStats(List<DashboardMaliciousEvent> events) {
        GuardrailStats stats = new GuardrailStats();
        for (DashboardMaliciousEvent e : safe(events)) {
            if (e == null) continue;
            stats.totalEvents++;
            if (StringUtils.isNotBlank(e.getFilterId())) stats.byPolicy.merge(e.getFilterId(), 1L, Long::sum);
            if (e.getApiCollectionId() != 0) stats.byAgent.merge(e.getApiCollectionId(), 1L, Long::sum);
        }
        return stats;
    }

    private BasicDBObject guardrailBreakdownCard(GuardrailStats stats, Map<Integer, ApiCollection> collectionsById) {
        BasicDBObject card = card(CARD_GUARDRAIL_BREAKDOWN, "Guardrail Activity Breakdown");
        card.put("cta", cta("view_activity", "View guardrail activity", InsightRoutes.GUARDRAIL_ACTIVITY));
        card.put("totalEvents", stats.totalEvents);
        card.put("byPolicy", topNByString(stats.byPolicy, "policy", BREAKDOWN_TOP_N));
        card.put("byAgent", topNByAgent(stats.byAgent, collectionsById, BREAKDOWN_TOP_N));

        List<BasicDBObject> facts = new ArrayList<>();
        facts.add(fact("totalEvents", "Guardrail/malicious events", InsightUtil.grouped(stats.totalEvents)));
        int rank = 0;
        for (Map.Entry<String, Long> e : topEntriesByString(stats.byPolicy, 3)) {
            facts.add(fact("policy_" + (rank++), "Events under policy " + e.getKey(), InsightUtil.grouped(e.getValue())));
        }
        rank = 0;
        for (Map.Entry<Integer, Long> e : topEntriesByAgent(stats.byAgent, 3)) {
            facts.add(fact("agent_" + (rank++), "Events on " + agentName(e.getKey(), collectionsById), InsightUtil.grouped(e.getValue())));
        }
        card.put("facts", facts);
        return card;
    }

    private BasicDBObject guardrailHotspotCard(GuardrailStats stats, Map<Integer, ApiCollection> collectionsById) {
        BasicDBObject card = card(CARD_GUARDRAIL_HOTSPOT, "Where Guardrail Activity Concentrates");
        card.put("cta", cta("view_policies", "Review guardrail policies", InsightRoutes.GUARDRAIL_POLICIES));
        List<BasicDBObject> facts = new ArrayList<>();

        Integer hottestAgentId = maxKey(stats.byAgent);
        if (hottestAgentId != null) {
            String agentName = agentName(hottestAgentId, collectionsById);
            long count = stats.byAgent.get(hottestAgentId);
            card.put("hottestAgent", new BasicDBObject("agentName", agentName).append("count", count));
            facts.add(fact("hottestAgent", "Agent generating the most guardrail activity", agentName));
            facts.add(fact("hottestAgentCount", "Events on that agent", InsightUtil.grouped(count)));
        } else {
            card.put("hottestAgent", null);
        }

        String hottestPolicy = maxKey(stats.byPolicy);
        if (hottestPolicy != null) {
            long count = stats.byPolicy.get(hottestPolicy);
            card.put("hottestPolicy", new BasicDBObject("policy", hottestPolicy).append("count", count));
            facts.add(fact("hottestPolicy", "Most-triggered policy", hottestPolicy));
            facts.add(fact("hottestPolicyCount", "Times that policy triggered", InsightUtil.grouped(count)));
        } else {
            card.put("hottestPolicy", null);
        }
        card.put("facts", facts);
        return card;
    }

    // ── Observability card ────────────────────────────────────────────────────────────────────

    private BasicDBObject observabilityCard(List<UserAnalysisData> serviceObservability, InsightDataBundle bundle,
                                             Map<String, Map<String, Integer>> globalTopics) {
        BasicDBObject card = card(CARD_OBSERVABILITY, "Agent Token Usage & Topics");
        card.put("cta", cta("view_observability", "View LLM observability", InsightRoutes.LLM_OBSERVABILITY));
        List<BasicDBObject> facts = new ArrayList<>();

        long totalTokens = 0;
        UserAnalysisData hottest = null;
        for (UserAnalysisData row : safe(serviceObservability)) {
            if (row == null) continue;
            long tokens = row.getTotalInputTokens() + row.getTotalOutputTokens();
            totalTokens += tokens;
            if (hottest == null || tokens > (hottest.getTotalInputTokens() + hottest.getTotalOutputTokens())) hottest = row;
        }
        card.put("totalTokens", totalTokens);
        facts.add(fact("totalTokens", "Tokens used by agents this window", InsightUtil.grouped(totalTokens)));

        if (hottest != null && hottest.getId() != null && hottest.getId().getServiceId() != null) {
            List<ApiCollection> matches = bundle.collectionsForServiceName(hottest.getId().getServiceId());
            String agentName = matches.isEmpty() ? hottest.getId().getServiceId() : matches.get(0).getName();
            long hottestTokens = hottest.getTotalInputTokens() + hottest.getTotalOutputTokens();
            card.put("hottestAgent", new BasicDBObject("agentName", agentName).append("tokens", hottestTokens));
            facts.add(fact("hottestAgent", "Agent with the most token usage", agentName));
            facts.add(fact("hottestAgentTokens", "Tokens used by that agent", InsightUtil.grouped(hottestTokens)));
        } else {
            card.put("hottestAgent", null);
        }

        List<BasicDBObject> topTopics = new ArrayList<>();
        int rank = 0;
        for (Map.Entry<String, Map<String, Integer>> e : safeMap(globalTopics).entrySet()) {
            if (rank >= TOPICS_CAP) break;
            long topicCount = 0;
            List<BasicDBObject> subTopics = new ArrayList<>();
            for (Map.Entry<String, Integer> sub : e.getValue().entrySet()) {
                topicCount += sub.getValue();
                subTopics.add(new BasicDBObject("subTopic", sub.getKey()).append("count", sub.getValue()));
            }
            topTopics.add(new BasicDBObject("topic", e.getKey()).append("count", topicCount).append("subTopics", subTopics));
            facts.add(fact("topic_" + rank, "Topic \"" + e.getKey() + "\"", InsightUtil.grouped(topicCount)));
            rank++;
        }
        card.put("topTopics", topTopics);
        card.put("facts", facts);
        return card;
    }

    // ── Shared card helpers ───────────────────────────────────────────────────────────────────

    private static BasicDBObject card(String id, String title) {
        return new BasicDBObject("id", id).append("title", title);
    }

    private static BasicDBObject fact(String key, String label, Object formatted) {
        return new BasicDBObject("key", key).append("label", label).append("formatted", formatted);
    }

    /** A Java-determined (never AI-generated) deep link — same {id,label,route} shape the old
     *  Finding.Cta used, so a route is always a real InsightRoutes constant, never something the
     *  model invented. */
    private static BasicDBObject cta(String id, String label, String route) {
        return new BasicDBObject("id", id).append("label", label).append("route", route);
    }

    private static String agentName(Integer collectionId, Map<Integer, ApiCollection> collectionsById) {
        if (collectionId == null) return null;
        ApiCollection c = collectionsById.get(collectionId);
        return c != null ? c.getName() : null;
    }

    /** Highest-value key, or null when the map is empty. Ties keep the first key iterated. */
    private static <K> K maxKey(Map<K, Long> counts) {
        K best = null;
        long bestValue = Long.MIN_VALUE;
        for (Map.Entry<K, Long> e : counts.entrySet()) {
            if (e.getValue() > bestValue) { best = e.getKey(); bestValue = e.getValue(); }
        }
        return best;
    }

    private static List<Map.Entry<String, Long>> topEntriesByString(Map<String, Long> counts, int n) {
        List<Map.Entry<String, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return entries.size() > n ? entries.subList(0, n) : entries;
    }

    private static List<Map.Entry<Integer, Long>> topEntriesByAgent(Map<Integer, Long> counts, int n) {
        List<Map.Entry<Integer, Long>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return entries.size() > n ? entries.subList(0, n) : entries;
    }

    private static List<BasicDBObject> topNByString(Map<String, Long> counts, String labelKey, int n) {
        List<BasicDBObject> out = new ArrayList<>();
        for (Map.Entry<String, Long> e : topEntriesByString(counts, n)) {
            out.add(new BasicDBObject(labelKey, e.getKey()).append("count", e.getValue()));
        }
        return out;
    }

    private static List<BasicDBObject> topNByAgent(Map<Integer, Long> counts, Map<Integer, ApiCollection> collectionsById, int n) {
        List<BasicDBObject> out = new ArrayList<>();
        for (Map.Entry<Integer, Long> e : topEntriesByAgent(counts, n)) {
            out.add(new BasicDBObject("agentName", agentName(e.getKey(), collectionsById)).append("count", e.getValue()));
        }
        return out;
    }

    private static <T> List<T> safe(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    private static Map<String, Map<String, Integer>> safeMap(Map<String, Map<String, Integer>> map) {
        return map != null ? map : new LinkedHashMap<>();
    }

    private BasicDBObject assetsKpi(List<ApiCollection> assets, String environment) {
        BasicDBObject kpi = kpi(KPI_ASSETS, "Assets", (long) assets.size());

        if (isAllEnvironments(environment)) {
            long production = 0;
            for (ApiCollection asset : assets) {
                if (ENV_PRODUCTION.equals(envBucket(envTagValue(asset)))) production++;
            }
            kpi.put("footnote", countLine(production, "production", "None in production"));
        }

        long externallyExposed = 0;
        kpi.put("secondaryFootnote", countLine(externallyExposed, "externally exposed", "None externally exposed"));
        kpi.put("secondaryTone", riskTone(externallyExposed, "warning"));
        return kpi;
    }

    private BasicDBObject highRiskAgentsKpi() {
        BasicDBObject kpi = kpi(KPI_HIGH_RISK_AGENTS, "High-Risk Agents", 0L);
        long newlyHighRisk = 0;
        kpi.put("secondaryFootnote", countLine(newlyHighRisk, "newly high risk this week", "No change since last week"));
        kpi.put("secondaryTone", riskTone(newlyHighRisk, "critical"));
        return kpi;
    }

    private BasicDBObject identityAccessKpi() {
        BasicDBObject kpi = kpi(KPI_IDENTITY_ACCESS, "Identity & Access", 0L);
        kpi.put("footnote", "overprivileged identity(s)");
        kpi.put("secondaryFootnote", sharedOrphanedLine(0, 0));
        kpi.put("secondaryTone", "subdued");
        return kpi;
    }

    private BasicDBObject privilegedToolsKpi(List<ApiCollection> assets, String environment,
                                             List<Integer> deactivatedIds) {
        long privileged = 0;
        long destructive = 0;

        if (!assets.isEmpty()) {
            Bson inScope = scopeFilter(assets, environment, deactivatedIds);

            privileged = ApiInfoDao.instance.count(Filters.and(inScope,
                    Filters.exists(ApiInfo.TOOL_INFO_CAPABILITY),
                    Filters.ne(ApiInfo.TOOL_INFO_CAPABILITY, ToolCapabilityClassifier.SAFE)));

            destructive = ApiInfoDao.instance.count(Filters.and(inScope,
                    Filters.in(ApiInfo.TOOL_INFO_CAPABILITY,
                            ToolCapabilityClassifier.RESOURCE_DELETE,
                            ToolCapabilityClassifier.CRITICAL_RESOURCE_WRITE)));
        }

        BasicDBObject kpi = kpi(KPI_PRIVILEGED_TOOLS, "Privileged Tools", privileged);
        kpi.put("footnote", "privileged");
        kpi.put("secondaryFootnote", countLine(destructive, "destructive", "None destructive"));
        kpi.put("secondaryTone", riskTone(destructive, "critical"));
        return kpi;
    }

    private static Bson scopeFilter(List<ApiCollection> assets, String environment,
                                    List<Integer> deactivatedIds) {
        if (isAllEnvironments(environment)) {
            if (deactivatedIds.isEmpty()) return Filters.empty();
            return Filters.nin(ApiInfo.ID_API_COLLECTION_ID, deactivatedIds);
        }

        List<Integer> ids = new ArrayList<>(assets.size());
        for (ApiCollection asset : assets) ids.add(asset.getId());
        return Filters.in(ApiInfo.ID_API_COLLECTION_ID, ids);
    }

    private BasicDBObject sensitiveDataKpi(List<ApiCollection> assets,
                                           Map<Integer, List<String>> sensitiveByCollection) {
        long withSensitive = 0;
        for (ApiCollection asset : assets) {
            List<String> types = sensitiveByCollection.get(asset.getId());
            if (types != null && !types.isEmpty()) withSensitive++;
        }

        BasicDBObject kpi = kpi(KPI_SENSITIVE_DATA, "Sensitive Data", withSensitive);
        kpi.put("footnote", "asset(s) access sensitive data");
        long canSendExternally = 0;
        kpi.put("secondaryFootnote", countLine(canSendExternally, "can send it externally", "None can send it externally"));
        kpi.put("secondaryTone", riskTone(canSendExternally, "critical"));
        return kpi;
    }

    private BasicDBObject protectionCoverageKpi(List<ApiCollection> assets, List<GuardrailPolicies> policies) {
        BasicDBObject kpi = kpi(KPI_PROTECTION_COVERAGE, "Protection Coverage", 0L);
        kpi.put("unit", "percent");
        kpi.put("tone", toneForPercent(0d));

        if (assets.isEmpty()) {
            kpi.put("value", 0d);
            kpi.put("secondaryFootnote", "No asset(s) discovered");
            kpi.put("secondaryTone", toneForPercent(0d));
            return kpi;
        }

        if (hasFleetWidePolicy(policies)) {
            kpi.put("value", 100d);
            kpi.put("tone", toneForPercent(100d));
            kpi.put("secondaryFootnote", "All asset(s) covered");
            kpi.put("secondaryTone", toneForPercent(100d));
            return kpi;
        }

        long covered = 0;
        for (ApiCollection asset : assets) {
            if (isCovered(asset, policies)) covered++;
        }

        long notCovered = assets.size() - covered;
        double percent = percentOf(covered, assets.size());

        kpi.put("value", percent);
        kpi.put("tone", toneForPercent(percent));
        kpi.put("secondaryFootnote", countLine(notCovered, "asset(s) not covered", "All asset(s) covered"));
        kpi.put("secondaryTone", toneForPercent(percent));
        return kpi;
    }

    private static String countLine(long count, String whenSome, String whenNone) {
        return count > 0 ? count + " " + whenSome : whenNone;
    }

    private static String sharedOrphanedLine(long shared, long orphaned) {
        if (shared == 0 && orphaned == 0) return "No shared or orphaned identity(s)";
        if (orphaned == 0) return shared + " shared";
        if (shared == 0) return orphaned + " orphaned";
        return shared + " shared · " + orphaned + " orphaned";
    }

    private static boolean hasFleetWidePolicy(List<GuardrailPolicies> policies) {
        for (GuardrailPolicies p : policies) {
            if (p != null && p.isApplyToAllServers()) return true;
        }
        return false;
    }

    private static boolean isCovered(ApiCollection asset, List<GuardrailPolicies> policies) {
        return InsightUtil.collectionCoveredByAnyPolicy(asset, policies);
    }

    private static String envTagValue(ApiCollection c) {
        return InsightUtil.envTagValue(c);
    }

    private static List<ApiCollection> assetsIn(List<ApiCollection> assets, String environment) {
        if (isAllEnvironments(environment)) return assets;

        String bucket = bucketForId(environment);
        if (bucket == null) return assets;

        List<ApiCollection> out = new ArrayList<>();
        for (ApiCollection asset : assets) {
            if (bucket.equals(envBucket(envTagValue(asset)))) out.add(asset);
        }
        return out;
    }

    private static Map<String, Integer> countByEnvironment(List<ApiCollection> assets) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ApiCollection asset : assets) {
            String bucket = envBucket(envTagValue(asset));
            counts.put(bucket, counts.getOrDefault(bucket, 0) + 1);
        }
        return counts;
    }

    private static String bucketForId(String environment) {
        if (StringUtils.isBlank(environment)) return null;
        switch (environment.trim().toLowerCase(Locale.ROOT)) {
            case ENV_ID_PRODUCTION:
                return ENV_PRODUCTION;
            case ENV_ID_STAGING:
                return ENV_STAGING;
            case ENV_ID_DEVELOPMENT:
                return ENV_DEVELOPMENT;
            default:
                return null;
        }
    }

    public static String envBucket(String envTagValue) {
        return InsightUtil.environmentBucket(envTagValue);
    }

    private static boolean isAllEnvironments(String environment) {
        return StringUtils.isBlank(environment) || ENV_ID_ALL.equalsIgnoreCase(environment.trim());
    }

    public static Bson filterForEnvironment(String environment) {
        if (StringUtils.isBlank(environment)) return Filters.empty();
        switch (environment.trim().toLowerCase(Locale.ROOT)) {
            case ENV_ID_DEVELOPMENT:
                return envTagIn(DEV_ENVS);
            case ENV_ID_STAGING:
                return envTagIn(STAGING_ENVS);
            case ENV_ID_PRODUCTION:
                List<String> nonProd = new ArrayList<>(DEV_ENVS);
                nonProd.addAll(STAGING_ENVS);
                return Filters.nor(envTagIn(nonProd));
            default:
                return Filters.empty();
        }
    }

    private static Bson envTagIn(List<String> values) {
        List<Pattern> patterns = new ArrayList<>(values.size());
        for (String value : values) {
            patterns.add(Pattern.compile("^" + Pattern.quote(value) + "$", Pattern.CASE_INSENSITIVE));
        }
        return Filters.elemMatch(ApiCollection.TAGS_STRING,
                Filters.and(
                        Filters.eq(CollectionTags.KEY_NAME, Constants.AKTO_ENV_TYPE_TAG),
                        Filters.in(CollectionTags.VALUE, patterns)));
    }

    private static BasicDBObject kpi(String id, String label, Long value) {
        BasicDBObject kpi = new BasicDBObject();
        kpi.put("id", id);
        kpi.put("label", label);
        kpi.put("value", value);
        return kpi;
    }

    private static List<BasicDBObject> environments(Map<String, Integer> counts) {
        List<BasicDBObject> out = new ArrayList<>();
        out.add(environment(ENV_ID_ALL, "All environments", null));
        out.add(environment(ENV_ID_PRODUCTION, ENV_PRODUCTION, counts.getOrDefault(ENV_PRODUCTION, 0)));
        out.add(environment(ENV_ID_STAGING, ENV_STAGING, counts.getOrDefault(ENV_STAGING, 0)));
        out.add(environment(ENV_ID_DEVELOPMENT, ENV_DEVELOPMENT, counts.getOrDefault(ENV_DEVELOPMENT, 0)));
        return out;
    }

    private static BasicDBObject environment(String id, String label, Integer count) {
        BasicDBObject env = new BasicDBObject();
        env.put("id", id);
        env.put("label", label);
        env.put("count", count);
        return env;
    }

    private static double percentOf(long part, long total) {
        if (total <= 0) return 0d;
        return Math.floor((part * 1000d) / total) / 10d;
    }

    private static String riskTone(long count, String toneWhenPresent) {
        return count > 0 ? toneWhenPresent : "subdued";
    }

    private static String toneForPercent(double percent) {
        if (percent >= TONE_SUCCESS_AT) return "success";
        if (percent >= TONE_WARNING_AT) return "warning";
        return "critical";
    }
}
