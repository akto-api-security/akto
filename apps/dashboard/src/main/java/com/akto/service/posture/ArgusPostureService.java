package com.akto.service.posture;

import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.insights.InsightNarrativeCacheDao;
import com.akto.dto.AgenticPostureScoreHistory;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.insights.InsightNarrativeCache;
import com.akto.dto.insights.agentic.AgentFindingGroup;
import com.akto.dto.test_editor.Info;
import com.akto.dto.testing.AgentConversationResult;
import com.akto.dto.traffic.CollectionTags;
import com.akto.gpt.handlers.gpt_prompts.AbstractGroundedNarrativeHandler;
import com.akto.gpt.handlers.gpt_prompts.ArgusAttackFlowNarrativeHandler;
import com.akto.gpt.handlers.gpt_prompts.ArgusInsightCardNarrativeHandler;
import com.akto.gpt.handlers.gpt_prompts.ToolCapabilityClassifier;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.*;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.crons.ToolClassificationCron;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.*;
import java.util.concurrent.*;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ArgusPostureService {

    private static final String UNKNOWN_AGENT = "Unknown agent";

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

    public static final String DRILL_PROTECTION_COVERAGE = KPI_PROTECTION_COVERAGE;
    public static final String DRILL_PRIVILEGED_TOOLS = KPI_PRIVILEGED_TOOLS;
    public static final String DRILL_SENSITIVE_DATA = KPI_SENSITIVE_DATA;
    private static final int SENSITIVE_DATA_WINDOW_SECONDS = 90 * 86400;


    private static final String PROTECTION_NONE = "None";
    private static final String PROTECTION_ALERT_ONLY = "Alert only";

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
        kpis.add(highRiskAgentsKpi(scoped));
        kpis.add(identityAccessKpi());
        kpis.add(privilegedToolsKpi(scoped, environment, deactivatedIds));
        kpis.add(sensitiveDataKpi(scoped, sensitiveViolationsByAsset(bundle, scoped)));
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

    // ── Insight card drilldowns ───────────────────────────────────────────────────────────────
    //
    // One flyout per card (reusing PostureDrillFlyout/PostureDrillResult/PostureDrillNarrativeService
    // wholesale, per the package CLAUDE.md's own "no drill-down for the Argus insight cards" open
    // work item — no second mechanism invented). Root level only, single table, no member level:
    // every card's own real rows already fit in one page, unlike the 5 endpoint-posture panels'
    // two-level group->member drills. RED_TEAM_BREAKDOWN and ATTACK_FLOW_ANALYSIS share
    // DRILL_RED_TEAM_ISSUES (both are "the account's open red-team issues", just summarized
    // differently); GUARDRAIL_BREAKDOWN and GUARDRAIL_HOTSPOT share DRILL_GUARDRAIL_EVENTS likewise.
    public static final String DRILL_RED_TEAM_ISSUES = "argusRedTeamIssues";
    public static final String DRILL_GUARDRAIL_EVENTS = "argusGuardrailEvents";
    public static final String DRILL_OBSERVABILITY = "argusObservability";
    private static final int DRILL_ROW_CAP = 50;
    private static final int DEFAULT_DRILL_LIMIT = 20;

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
                                                  Map<String, Info> testInfoByType,
                                                  List<DashboardMaliciousEvent> maliciousEvents,
                                                  List<UserAnalysisData> serviceObservability,
                                                  Map<String, Map<String, Integer>> globalTopics) {
        Map<Integer, ApiCollection> collectionsById = new HashMap<>();
        for (ApiCollection c : bundle.collections) {
            if (c != null) collectionsById.put(c.getId(), c);
        }
        Map<String, GuardrailPolicies> policiesByName = new HashMap<>();
        for (GuardrailPolicies p : safe(bundle.policies)) {
            if (p != null && p.getName() != null) policiesByName.put(p.getName().toLowerCase(Locale.ROOT), p);
        }

        RedTeamStats redTeam = computeRedTeamStats(openIssueGroups);
        GuardrailStats guardrail = computeGuardrailStats(maliciousEvents, new HostCollectionResolver(bundle.collections));
        List<AgentFindingGroup> topCritical = pickTopCriticalIssues(openIssueGroups, ATTACK_FLOW_ISSUE_COUNT);

        List<BasicDBObject> cards = new ArrayList<>();
        cards.add(redTeamBreakdownCard(redTeam, topCritical, collectionsById, criticalIssueConversations, testInfoByType));
        cards.add(guardrailBreakdownCard(guardrail, collectionsById, policiesByName));
        cards.add(guardrailHotspotCard(guardrail, collectionsById, policiesByName));
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
            futures.put(cardId, SUMMARY_EXECUTOR.submit(Context.withContext(accountId, userId, contextSource, () ->
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

    private BasicDBObject generateCardSummary(InsightContext ctx, String cardId, BasicDBObject card, boolean forceRefresh) {
        Object facts = card.get("facts");
        Object context = card.get("context");
        BasicDBObject narrativeInput = new BasicDBObject("facts", facts != null ? facts : new ArrayList<>())
                .append("context", context != null ? context : new ArrayList<>());
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

    private BasicDBObject redTeamBreakdownCard(RedTeamStats stats, List<AgentFindingGroup> topCritical,
                                                Map<Integer, ApiCollection> collectionsById,
                                                Map<String, AgentConversationResult> conversationsById,
                                                Map<String, Info> testInfoByType) {
        BasicDBObject card = card(CARD_RED_TEAM_BREAKDOWN, "Red-Team Issue Breakdown");
        card.put("totalOpenIssues", stats.totalOpenIssues);
        card.put("bySeverity", stats.bySeverity);
        card.put("cta", cta("view_issues", "Open issues", InsightRoutes.ISSUES));
        card.put("drillId", DRILL_RED_TEAM_ISSUES);

        List<BasicDBObject> facts = new ArrayList<>();
        facts.add(fact("totalOpenIssues", "Open red-team issues", InsightUtil.grouped(stats.totalOpenIssues)));
        for (Map.Entry<String, Long> e : stats.bySeverity.entrySet()) {
            if (e.getValue() > 0) facts.add(fact("severity_" + e.getKey(), e.getKey() + " severity issues", InsightUtil.grouped(e.getValue())));
        }

        if (stats.topFinding != null) {
            String agentName = agentName(stats.topFinding.getCollectionId(), collectionsById);
            String vulnType = testDisplayName(stats.topFinding.getType(), testInfoByType);
            BasicDBObject top = new BasicDBObject("agentName", agentName)
                    .append("vulnType", vulnType)
                    .append("severity", stats.topFinding.getSecondary())
                    .append("count", stats.topFinding.getCount());
            card.put("topFinding", top);
            facts.add(fact("topFindingAgent", "Agent with the most common issue", agentName));
            facts.add(fact("topFindingType", "Most common issue type", vulnType));
            facts.add(fact("topFindingCount", "Occurrences of that issue", InsightUtil.grouped(stats.topFinding.getCount())));
        } else {
            card.put("topFinding", null);
        }

        // WHY this matters: the account's single most CRITICAL open issue (not just the most
        // common one above) — grounded in the test template's own real name/description/impact
        // (YamlTemplate#getInfo(), resolved by ArgusPostureAction#fetchTestInfo) and, when a real
        // validated conversation exists for it, that conversation's own validationMessage. This is
        // exactly the "why is this vulnerability type severe" reasoning a bare count can't carry.
        card.put("context", mostCriticalIssueContext(topCritical, collectionsById, conversationsById, testInfoByType));
        card.put("facts", facts);
        return card;
    }

    /** Shared by redTeamBreakdownCard and attackFlowCard: the real template info + (when present) a
     *  real validated conversation for the single most critical open issue — never invented. */
    private List<BasicDBObject> mostCriticalIssueContext(List<AgentFindingGroup> topCritical,
                                                          Map<Integer, ApiCollection> collectionsById,
                                                          Map<String, AgentConversationResult> conversationsById,
                                                          Map<String, Info> testInfoByType) {
        List<BasicDBObject> context = new ArrayList<>();
        if (safe(topCritical).isEmpty()) return context;
        AgentFindingGroup worst = topCritical.get(0);
        String agentName = agentName(worst.getCollectionId(), collectionsById);

        Info info = testInfoByType.get(worst.getType());
        if (info != null) {
            String label = testDisplayName(worst.getType(), testInfoByType);
            StringBuilder text = new StringBuilder("Most critical open issue — ").append(label)
                    .append(" on ").append(agentName).append(" (").append(worst.getSecondary()).append("). ");
            if (info.getDescription() != null) text.append(info.getDescription()).append(" ");
            if (info.getImpact() != null) text.append("Real-world impact: ").append(info.getImpact());
            context.add(new BasicDBObject("key", "mostCriticalIssueTemplate").append("text", text.toString()));
        }

        AgentConversationResult conversation = worst.getSample() != null ? firstResolved(worst.getSample(), conversationsById) : null;
        if (conversation != null && conversation.getValidationMessage() != null) {
            context.add(new BasicDBObject("key", "mostCriticalIssueValidated")
                    .append("text", "Validated red-team outcome on " + agentName + ": " + conversation.getValidationMessage()));
        }
        return context;
    }

    private static String testDisplayName(String type, Map<String, Info> testInfoByType) {
        Info info = testInfoByType.get(type);
        return info != null && info.getName() != null ? info.getName() : type;
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
        final Map<Integer, Long> byAgent = new HashMap<>();   // collection resolved from the event's host, then actor
    }

    /**
     * filterId IS the guardrail policy's real name (confirmed: GuardrailPolicyReplayAction's own
     * javadoc — "a guardrail event's filterId is the policy name"; also
     * ThreatDetectionBackendClient's own "filterIds guardrail policy names (== event filterId)").
     * So it doubles as both the display label (same convention PostureService.criticalAlertsDrill
     * already uses: row("policy", e.getFilterId())) and the join key back to the real
     * GuardrailPolicies document — see resolvePolicy.
     */
    private GuardrailStats computeGuardrailStats(List<DashboardMaliciousEvent> events, HostCollectionResolver resolver) {
        GuardrailStats stats = new GuardrailStats();
        for (DashboardMaliciousEvent e : safe(events)) {
            if (e == null) continue;
            stats.totalEvents++;
            if (StringUtils.isNotBlank(e.getFilterId())) stats.byPolicy.merge(e.getFilterId(), 1L, Long::sum);
            Integer agentId = agentIdForEvent(e, resolver);
            if (agentId != null) stats.byAgent.merge(agentId, 1L, Long::sum);
        }
        return stats;
    }

    private GuardrailPolicies resolvePolicy(String filterId, Map<String, GuardrailPolicies> policiesByName) {
        return filterId == null ? null : policiesByName.get(filterId.toLowerCase(Locale.ROOT));
    }

    /** Adds each row's own real, resolved policy severity (for the frontend's SeverityBadge) — a
     *  policy name with no resolvable GuardrailPolicies document (e.g. a stale/deleted policy)
     *  just gets a null severity, never a guessed one. */
    private void attachPolicySeverity(List<BasicDBObject> policyRows, String nameKey, Map<String, GuardrailPolicies> policiesByName) {
        for (BasicDBObject row : policyRows) {
            GuardrailPolicies resolved = resolvePolicy(row.getString(nameKey), policiesByName);
            row.put("severity", resolved != null ? resolved.getSeverity() : null);
        }
    }

    /** A one-line real summary of what a guardrail policy is actually configured to do — behaviour
     *  (block/warn/alert/approval) is the single most impact-relevant fact: a "warn"-only policy
     *  lets violations proceed, a "block" policy stops them. */
    private String policyContextText(GuardrailPolicies policy) {
        StringBuilder text = new StringBuilder("Policy \"").append(policy.getName()).append("\" behaviour: ")
                .append(policy.getBehaviour() != null ? policy.getBehaviour() : "unspecified").append(".");
        if (policy.getSeverity() != null) text.append(" Configured severity: ").append(policy.getSeverity()).append(".");
        if (StringUtils.isNotBlank(policy.getDescription())) text.append(" ").append(policy.getDescription());
        if (policy.getDeniedTopics() != null && !policy.getDeniedTopics().isEmpty()) {
            text.append(" Denied topics: ");
            boolean first = true;
            for (GuardrailPolicies.DeniedTopic t : policy.getDeniedTopics()) {
                if (t == null || t.getTopic() == null) continue;
                if (!first) text.append(", ");
                text.append(t.getTopic());
                first = false;
            }
            text.append(".");
        }
        return text.toString();
    }

    private BasicDBObject guardrailBreakdownCard(GuardrailStats stats, Map<Integer, ApiCollection> collectionsById,
                                                  Map<String, GuardrailPolicies> policiesByName) {
        BasicDBObject card = card(CARD_GUARDRAIL_BREAKDOWN, "Guardrail Activity Breakdown");
        card.put("cta", cta("view_activity", "View guardrail activity", InsightRoutes.GUARDRAIL_ACTIVITY));
        card.put("drillId", DRILL_GUARDRAIL_EVENTS);
        card.put("totalEvents", stats.totalEvents);
        List<BasicDBObject> byPolicy = topNByString(stats.byPolicy, "policy", BREAKDOWN_TOP_N);
        attachPolicySeverity(byPolicy, "policy", policiesByName);
        card.put("byPolicy", byPolicy);
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

        List<BasicDBObject> context = new ArrayList<>();
        List<Map.Entry<String, Long>> topPolicies = topEntriesByString(stats.byPolicy, 3);
        if (!topPolicies.isEmpty()) {
            GuardrailPolicies topPolicy = resolvePolicy(topPolicies.get(0).getKey(), policiesByName);
            if (topPolicy != null) {
                context.add(new BasicDBObject("key", "topPolicyConfig").append("text", policyContextText(topPolicy)));
            }
        }
        card.put("context", context);
        return card;
    }

    private BasicDBObject guardrailHotspotCard(GuardrailStats stats, Map<Integer, ApiCollection> collectionsById,
                                                Map<String, GuardrailPolicies> policiesByName) {
        BasicDBObject card = card(CARD_GUARDRAIL_HOTSPOT, "Where Guardrail Activity Concentrates");
        card.put("cta", cta("view_policies", "Review guardrail policies", InsightRoutes.GUARDRAIL_POLICIES));
        card.put("drillId", DRILL_GUARDRAIL_EVENTS);
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
        List<BasicDBObject> context = new ArrayList<>();
        if (hottestPolicy != null) {
            long count = stats.byPolicy.get(hottestPolicy);
            GuardrailPolicies resolved = resolvePolicy(hottestPolicy, policiesByName);
            card.put("hottestPolicy", new BasicDBObject("policy", hottestPolicy).append("count", count)
                    .append("severity", resolved != null ? resolved.getSeverity() : null));
            facts.add(fact("hottestPolicy", "Most-triggered policy", hottestPolicy));
            facts.add(fact("hottestPolicyCount", "Times that policy triggered", InsightUtil.grouped(count)));

            if (resolved != null) context.add(new BasicDBObject("key", "hottestPolicyConfig").append("text", policyContextText(resolved)));
        } else {
            card.put("hottestPolicy", null);
        }
        card.put("context", context);
        card.put("facts", facts);
        return card;
    }

    // ── Observability card ────────────────────────────────────────────────────────────────────

    private BasicDBObject observabilityCard(List<UserAnalysisData> serviceObservability, InsightDataBundle bundle,
                                             Map<String, Map<String, Integer>> globalTopics) {
        BasicDBObject card = card(CARD_OBSERVABILITY, "Agent Token Usage & Topics");
        card.put("cta", cta("view_observability", "View LLM observability", InsightRoutes.LLM_OBSERVABILITY));
        card.put("drillId", DRILL_OBSERVABILITY);
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

    // ── Insight card drilldowns ───────────────────────────────────────────────────────────────

    /**
     * One flyout level per drillId — the real rows behind a card, capped at {@link #DRILL_ROW_CAP}
     * and sorted worst-first (severity, where the row carries one; token volume for observability).
     * Every row was already sitting in memory by the time ArgusPostureAction#buildCards runs
     * (openIssueGroups/maliciousEvents/serviceObservability), so — same as PostureService's own
     * fetchDrill — this is pagination over an in-memory list, not a new query.
     */
    public PostureDrillResult fetchDrill(String drillId, int skip, int limit,
                                          List<AgentFindingGroup> openIssueGroups,
                                          Map<String, Info> testInfoByType,
                                          List<DashboardMaliciousEvent> maliciousEvents,
                                          List<UserAnalysisData> serviceObservability,
                                          InsightDataBundle bundle) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_DRILL_LIMIT;
        Map<Integer, ApiCollection> collectionsById = new HashMap<>();
        if (bundle != null) {
            for (ApiCollection c : safe(bundle.collections)) {
                if (c != null) collectionsById.put(c.getId(), c);
            }
        }
        if (drillId == null) return unknownDrill();
        switch (drillId) {
            case DRILL_RED_TEAM_ISSUES:
                return redTeamIssuesDrill(openIssueGroups, testInfoByType, collectionsById, skip, effectiveLimit);
            case DRILL_GUARDRAIL_EVENTS:
                return guardrailEventsDrill(maliciousEvents, collectionsById, skip, effectiveLimit);
            case DRILL_OBSERVABILITY:
                return observabilityDrill(serviceObservability, bundle, skip, effectiveLimit);
            default:
                return unknownDrill();
        }
    }

    private PostureDrillResult redTeamIssuesDrill(List<AgentFindingGroup> openIssueGroups,
                                                   Map<String, Info> testInfoByType,
                                                   Map<Integer, ApiCollection> collectionsById, int skip, int limit) {
        PostureDrillResult result = new PostureDrillResult();
        result.setTitle("Open red-team issues");
        result.getBreadcrumb().add(new PostureDrillResult.BreadcrumbItem("", "Open red-team issues"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("agentName", "Agent"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("vulnType", "Issue type"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("severity", "Severity"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("count", "Occurrences"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("lastSeen", "Last seen"));
        result.setDrillable(false);
        result.getCtas().add(new InsightResult.Cta("openIssues", "Open issues", "NAVIGATE",
                InsightRoutes.ISSUES, null, false));

        List<AgentFindingGroup> groups = new ArrayList<>(safe(openIssueGroups));
        groups.removeIf(g -> g == null);
        groups.sort(Comparator
                .comparingInt((AgentFindingGroup g) -> InsightUtil.severityRank(g.getSecondary()))
                .thenComparing(Comparator.comparingLong(AgentFindingGroup::getCount).reversed()));

        long totalOpenIssues = 0;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentFindingGroup g : groups) {
            totalOpenIssues += g.getCount();
            rows.add(PostureService.row("agentName", agentName(g.getCollectionId(), collectionsById),
                    "vulnType", testDisplayName(g.getType(), testInfoByType), "severity", g.getSecondary(), "count", g.getCount(),
                    "lastSeen", g.getLastSeen()));
        }
        result.getSummary().add(new InsightResult.Metric("issueGroups", "Distinct issue groups",
                groups.size(), "count", InsightUtil.grouped(groups.size())));
        result.getSummary().add(new InsightResult.Metric("totalOpenIssues", "Open red-team issues",
                totalOpenIssues, "count", InsightUtil.grouped(totalOpenIssues)));

        capAndPaginate(result, rows, skip, limit);
        return result;
    }

    private PostureDrillResult guardrailEventsDrill(List<DashboardMaliciousEvent> maliciousEvents,
                                                      Map<Integer, ApiCollection> collectionsById, int skip, int limit) {
        PostureDrillResult result = new PostureDrillResult();
        result.setTitle("Guardrail activity");
        result.getBreadcrumb().add(new PostureDrillResult.BreadcrumbItem("", "Guardrail activity"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("agentName", "Agent"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("policy", "Policy"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("severity", "Severity"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("detectedAt", "Detected at"));
        result.setDrillable(false);
        result.getCtas().add(new InsightResult.Cta("viewActivity", "View guardrail activity", "NAVIGATE",
                InsightRoutes.GUARDRAIL_ACTIVITY, null, false));

        List<DashboardMaliciousEvent> events = new ArrayList<>();
        for (DashboardMaliciousEvent e : safe(maliciousEvents)) {
            if (e != null) events.add(e);
        }
        events.sort(Comparator
                .comparingInt((DashboardMaliciousEvent e) -> InsightUtil.severityRank(e.getSeverity()))
                .thenComparing(Comparator.comparingLong(DashboardMaliciousEvent::getTimestamp).reversed()));

        HostCollectionResolver resolver = new HostCollectionResolver(new ArrayList<>(collectionsById.values()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DashboardMaliciousEvent e : events) {
            rows.add(PostureService.row("agentName", agentName(agentIdForEvent(e, resolver), collectionsById),
                    "policy", e.getFilterId(), "severity", e.getSeverity(), "detectedAt", e.getTimestamp()));
        }
        result.getSummary().add(new InsightResult.Metric("totalEvents", "Guardrail/malicious events",
                events.size(), "count", InsightUtil.grouped(events.size())));

        capAndPaginate(result, rows, skip, limit);
        return result;
    }

    private PostureDrillResult observabilityDrill(List<UserAnalysisData> serviceObservability,
                                                    InsightDataBundle bundle, int skip, int limit) {
        PostureDrillResult result = new PostureDrillResult();
        result.setTitle("Agent token usage");
        result.getBreadcrumb().add(new PostureDrillResult.BreadcrumbItem("", "Agent token usage"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("agentName", "Agent"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("inputTokens", "Input tokens"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("outputTokens", "Output tokens"));
        result.getColumns().add(new PostureDrillResult.ColumnDef("totalTokens", "Total tokens"));
        result.setDrillable(false);
        result.getCtas().add(new InsightResult.Cta("viewObservability", "View LLM observability", "NAVIGATE",
                InsightRoutes.LLM_OBSERVABILITY, null, false));

        List<Map<String, Object>> rows = new ArrayList<>();
        long grandTotal = 0;
        for (UserAnalysisData row : safe(serviceObservability)) {
            if (row == null) continue;
            long input = row.getTotalInputTokens();
            long output = row.getTotalOutputTokens();
            long total = input + output;
            grandTotal += total;
            String agentName = row.getId() != null && row.getId().getServiceId() != null
                    ? row.getId().getServiceId() : null;
            if (agentName != null && bundle != null) {
                List<ApiCollection> matches = bundle.collectionsForServiceName(agentName);
                if (!matches.isEmpty()) agentName = matches.get(0).getName();
            }
            rows.add(PostureService.row("agentName", agentName, "inputTokens", input,
                    "outputTokens", output, "totalTokens", total));
        }
        rows.sort((a, b) -> Long.compare((Long) b.get("totalTokens"), (Long) a.get("totalTokens")));
        result.getSummary().add(new InsightResult.Metric("totalTokens", "Tokens used by agents this window",
                grandTotal, "tokens", InsightUtil.grouped(grandTotal)));

        capAndPaginate(result, rows, skip, limit);
        return result;
    }

    /** Caps `rows` (already sorted worst-first by the caller) at {@link #DRILL_ROW_CAP} — "the top
     *  50, sorted" — then delegates to PostureService's own skip/limit slice, and sets the level's
     *  real worst-severity badge from whatever survived the cap. Same "fixed top-N snapshot, not a
     *  true paginated total" tradeoff PostureService's own criticalAlertsDrill already makes. */
    private void capAndPaginate(PostureDrillResult result, List<Map<String, Object>> rows, int skip, int limit) {
        List<Map<String, Object>> capped = rows.size() > DRILL_ROW_CAP ? rows.subList(0, DRILL_ROW_CAP) : rows;
        PostureService.paginate(result, capped, skip, limit);
        result.setSeverity(PostureService.worstSeverity(capped));
    }

    private static PostureDrillResult unknownDrill() {
        PostureDrillResult result = new PostureDrillResult();
        result.setTitle("Unknown drilldown");
        result.setDrillable(false);
        return result;
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
        if (collectionId == null) return UNKNOWN_AGENT;
        ApiCollection c = collectionsById.get(collectionId);
        return c != null ? agentDisplayName(c) : UNKNOWN_AGENT;
    }

    // An event's apiCollectionId is not a real collection id for guardrail traffic, so attribute it by host, then actor
    // (HostCollectionResolver.resolveEvent, same as the posture score); null when unmatched.
    private static Integer agentIdForEvent(DashboardMaliciousEvent e, HostCollectionResolver resolver) {
        List<Integer> ids = resolver.resolveEvent(e.getHost(), e.getActor());
        return ids.isEmpty() ? null : ids.get(0);
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

        return kpi;
    }

    // Same scoping and threshold as buildHighestRiskAgents, so the tile and the table agree.
    private BasicDBObject highRiskAgentsKpi(List<ApiCollection> assets) {
        long highRisk = 0;
        for (ApiCollection c : assets) {
            if (c == null || !isAgenticInScope(c) || c.getPostureScore() == null) continue;
            if (Math.round(c.getPostureScore()) >= SEVERITY_HIGH_AT) highRisk++;
        }
        BasicDBObject kpi = kpi(KPI_HIGH_RISK_AGENTS, "High-Risk Agents", highRisk);
        // No per-agent history is stored, so a week-over-week delta isn't available yet.
        kpi.put("secondaryFootnote", "Week-over-week change not tracked yet");
        kpi.put("secondaryTone", "subdued");
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

            privileged = ApiInfoDao.instance.count(Filters.and(inScope, privilegedToolFilter()));

            destructive = ApiInfoDao.instance.count(Filters.and(inScope, destructiveToolFilter()));
        }

        BasicDBObject kpi = kpi(KPI_PRIVILEGED_TOOLS, "Privileged Tools", privileged);
        kpi.put("footnote", "privileged");
        kpi.put("secondaryFootnote", countLine(destructive, "destructive", "None destructive"));
        kpi.put("secondaryTone", riskTone(destructive, "critical"));
        return kpi;
    }

    private static Bson privilegedToolFilter() {
        return Filters.and(
                Filters.exists(ApiInfo.TOOL_INFO_CAPABILITY),
                Filters.ne(ApiInfo.TOOL_INFO_CAPABILITY, ToolCapabilityClassifier.SAFE));
    }

    private static Bson destructiveToolFilter() {
        return Filters.in(ApiInfo.TOOL_INFO_CAPABILITY,
                ToolCapabilityClassifier.RESOURCE_DELETE,
                ToolCapabilityClassifier.CRITICAL_RESOURCE_WRITE);
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
                                           Map<Integer, Map<String, Integer>> sensitiveViolationsByAsset) {
        long violations = 0, withSensitive = 0;
        if (sensitiveViolationsByAsset != null) {
            for (ApiCollection asset : assets) {
                Map<String, Integer> bySeverity = sensitiveViolationsByAsset.get(asset.getId());
                if (bySeverity == null || bySeverity.isEmpty()) continue;
                withSensitive++;
                violations += bySeverity.values().stream().mapToInt(Integer::intValue).sum();
            }
        }

        BasicDBObject kpi = kpi(KPI_SENSITIVE_DATA, "Sensitive Data", violations);
        kpi.put("footnote", sensitiveViolationsByAsset == null
                ? "Guardrail violation data unavailable"
                : "violation(s) in the last 90 days across " + withSensitive + " asset(s)");
        return kpi;
    }

    /** assetId -> {severity -> count} of sensitive-data guardrail violations (PII or custom LLM rule, see
     *  InsightUtil.isSensitiveDataEvent) in the last 90 days — the same events and agent attribution the
     *  posture score's sensitive-data category uses; null when the threat backend is unavailable. */
    private static Map<Integer, Map<String, Integer>> sensitiveViolationsByAsset(InsightDataBundle bundle,
                                                                               List<ApiCollection> assets) {
        List<Integer> ids = new ArrayList<>();
        for (ApiCollection a : assets) ids.add(a.getId());
        if (ids.isEmpty()) return new HashMap<>();
        return bundle.maliciousSeverityCounts(ids, Context.now() - SENSITIVE_DATA_WINDOW_SECONDS,
                InsightUtil::isSensitiveDataEvent);
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

        GuardrailsCoverageBreakdown breakdown = computeCoverage(assets, policies);
        long notCovered = breakdown.uncovered.size();
        double percent = percentOf(breakdown.covered(), assets.size());

        kpi.put("value", percent);
        kpi.put("tone", toneForPercent(percent));
        kpi.put("footnote", enforcingLine(breakdown.enforcing.size(), assets.size()));
        kpi.put("secondaryFootnote", countLine(notCovered, "asset(s) not covered", "All asset(s) covered"));
        kpi.put("secondaryTone", toneForPercent(percent));
        return kpi;
    }

    public static class GuardrailsCoverageBreakdown {
        public final List<ApiCollection> uncovered = new ArrayList<>();
        public final List<ApiCollection> alertOnly = new ArrayList<>();
        public final List<ApiCollection> enforcing = new ArrayList<>();
        public final Map<Integer, List<GuardrailPolicies>> coveringPolicies = new HashMap<>();

        public int covered() {
            return alertOnly.size() + enforcing.size();
        }
    }

    /** Connector-created collections carry a name but no hostName; policies target them by that
     *  name, so it is the identity used for both matching and display. */
    static String assetIdentity(ApiCollection c) {
        if (c == null) return null;
        return c.getHostName() != null ? c.getHostName() : c.getName();
    }

    static GuardrailsCoverageBreakdown computeCoverage(List<ApiCollection> assets, List<GuardrailPolicies> policies) {
        GuardrailsCoverageBreakdown breakdown = new GuardrailsCoverageBreakdown();
        for (ApiCollection asset : assets) {
            List<GuardrailPolicies> covering = new ArrayList<>();
            boolean blocking = false;
            for (GuardrailPolicies p : policies) {
                if (p == null) continue;
                if (!InsightUtil.policyCoversHost(p, assetIdentity(asset))) continue;
                covering.add(p);
                if (InsightUtil.isBlockingPolicy(p)) blocking = true;
            }
            if (covering.isEmpty()) {
                breakdown.uncovered.add(asset);
                continue;
            }
            breakdown.coveringPolicies.put(asset.getId(), covering);
            if (blocking) breakdown.enforcing.add(asset);
            else breakdown.alertOnly.add(asset);
        }
        return breakdown;
    }

    private static String enforcingLine(long enforcing, long total) {
        if (enforcing == 0) return "No asset(s) enforcing";
        return enforcing + " of " + total + " asset(s) enforcing";
    }

    public PostureDrillResult fetchSensitiveDataDrill(InsightDataBundle bundle, String environment,
                                                      int skip, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_DRILL_LIMIT;
        int effectiveSkip = Math.max(skip, 0);

        List<ApiCollection> assets = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c == null || c.isDeactivated()) continue;
            assets.add(c);
        }
        List<ApiCollection> scoped = assetsIn(assets, environment);
        Map<Integer, Map<String, Integer>> violationsByAsset = sensitiveViolationsByAsset(bundle, scoped);
        List<Integer> scopedIds = new ArrayList<>();
        for (ApiCollection a : scoped) scopedIds.add(a.getId());
        Map<Integer, Map<String, Integer>> dataByAsset = scopedIds.isEmpty() ? new HashMap<>()
                : bundle.sensitiveDataCounts(scopedIds, Context.now() - SENSITIVE_DATA_WINDOW_SECONDS);

        List<ApiCollection> withSensitive = new ArrayList<>();
        Map<Integer, Integer> violationCount = new HashMap<>();
        if (violationsByAsset != null) {
            for (ApiCollection asset : scoped) {
                Map<String, Integer> bySeverity = violationsByAsset.get(asset.getId());
                if (bySeverity == null || bySeverity.isEmpty()) continue;
                withSensitive.add(asset);
                violationCount.put(asset.getId(), bySeverity.values().stream().mapToInt(Integer::intValue).sum());
            }
        }
        withSensitive.sort(Comparator.comparingInt((ApiCollection c) -> violationCount.get(c.getId())).reversed());

        PostureDrillResult result = new PostureDrillResult();
        result.setTitle("Sensitive data");
        result.setBreadcrumb(Collections.singletonList(
                new PostureDrillResult.BreadcrumbItem("", "Sensitive data")));
        result.setColumns(sensitiveDataColumns());
        result.setDrillable(false);
        result.setTotal(withSensitive.size());
        result.setSkip(effectiveSkip);
        result.setLimit(effectiveLimit);
        long totalViolations = violationCount.values().stream().mapToLong(Integer::longValue).sum();
        result.setSummary(Arrays.asList(
                new InsightResult.Metric("violations", "Sensitive-data violations (90d)", totalViolations, "count",
                        InsightUtil.grouped(totalViolations)),
                new InsightResult.Metric("withViolations", "Assets affected", withSensitive.size(), scoped.size(), "count",
                        InsightUtil.grouped(withSensitive.size()), null)));
        result.setEmptyMessage("No assets with sensitive-data guardrail violations in the last 90 days.");
        if (violationsByAsset == null) {
            result.addDataGap(new InsightResult.Gap("THREAT_BACKEND", "REQUEST_FAILED",
                    "Guardrail violation data could not be fetched, so sensitive-data violations can't be shown."));
        }
        result.setCtas(Arrays.asList(
                new InsightResult.Cta("view_activity", "View guardrail activity", "NAVIGATE",
                        InsightRoutes.GUARDRAIL_ACTIVITY, new HashMap<>(), true),
                new InsightResult.Cta("create_policy", "Create guardrail policy", "NAVIGATE",
                        InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), false)));

        int from = Math.min(effectiveSkip, withSensitive.size());
        int to = Math.min(from + effectiveLimit, withSensitive.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiCollection asset : withSensitive.subList(from, to)) {
            Map<String, Object> row = new HashMap<>();
            row.put("asset", assetIdentity(asset));
            row.put("type", AgenticObserveUtil.getTypeFromCollection(asset));
            row.put("environment", envBucket(envTagValue(asset)));
            Map<String, Integer> data = dataByAsset == null ? null : dataByAsset.get(asset.getId());
            row.put("dataTypes", data == null || data.isEmpty() ? "-" : InsightUtil.sensitiveDataLine(data));
            row.put("violations", violationCount.get(asset.getId()));
            row.put("severity", worstSeverityOf(violationsByAsset.get(asset.getId())));
            rows.add(row);
        }
        result.setRows(rows);
        return result;
    }

    private static String worstSeverityOf(Map<String, Integer> bySeverity) {
        String worst = null;
        for (Map.Entry<String, Integer> e : bySeverity.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0) continue;
            if (worst == null || InsightUtil.severityRank(e.getKey()) < InsightUtil.severityRank(worst)) worst = e.getKey();
        }
        return worst;
    }

    private static List<PostureDrillResult.ColumnDef> sensitiveDataColumns() {
        return Arrays.asList(
                new PostureDrillResult.ColumnDef("asset", "Asset"),
                new PostureDrillResult.ColumnDef("type", "Type"),
                new PostureDrillResult.ColumnDef("environment", "Environment"),
                new PostureDrillResult.ColumnDef("dataTypes", "Data flagged"),
                new PostureDrillResult.ColumnDef("violations", "Sensitive-data violations (90d)"),
                new PostureDrillResult.ColumnDef("severity", "Worst severity"));
    }

    /** Every tile is out of the same total (assets holding sensitive data), so each carries it as a
     *  denominator. It stays out of `formatted` on purpose — that string is what the narrative model
     *  reads, and "0 / 1" there gets misread as "0 of 1 findings". The UI renders it as a suffix. */
    public PostureDrillResult fetchPrivilegedToolsDrill(InsightDataBundle bundle, String environment,
                                                        int skip, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_DRILL_LIMIT;
        int effectiveSkip = Math.max(skip, 0);

        List<ApiCollection> assets = new ArrayList<>();
        List<Integer> deactivatedIds = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c == null) continue;
            if (c.isDeactivated()) deactivatedIds.add(c.getId());
            else assets.add(c);
        }
        List<ApiCollection> scoped = assetsIn(assets, environment);

        PostureDrillResult result = new PostureDrillResult();
        result.setTitle("Privileged tools");
        result.setBreadcrumb(Collections.singletonList(
                new PostureDrillResult.BreadcrumbItem("", "Privileged tools")));
        result.setColumns(privilegedToolsColumns());
        result.setDrillable(false);
        result.setSkip(effectiveSkip);
        result.setLimit(effectiveLimit);
        result.setEmptyMessage("No privileged tools in this environment.");
        result.setCtas(Arrays.asList(
                new InsightResult.Cta("view_agentic_assets", "View agentic assets", "NAVIGATE",
                        InsightRoutes.AGENTIC_ASSETS, new HashMap<>(), true),
                new InsightResult.Cta("create_policy", "Create guardrail policy", "NAVIGATE",
                        InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), false)));

        if (scoped.isEmpty()) {
            result.setSummary(privilegedToolsSummary(0, 0, 0, 0));
            return result;
        }

        Bson inScope = scopeFilter(scoped, environment, deactivatedIds);
        Bson privileged = Filters.and(inScope, privilegedToolFilter());

        Map<String, Long> byCapability = toolCountsByCapability(privileged);
        long destructive = byCapability.getOrDefault(ToolCapabilityClassifier.RESOURCE_DELETE, 0L)
                + byCapability.getOrDefault(ToolCapabilityClassifier.CRITICAL_RESOURCE_WRITE, 0L);
        long credentialRead = byCapability.getOrDefault(ToolCapabilityClassifier.CREDENTIAL_OR_PII_READ, 0L);
        long fileWrite = byCapability.getOrDefault(ToolCapabilityClassifier.FILE_WRITE, 0L);

        long total = 0;
        for (long n : byCapability.values()) total += n;

        result.setTotal(total);
        result.setSummary(privilegedToolsSummary(total, destructive, credentialRead, fileWrite));

        List<ApiInfo> page = ApiInfoDao.instance.findAll(privileged, effectiveSkip, effectiveLimit,
                Sorts.descending(ApiInfo.LAST_SEEN),
                Projections.include(Constants.ID, ApiInfo.TOOL_INFO_CAPABILITY, ApiInfo.LAST_SEEN));

        Map<Integer, ApiCollection> byId = new HashMap<>();
        for (ApiCollection c : scoped) byId.put(c.getId(), c);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiInfo tool : page) rows.add(privilegedToolRow(tool, byId));
        result.setRows(rows);
        return result;
    }

    private static Map<String, Long> toolCountsByCapability(Bson filter) {
        Map<String, Long> counts = new HashMap<>();
        List<Bson> pipeline = Arrays.asList(
                Aggregates.match(filter),
                Aggregates.group("$" + ApiInfo.TOOL_INFO_CAPABILITY, Accumulators.sum("count", 1)));

        MongoCursor<BasicDBObject> cursor = ApiInfoDao.instance.aggregateWithRbac(pipeline).cursor();
        while (cursor.hasNext()) {
            BasicDBObject doc = cursor.next();
            Object capability = doc.get("_id");
            if (capability == null) continue;
            counts.put(String.valueOf(capability), (long) doc.getInt("count", 0));
        }
        return counts;
    }

    private static List<PostureDrillResult.ColumnDef> privilegedToolsColumns() {
        return Arrays.asList(
                new PostureDrillResult.ColumnDef("tool", "Tool"),
                new PostureDrillResult.ColumnDef("capability", "Capability"),
                new PostureDrillResult.ColumnDef("asset", "Asset"),
                new PostureDrillResult.ColumnDef("environment", "Environment"),
                new PostureDrillResult.ColumnDef("lastSeen", "Last seen"));
    }

    private static List<InsightResult.Metric> privilegedToolsSummary(long privileged, long destructive,
                                                                     long credentialRead, long fileWrite) {
        return Arrays.asList(
                new InsightResult.Metric("privileged", "Privileged", privileged, "count",
                        InsightUtil.grouped(privileged)),
                new InsightResult.Metric("destructive", "Destructive", destructive, "count",
                        InsightUtil.grouped(destructive)),
                new InsightResult.Metric("credentialRead", "Credential / PII read", credentialRead, "count",
                        InsightUtil.grouped(credentialRead)),
                new InsightResult.Metric("fileWrite", "File write", fileWrite, "count",
                        InsightUtil.grouped(fileWrite)));
    }

    private static Map<String, Object> privilegedToolRow(ApiInfo tool, Map<Integer, ApiCollection> byId) {
        ApiInfo.ApiInfoKey key = tool.getId();
        ApiCollection collection = key == null ? null : byId.get(key.getApiCollectionId());

        Map<String, Object> row = new HashMap<>();
        row.put("tool", key == null || key.getUrl() == null ? "-" : ToolClassificationCron.toolNameFromUrl(key.getUrl()));
        row.put("capability", InsightUtil.humanizeToolCapability(
                tool.getToolInfo() == null ? null : tool.getToolInfo().getCapability()));
        row.put("asset", collection == null ? "-" : assetIdentity(collection));
        row.put("environment", collection == null ? "-" : envBucket(envTagValue(collection)));
        row.put("lastSeen", tool.getLastSeen());
        return row;
    }

    public PostureDrillResult fetchProtectionCoverageDrill(InsightDataBundle bundle, String environment,
                                                           int skip, int limit) {
        int effectiveLimit = limit > 0 ? limit : DEFAULT_DRILL_LIMIT;
        int effectiveSkip = Math.max(skip, 0);

        List<ApiCollection> assets = new ArrayList<>();
        for (ApiCollection c : bundle.collections) {
            if (c == null || c.isDeactivated()) continue;
            assets.add(c);
        }
        List<ApiCollection> scoped = assetsIn(assets, environment);
        GuardrailsCoverageBreakdown breakdown = computeCoverage(scoped, bundle.policies);

        List<Integer> uncoveredIds = new ArrayList<>();
        for (ApiCollection c : breakdown.uncovered) uncoveredIds.add(c.getId());
        Map<Integer, Integer> toolCounts =
                ApiInfoDao.instance.getCountsByCollection(uncoveredIds, privilegedToolFilter());

        List<ApiCollection> attention = new ArrayList<>(breakdown.uncovered);
        attention.sort(Comparator.comparingInt(
                (ApiCollection c) -> toolCounts.getOrDefault(c.getId(), 0)).reversed());

        PostureDrillResult result = new PostureDrillResult();
        result.setTitle("Protection coverage");
        result.setBreadcrumb(Collections.singletonList(
                new PostureDrillResult.BreadcrumbItem("", "Protection coverage")));
        result.setColumns(protectionCoverageColumns());
        result.setDrillable(false);
        result.setTotal(attention.size());
        result.setSkip(effectiveSkip);
        result.setLimit(effectiveLimit);
        result.setSummary(protectionCoverageSummary(scoped.size(), breakdown));
        result.setEmptyMessage(scoped.isEmpty()
                ? "No asset(s) discovered in this environment."
                : "All " + scoped.size() + " asset(s) are covered by a policy.");
        result.setCtas(Arrays.asList(
                new InsightResult.Cta("create_policy", "Create guardrail policy", "NAVIGATE",
                        InsightRoutes.GUARDRAIL_POLICIES, new HashMap<>(), true),
                new InsightResult.Cta("view_violations", "View violations", "NAVIGATE",
                        InsightRoutes.GUARDRAIL_VIOLATIONS, new HashMap<>(), false)));

        int from = Math.min(effectiveSkip, attention.size());
        int to = Math.min(from + effectiveLimit, attention.size());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ApiCollection asset : attention.subList(from, to)) {
            rows.add(protectionCoverageRow(asset, bundle, breakdown, toolCounts));
        }
        result.setRows(rows);
        return result;
    }

    private static List<PostureDrillResult.ColumnDef> protectionCoverageColumns() {
        return Arrays.asList(
                new PostureDrillResult.ColumnDef("asset", "Unprotected asset"),
                new PostureDrillResult.ColumnDef("type", "Type"),
                new PostureDrillResult.ColumnDef("environment", "Environment"),
                new PostureDrillResult.ColumnDef("protection", "Protection"),
                new PostureDrillResult.ColumnDef("sensitiveData", "Sensitive data"),
                new PostureDrillResult.ColumnDef("privilegedTools", "Privileged tools"));
    }

    private static List<InsightResult.Metric> protectionCoverageSummary(int inScope,
                                                                        GuardrailsCoverageBreakdown breakdown) {
        double percent = percentOf(breakdown.covered(), inScope);
        return Arrays.asList(
                new InsightResult.Metric("coverage", "Coverage", percent, "percent", formatPercent(percent)),
                new InsightResult.Metric("alertMode", "Alert mode", breakdown.alertOnly.size(), "count",
                        InsightUtil.grouped(breakdown.alertOnly.size())),
                new InsightResult.Metric("enforcing", "Enforcing", breakdown.enforcing.size(), "count",
                        InsightUtil.grouped(breakdown.enforcing.size())),
                new InsightResult.Metric("notCovered", "Not covered", breakdown.uncovered.size(), inScope, "count",
                        InsightUtil.grouped(breakdown.uncovered.size()), null));
    }

    private static Map<String, Object> protectionCoverageRow(ApiCollection asset, InsightDataBundle bundle,
                                                             GuardrailsCoverageBreakdown breakdown,
                                                             Map<Integer, Integer> toolCounts) {
        List<GuardrailPolicies> covering = breakdown.coveringPolicies.get(asset.getId());
        List<String> policyNames = new ArrayList<>();
        if (covering != null) {
            for (GuardrailPolicies p : covering) {
                if (p.getName() != null) policyNames.add(p.getName());
            }
        }
        List<String> sensitiveTypes = bundle.sensitiveByCollection.get(asset.getId());

        Map<String, Object> row = new HashMap<>();
        row.put("asset", assetIdentity(asset));
        row.put("type", AgenticObserveUtil.getTypeFromCollection(asset));
        row.put("environment", envBucket(envTagValue(asset)));
        row.put("protection", policyNames.isEmpty()
                ? PROTECTION_NONE
                : PROTECTION_ALERT_ONLY + " (" + String.join(", ", policyNames) + ")");
        row.put("sensitiveData", sensitiveTypes == null || sensitiveTypes.isEmpty()
                ? "None detected" : String.join(", ", sensitiveTypes));
        row.put("privilegedTools", toolCounts.getOrDefault(asset.getId(), 0));
        return row;
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

    // Package-private (not private): TestArgusPostureService exercises this wrapper directly, and
    // ArgusAgentPostureDrillService calls InsightUtil.envTagValue directly instead — same package,
    // same convention PostureService's own paginate/worstSeverity helpers already use.
    static String envTagValue(ApiCollection c) {
        return InsightUtil.envTagValue(c);
    }

    static List<ApiCollection> assetsIn(List<ApiCollection> assets, String environment) {
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

    public static String environmentKey(String environment) {
        return isAllEnvironments(environment) ? ENV_ID_ALL : environment.trim().toLowerCase(Locale.ROOT);
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

    static String formatPercent(double percent) {
        if (percent == Math.floor(percent)) return (long) percent + "%";
        return percent + "%";
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

    // Pure read of cron-written history rows; nothing is recomputed here.
    public BasicDBObject buildPostureScore(AgenticPostureScoreHistory latest, List<AgenticPostureScoreHistory> trendHistory,
                                            AgenticPostureScoreHistory weekAgoHistory) {
        BasicDBObject postureScore = new BasicDBObject();
        postureScore.put("value", latest != null ? Math.round(latest.getValue()) : null);
        postureScore.put("agentsScored", latest != null ? latest.getAgentsScored() : 0);
        postureScore.put("agentsWithNoSignal", latest != null ? latest.getAgentsWithNoSignal() : 0);
        postureScore.put("dataGaps", postureScoreGaps(latest));

        List<Double> trend = safe(trendHistory).stream().filter(Objects::nonNull)
                .map(AgenticPostureScoreHistory::getValue).collect(Collectors.toList());
        if (!trend.isEmpty()) postureScore.put("trend", trend);

        if (latest != null && weekAgoHistory != null) {
            long current = Math.round(latest.getValue());
            long prior = Math.round(weekAgoHistory.getValue());
            postureScore.put("delta", current - prior);
            postureScore.put("deltaTone", current > prior ? "critical" : current < prior ? "success" : "neutral"); // higher is worse
        }

        return postureScore;
    }

    // NOT_COMPUTED_YET / NO_ROWS / PARTIAL_COVERAGE depending on how much of the account is scored.
    private static List<Map<String, Object>> postureScoreGaps(AgenticPostureScoreHistory latest) {
        List<Map<String, Object>> gaps = new ArrayList<>();
        if (latest == null) {
            gaps.add(gapRow("AGENTIC_ASSETS", "NOT_COMPUTED_YET", "The posture score hasn't been computed for this account yet — check back shortly."));
        } else if (latest.getAgentsScored() == 0) {
            gaps.add(gapRow("AGENTIC_ASSETS", "NO_ROWS", "No AI agents have been discovered yet, so the posture score can't be computed."));
        } else if (latest.getAgentsWithNoSignal() > 0) {
            gaps.add(gapRow("AGENTIC_ASSETS", "PARTIAL_COVERAGE",
                    latest.getAgentsWithNoSignal() + " of " + latest.getAgentsScored() + " agent(s) haven't been scored yet and are excluded from the score."));
        }
        return gaps;
    }

    private static final int HIGHEST_RISK_AGENTS_LIMIT = 5;

    public List<BasicDBObject> buildHighestRiskAgents(InsightDataBundle bundle, String environment) {
        List<BasicDBObject> rows = new ArrayList<>();
        int rank = 1;
        for (ApiCollection c : assetsIn(scoredAgents(bundle.collections), environment)) {
            if (rank > HIGHEST_RISK_AGENTS_LIMIT) break;
            long score = Math.round(c.getPostureScore());

            BasicDBObject row = new BasicDBObject();
            row.put("rank", rank++);
            row.put("groupKey", String.valueOf(c.getId()));
            row.put("name", agentDisplayName(c));
            row.put("environment", envBucket(envTagValue(c)));
            row.put("score", score);
            row.put("issue", worstIssue(c.getPostureSubScores()));
            row.put("severity", severityForScore(score));
            rows.add(row);
        }
        return rows;
    }

    // In-scope, active agents with a cron-computed score, highest score first.
    static List<ApiCollection> scoredAgents(List<ApiCollection> collections) {
        List<ApiCollection> scored = new ArrayList<>();
        for (ApiCollection c : collections) {
            if (c == null || c.isDeactivated() || c.getPostureScore() == null) continue;
            if (isAgenticInScope(c)) scored.add(c);
        }
        scored.sort(Comparator.comparingDouble(ApiCollection::getPostureScore).reversed());
        return scored;
    }

    // Mirrors UsersCollectionsList#getContextCollections(AGENTIC); must match AgenticPostureScoreCron's gate.
    static boolean isAgenticInScope(ApiCollection c) {
        return (c.isMcpCollection() || c.isGenAICollection()) && !c.isEndpointCollection();
    }

    static String agentDisplayName(ApiCollection c) {
        // Not extractServiceName(hostName): it mis-parses real DNS hosts ("mcp.kite.trade" -> "trade").
        String assetValue = AgenticObserveUtil.getAssetTagValue(c);
        if (assetValue != null && !assetValue.trim().isEmpty()) return AgenticObserveUtil.formatDisplayName(assetValue);
        if (c.getName() != null && !c.getName().trim().isEmpty()) return c.getName();
        return c.getHostName() != null ? c.getHostName() : UNKNOWN_AGENT;
    }

    // Category contributing the most weighted points, not the highest raw sub-score.
    static PostureScoreCategory worstCategory(Map<String, Object> subScores) {
        PostureScoreCategory worst = null;
        double worstPoints = 0;
        for (PostureScoreCategory category : PostureScoreCategory.values()) {
            double points = category.points(subScores);
            if (points > worstPoints) {
                worstPoints = points;
                worst = category;
            }
        }
        return worst;
    }

    static String worstIssue(Map<String, Object> subScores) {
        PostureScoreCategory worst = worstCategory(subScores);
        return worst != null ? worst.issue : "No significant issues detected";
    }

    private static final int SEVERITY_CRITICAL_AT = 75;
    static final int SEVERITY_HIGH_AT = 50;
    private static final int SEVERITY_MEDIUM_AT = 25;

    static String severityForScore(long score) {
        if (score >= SEVERITY_CRITICAL_AT) return "CRITICAL";
        if (score >= SEVERITY_HIGH_AT) return "HIGH";
        if (score >= SEVERITY_MEDIUM_AT) return "MEDIUM";
        return "LOW";
    }

    // Same shape as InsightResult.Gap so the frontend's GapHint renders it.
    private static Map<String, Object> gapRow(String source, String reason, String impact) {
        Map<String, Object> row = new HashMap<>();
        row.put("source", source);
        row.put("reason", reason);
        row.put("impact", impact);
        return row;
    }
}
