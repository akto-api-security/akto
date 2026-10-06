package com.akto.service.ask;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.McpAllowlistDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.agentic_sessions.UserAnalysisDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.McpAllowlist;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.proto.generated.threat_detection.service.dashboard_service.v1.ListMaliciousRequestsResponse;
import com.akto.service.insights.InsightRoutes;
import com.akto.service.insights.InsightUtil;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.threat_detection.ThreatDetectionBackendClient;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Ask Akto overlay's always-on tile layer — a handful of single bounded queries, computed
 * fresh on every request (no InsightDataBundle, no 60s cache; the insight-tile layer above these
 * already has one). This exists because InsightService's providers are too heavy to run
 * wholesale on every page open: these are the "what could you do right now" numbers that are
 * always cheap enough to show, whether or not anything is CRITICAL/HIGH today.
 *
 * compute(contextSource) picks a different tile set per domain — API, AGENTIC (also covers the
 * raw MCP/GEN_AI values), ENDPOINT — matching whichever dashboard opened the overlay, and reuses
 * that same contextSource to scope every query to UsersCollectionsList.getContextCollectionsForUser's
 * domain-appropriate collection ids, so e.g. the API dashboard's "never tested" count doesn't
 * include agentic/MCP collections and vice versa. That lookup is itself RBAC'd and cached — see
 * its own javadoc — so this is strictly cheaper than the ad hoc scan-all-collections approach it
 * replaced here.
 *
 * The AGENTIC set's threatActivity() is the one deliberate exception to "Mongo-only, no external
 * dependency": it calls the threat-detection backend, guarded by the same try/catch-degrades-
 * to-zero pattern every other recommendation uses, so a slow/unreachable threat backend yields
 * an empty tile rather than breaking the rest of the overlay.
 */
public final class RecommendationCatalog {

    private static final LoggerMaker logger = new LoggerMaker(RecommendationCatalog.class, LogDb.DASHBOARD);

    private static final int MCP_COLLECTION_ROW_CAP = 20_000;
    private static final int AUDIT_ROW_CAP = 5_000;
    private static final int SKILL_ROW_CAP = 20_000;
    private static final int THREAT_FETCH_LIMIT = 500;
    private static final int THREAT_LOOKBACK_SECONDS = 30 * 24 * 3600;
    private static final String MALICIOUS_SKILL_TAG = "malicious-skill-tag";

    private RecommendationCatalog() {}

    public static List<Recommendation> compute(CONTEXT_SOURCE contextSource) {
        CONTEXT_SOURCE cs = contextSource != null ? contextSource : CONTEXT_SOURCE.API;
        switch (cs) {
            case AGENTIC:
            case MCP:
            case GEN_AI:
                return computeAgentic(cs);
            case ENDPOINT:
                return computeEndpoint(cs);
            case API:
            case DAST:
            default:
                return computeApi(cs);
        }
    }

    private static List<Recommendation> computeApi(CONTEXT_SOURCE contextSource) {
        List<Recommendation> out = new ArrayList<>();
        out.add(openCriticals(contextSource));
        out.add(unauthenticatedSensitive(contextSource));
        out.add(neverTested(contextSource));
        out.add(sensitiveDataTypesInResponse(contextSource));
        return out;
    }

    private static List<Recommendation> computeAgentic(CONTEXT_SOURCE contextSource) {
        List<Recommendation> out = new ArrayList<>();
        List<ApiCollection> mcpCollections = loadMcpCollections(contextSource);
        List<McpAuditInfo> auditRows = loadAuditRows();
        out.add(redTeamingCriticals(contextSource));
        out.add(maliciousMcpTools(mcpCollections, auditRows));
        out.add(unapprovedMcpServers(mcpCollections, auditRows));
        out.add(threatActivity());
        return out;
    }

    private static List<Recommendation> computeEndpoint(CONTEXT_SOURCE contextSource) {
        List<Recommendation> out = new ArrayList<>();
        out.add(maliciousSkillsTotal(contextSource));
        out.add(tokensUsedTotal());
        out.add(guardrailsOverview());
        return out;
    }

    // ── Shared domain scoping ───────────────────────────────────────────────────────────

    /** RBAC'd, cached (see its own javadoc) collection ids for one dashboard domain — API
     *  resolves to "everything not tagged MCP/GenAI/DAST/Endpoint", AGENTIC to "MCP union
     *  GenAI, excluding Endpoint", etc. Never null (an account with no matching collections
     *  returns an empty set, which correctly zeroes every recommendation scoped to it — not
     *  "don't filter"). */
    private static Set<Integer> contextCollectionIds(CONTEXT_SOURCE contextSource) {
        try {
            return UsersCollectionsList.getContextCollectionsForUser(Context.accountId.get(), contextSource);
        } catch (Exception e) {
            logger.error("RecommendationCatalog: contextCollectionIds failed: " + e.getMessage());
            return new HashSet<>();
        }
    }

    // ── API domain ──────────────────────────────────────────────────────────────────────

    /** RBAC-scoped via TestingRunIssuesDao.addCollectionsFilterForDashboard — the same
     *  dashboard-purpose-built filter IssuesAction.fetchAllIssues and
     *  InsightLazySources.agingOpenIssues() use. Deliberately NOT
     *  TestingRunIssuesDao.getSeveritiesMapForCollections(): that method aggregates through
     *  getMCollection() directly with only a manual, best-effort UsersCollectionsList filter
     *  baked in (no admin/dashboardContext handling, exceptions silently swallowed) — a real gap
     *  for a tile that's supposed to respect the viewer's actual collection access. Also scoped
     *  to the caller's domain collections, so an agentic red-teaming critical never counts
     *  toward the plain API dashboard's tile or vice versa. */
    private static long openIssueCountBySeverity(String severity, CONTEXT_SOURCE contextSource) {
        long count = 0;
        try {
            Bson baseFilter = Filters.and(
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, "OPEN"),
                    Filters.eq(TestingRunIssues.KEY_SEVERITY, severity),
                    Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, contextCollectionIds(contextSource)));
            Bson rbacFilter = TestingRunIssuesDao.instance.addCollectionsFilterForDashboard(baseFilter);
            count = TestingRunIssuesDao.instance.getMCollection().countDocuments(rbacFilter);
        } catch (Exception e) {
            logger.error("RecommendationCatalog: openIssueCountBySeverity(" + severity + ") failed: " + e.getMessage());
        }
        return count;
    }

    private static Recommendation openCriticals(CONTEXT_SOURCE contextSource) {
        long count = openIssueCountBySeverity("CRITICAL", contextSource);
        return new Recommendation("open_criticals", "Open criticals", count, "count", count > 0 ? "CRITICAL" : null,
                "Of these " + count + " critical issues, which are likely false positives?",
                InsightRoutes.ISSUES, params("severity", Arrays.asList("CRITICAL"), "status", Arrays.asList("OPEN")));
    }

    /** A single count-only Mongo query, not a row fetch: the "only auth type is UNAUTHENTICATED"
     *  exact-array-match idiom is expressible as a filter directly (ApiCollectionsAction uses the
     *  same idiom for its own posture numbers), so this never touches ApiInfoDao.findAll.
     *  ApiInfoDao.count() is RBAC-scoped (AccountsContextDaoWithRbac overrides count()), unlike
     *  the raw-aggregate path openCriticals() had to move away from above. */
    private static Recommendation unauthenticatedSensitive(CONTEXT_SOURCE contextSource) {
        long count = 0;
        try {
            Bson filter = Filters.and(
                    Filters.eq(ApiInfo.IS_SENSITIVE, true),
                    Filters.in(ApiInfo.ALL_AUTH_TYPES_FOUND,
                            java.util.Collections.singletonList(java.util.Collections.singletonList(ApiInfo.AuthType.UNAUTHENTICATED))),
                    Filters.in(ApiInfo.ID_API_COLLECTION_ID, contextCollectionIds(contextSource)),
                    UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID));
            count = ApiInfoDao.instance.count(filter);
        } catch (Exception e) {
            logger.error("RecommendationCatalog: unauthenticatedSensitive failed: " + e.getMessage());
        }
        return new Recommendation("unauth_sensitive", "Unauthenticated + sensitive", count, "count", count > 0 ? "HIGH" : null,
                "Which of these " + count + " unauthenticated APIs returning sensitive data are public-facing?",
                InsightRoutes.INVENTORY, params("authType", Arrays.asList("UNAUTHENTICATED")));
    }

    /** Count-only: APIs whose lastTested is still the zero-value default. Coarser than
     *  UntestedHighRiskApisProvider's ratio (no risk-score or 30-day-staleness cutoff) —
     *  deliberately so, to stay a single cheap COUNT rather than a full apiInfoRows scan. */
    private static Recommendation neverTested(CONTEXT_SOURCE contextSource) {
        long count = 0;
        try {
            Bson filter = Filters.and(
                    Filters.eq(ApiInfo.LAST_TESTED, 0),
                    Filters.in(ApiInfo.ID_API_COLLECTION_ID, contextCollectionIds(contextSource)),
                    UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID));
            count = ApiInfoDao.instance.count(filter);
        } catch (Exception e) {
            logger.error("RecommendationCatalog: neverTested failed: " + e.getMessage());
        }
        return new Recommendation("never_tested", "Never tested", count, "count", null,
                "Which of these " + count + " never-tested APIs carry sensitive data and should be tested first?",
                InsightRoutes.INVENTORY, params("lastTested", "never"));
    }

    /** Distinct sensitive data TYPES actually observed in responses — via
     *  SingleTypeInfoDao.responseSensitiveSubtypeApiCounts(), an API count rather than a hit
     *  count (SingleTypeInfo.count isn't a reliable hit counter — see that method's own
     *  javadoc); this is also where InsightLazySources.sensitiveApiCountBySubType() gets the
     *  exact same computation, so the two no longer duplicate the match-clause-building logic
     *  that used to live in both places. */
    private static Recommendation sensitiveDataTypesInResponse(CONTEXT_SOURCE contextSource) {
        long distinctTypes = 0;
        try {
            Bson extraFilter = Filters.and(
                    Filters.in(SingleTypeInfo._API_COLLECTION_ID, contextCollectionIds(contextSource)),
                    Filters.nin(SingleTypeInfo._API_COLLECTION_ID, new ArrayList<>(UsageMetricCalculator.getDemosAndDeactivated())));
            Map<String, Integer> apiCountBySubType = SingleTypeInfoDao.instance.responseSensitiveSubtypeApiCounts(extraFilter);
            distinctTypes = apiCountBySubType.size();
        } catch (Exception e) {
            logger.error("RecommendationCatalog: sensitiveDataTypesInResponse failed: " + e.getMessage());
        }
        return new Recommendation("sensitive_data_types", "Sensitive data types in responses", distinctTypes, "count",
                distinctTypes > 0 ? "HIGH" : null,
                "Which of these " + distinctTypes + " sensitive data types returned in responses carry the most risk?",
                InsightRoutes.SENSITIVE_DATA, params());
    }

    // ── Agentic domain ──────────────────────────────────────────────────────────────────

    /** Same RBAC-scoped open-criticals count as the API domain's openCriticals() — reworded for
     *  the agentic dashboard, since these are red-teaming findings against agent/MCP surfaces,
     *  not "API issues" in the inventory sense. Scoped to AGENTIC's own context collections
     *  (MCP union GenAI), so this no longer overlaps with the API dashboard's count. */
    private static Recommendation redTeamingCriticals(CONTEXT_SOURCE contextSource) {
        long count = openIssueCountBySeverity("CRITICAL", contextSource);
        return new Recommendation("open_criticals_redteam", "Critical red-teaming vulnerabilities", count, "count",
                count > 0 ? "CRITICAL" : null,
                "Of these " + count + " critical red-teaming vulnerabilities, which are likely false positives?",
                InsightRoutes.ISSUES, params("severity", Arrays.asList("CRITICAL"), "status", Arrays.asList("OPEN")));
    }

    /** Candidate collections for maliciousMcpTools()/unapprovedMcpServers(): AGENTIC's context
     *  collections (MCP union GenAI, RBAC'd and cached by
     *  UsersCollectionsList.getContextCollectionsForUser) narrowed further to real MCP servers
     *  via isMcpCollection() in Java, so a browser-based LLM chat account or other non-MCP
     *  agentic collection isn't swept in. Replaces an earlier version of this method that
     *  scanned every collection in the account with no domain filter at all — the context-scoped
     *  candidate set here is both smaller and already RBAC'd, so the projection below is now the
     *  only remaining cost worth caring about. */
    private static List<ApiCollection> loadMcpCollections(CONTEXT_SOURCE contextSource) {
        try {
            Bson filter = Filters.and(
                    Filters.in(ApiCollection.ID, contextCollectionIds(contextSource)),
                    Filters.ne(ApiCollection._DEACTIVATED, true));
            Bson projection = Projections.include(ApiCollection.TAGS_STRING, ApiCollection.HOST_NAME);
            return ApiCollectionsDao.instance.findAll(filter, 0, MCP_COLLECTION_ROW_CAP, null, projection);
        } catch (Exception e) {
            logger.error("RecommendationCatalog: loadMcpCollections failed: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /** Same contextSource filter InsightDataLoader.loadAuditRows() uses for the bundle (current
     *  contextSource, or missing entirely on legacy docs) — without this, maliciousMcpTools()/
     *  unapprovedMcpServers() would silently disagree with MaliciousComponentInUseProvider/
     *  McpSprawlProvider's counts for the same account, which is exactly the kind of drift that
     *  reads as a bug to whoever notices it first. Projected to only mcpHost/remarks/
     *  componentRiskAnalysis, the three fields actually read below. */
    private static List<McpAuditInfo> loadAuditRows() {
        try {
            String contextSourceName = Context.contextSource.get() != null ? Context.contextSource.get().name() : null;
            Bson filter = Filters.or(
                    Filters.eq(McpAuditInfo.CONTEXT_SOURCE, contextSourceName),
                    Filters.exists(McpAuditInfo.CONTEXT_SOURCE, false));
            Bson projection = Projections.include(McpAuditInfo.MCP_HOST, McpAuditInfo.REMARKS, McpAuditInfo.COMPONENT_RISK_ANALYSIS);
            return McpAuditInfoDao.instance.findAll(filter, 0, AUDIT_ROW_CAP, null, projection);
        } catch (Exception e) {
            logger.error("RecommendationCatalog: loadAuditRows failed: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    /** Distinct malicious MCP components (servers/tools) reachable right now — the
     *  RecommendationCatalog-weight version of MaliciousComponentInUseProvider's core count: an
     *  ApiCollection tagged malicious-mcp-server, or an McpAuditInfo row whose
     *  componentRiskAnalysis flags the component malicious. No invocation search, no
     *  team/device breakdown — those stay in the insight tile, which already runs this same
     *  cross-reference at CRITICAL/HIGH through the bundle. */
    private static Recommendation maliciousMcpTools(List<ApiCollection> mcpCollections, List<McpAuditInfo> auditRows) {
        long count = 0;
        try {
            Set<String> maliciousServiceNames = new HashSet<>();
            for (ApiCollection c : mcpCollections) {
                if (c.isDeactivated() || !c.isMcpCollection()) continue;
                if (InsightUtil.isMaliciousMcpServer(c)) {
                    String s = InsightUtil.serviceNameOf(c);
                    if (s != null) maliciousServiceNames.add(s.toLowerCase(Locale.ROOT));
                }
            }
            for (McpAuditInfo a : auditRows) {
                if (a.getComponentRiskAnalysis() != null && a.getComponentRiskAnalysis().getIsComponentMalicious()
                        && a.getMcpHost() != null) {
                    maliciousServiceNames.add(a.getMcpHost().toLowerCase(Locale.ROOT));
                }
            }
            count = maliciousServiceNames.size();
        } catch (Exception e) {
            logger.error("RecommendationCatalog: maliciousMcpTools failed: " + e.getMessage());
        }
        return new Recommendation("malicious_mcp_tools", "Malicious tools on local MCP servers", count, "count",
                count > 0 ? "CRITICAL" : null,
                "Which of these " + count + " malicious tools are being called by locally-hosted MCP servers?",
                InsightRoutes.AUDIT, params());
    }

    /** Local or not-allowlisted MCP servers — McpSprawlProvider's flaggedCount, without the
     *  team-breakdown/dormancy pass. "Approved" = on the allowlist, or an audit row with
     *  remarks=Approved; scope is restricted to real MCP servers via isMcpCollection() so
     *  browser-based LLM chat accounts and other agentic (non-MCP) collections aren't swept in. */
    private static Recommendation unapprovedMcpServers(List<ApiCollection> mcpCollections, List<McpAuditInfo> auditRows) {
        long count = 0;
        try {
            Set<String> approvedLower = new HashSet<>();
            List<McpAllowlist> allowlist = McpAllowlistDao.instance.findAll(Filters.empty(), Projections.include(McpAllowlist.NAME));
            for (McpAllowlist a : allowlist) {
                if (a.getName() != null) approvedLower.add(a.getName().toLowerCase(Locale.ROOT));
            }
            for (McpAuditInfo a : auditRows) {
                if (McpAuditInfo.REMARKS_APPROVED.equals(a.getRemarks()) && a.getMcpHost() != null) {
                    approvedLower.add(a.getMcpHost().toLowerCase(Locale.ROOT));
                }
            }

            for (ApiCollection c : mcpCollections) {
                if (c.isDeactivated() || !c.isMcpCollection()) continue;
                String serviceName = InsightUtil.serviceNameOf(c);
                boolean unapproved = serviceName != null && !approvedLower.contains(serviceName.toLowerCase(Locale.ROOT));
                if (InsightUtil.isLocalMcp(c) || unapproved) count++;
            }
        } catch (Exception e) {
            logger.error("RecommendationCatalog: unapprovedMcpServers failed: " + e.getMessage());
        }
        return new Recommendation("unapproved_mcp_servers", "Unapproved MCP servers", count, "count",
                count > 0 ? "MEDIUM" : null,
                "Which of these " + count + " unapproved MCP servers should be reviewed in the audit log?",
                InsightRoutes.AUDIT, params());
    }

    /** High-severity threat events in the last 30 days, from the threat-detection backend — the
     *  one recommendation that isn't a local Mongo query. Mirrors
     *  AgenticDashboardAction.fetchAllMaliciousEvents: no collection-level RBAC is applied here,
     *  matching that action's own documented decision that threat data is security-relevant and
     *  shown regardless of RBAC collection access. Degrades to a zero-count tile, not an error,
     *  on any backend failure/timeout — same as every other recommendation. */
    private static Recommendation threatActivity() {
        long count = 0;
        try {
            int nowSeconds = (int) (System.currentTimeMillis() / 1000);
            int lookbackSeconds = nowSeconds - THREAT_LOOKBACK_SECONDS;
            String contextSourceValue = Context.contextSource.get() != null ? Context.contextSource.get().toString() : "";
            ListMaliciousRequestsResponse response = ThreatDetectionBackendClient.listMaliciousRequests(
                    Context.accountId.get(), lookbackSeconds, nowSeconds, THREAT_FETCH_LIMIT, null, contextSourceValue, null);
            if (response != null) {
                for (ListMaliciousRequestsResponse.MaliciousEvent event : response.getMaliciousEventsList()) {
                    String severity = event.getSeverity();
                    if ("CRITICAL".equalsIgnoreCase(severity) || "HIGH".equalsIgnoreCase(severity)) count++;
                }
            }
        } catch (Exception e) {
            logger.error("RecommendationCatalog: threatActivity failed: " + e.getMessage());
        }
        return new Recommendation("threat_activity", "High-severity threat activity (30d)", count, "count",
                count > 0 ? "CRITICAL" : null,
                "Of these " + count + " high-severity threat events in the last 30 days, which need an immediate response?",
                InsightRoutes.THREAT_ACTIVITY, params());
    }

    // ── Endpoint domain ─────────────────────────────────────────────────────────────────

    /** Distinct malicious skills in use — the RecommendationCatalog-weight version of
     *  AgenticObserveAction.getOrBuildSkillData()'s maliciousSkills count. Deliberately uses the
     *  5-arg findAll(filter, skip, limit, sort, projection) overload, which is the one
     *  AccountsContextDaoWithRbac actually applies addRbacFilter to — getOrBuildSkillData()
     *  itself calls the 1-arg findAll(Bson), which is NOT RBAC-overridden; not fixing that here
     *  (separate file, separate feature), but not replicating the gap into new code either.
     *  Scoped to ENDPOINT's own context collections, and projected to tagsList only — ApiInfo is
     *  the fattest collection in the schema and this only ever reads getId() (free — _id is
     *  always returned) and tags. NOT index-backed: the url regex here is unanchored ("skills/" /
     *  "/config/" can appear anywhere in the path), which Mongo can't use a B-tree index for —
     *  same characteristic getOrBuildSkillData() already has, which is exactly why that method
     *  keeps a 2-minute cache around it. This tile has no cache (RecommendationCatalog's whole
     *  design is "cheap enough to run uncached"), so this is the one recommendation still worth
     *  caching if it shows up as slow in practice — the domain-collection scope at least bounds
     *  the candidate set to ENDPOINT collections rather than the whole account. */
    private static Recommendation maliciousSkillsTotal(CONTEXT_SOURCE contextSource) {
        long count = 0;
        try {
            Bson filter = Filters.and(
                    Filters.or(Filters.regex(ApiInfo.ID_URL, "skills/"), Filters.regex(ApiInfo.ID_URL, "/config/")),
                    Filters.in(ApiInfo.ID_API_COLLECTION_ID, contextCollectionIds(contextSource)),
                    Filters.nin(ApiInfo.ID_API_COLLECTION_ID, new ArrayList<>(UsageMetricCalculator.getDeactivated())));
            Bson projection = Projections.include(ApiInfo.TAGS_LIST);
            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(filter, 0, SKILL_ROW_CAP, null, projection);
            Set<String> maliciousSkills = new HashSet<>();
            for (ApiInfo info : apiInfos) {
                if (info == null || info.getId() == null) continue;
                String url = info.getId().getUrl();
                if (url == null) continue;
                int idx = url.indexOf("skills/");
                if (idx < 0) continue;
                String skillName = url.substring(idx + "skills/".length());
                if (!skillName.isEmpty() && hasTrueTag(info.getTagsList(), MALICIOUS_SKILL_TAG)) {
                    maliciousSkills.add(skillName);
                }
            }
            count = maliciousSkills.size();
        } catch (Exception e) {
            logger.error("RecommendationCatalog: maliciousSkillsTotal failed: " + e.getMessage());
        }
        return new Recommendation("malicious_skills", "Malicious skills in use", count, "count",
                count > 0 ? "CRITICAL" : null,
                "Which of these " + count + " malicious skills have actually been invoked?",
                InsightRoutes.AGENTIC_ASSETS, params());
    }

    /** Account-wide input+output token sum across UserAnalysisData rows — same source
     *  OffDomainTokenBurnProvider/ExposureConcentrationProvider already sum per-user, just
     *  totalled here instead of broken out. UserAnalysisDataDao extends AccountsContextDao (not
     *  ...WithRbac) and its rows aren't api_collection-scoped (they're per-user session
     *  aggregates), so there's no domain/RBAC collection filter available here — same as the
     *  existing insight providers that already read it unfiltered. Projected to just the two
     *  token fields this sums — no reason to pull topicHierarchy/harmfulTopics/aiSummary off the
     *  wire for a sum of two longs. */
    private static Recommendation tokensUsedTotal() {
        long count = 0;
        try {
            Bson projection = Projections.include(UserAnalysisData.TOTAL_INPUT_TOKENS, UserAnalysisData.TOTAL_OUTPUT_TOKENS);
            List<UserAnalysisData> rows = UserAnalysisDataDao.instance.findAll(Filters.empty(), projection);
            for (UserAnalysisData d : rows) {
                count += d.getTotalInputTokens() + d.getTotalOutputTokens();
            }
        } catch (Exception e) {
            logger.error("RecommendationCatalog: tokensUsedTotal failed: " + e.getMessage());
        }
        return new Recommendation("tokens_used", "Tokens used", count, "count", null,
                "What's driving the " + count + " tokens used across sessions — any single user or agent standing out?",
                InsightRoutes.USERS_AND_DEVICES, params());
    }

    /** Count of policies with active=true — GuardrailPolicies DOES carry an active flag
     *  (Lombok-generated isActive() off a plain `private boolean active`, easy to miss on a
     *  literal grep for "isActive"; InsightLazySources' sibling loadPolicies() logic already
     *  filters on it the same way). A plain Filters.empty() count here would have silently
     *  included disabled policies in an "active guardrail policies" tile — counted a real, if
     *  unindexed, field match instead. guardrail_policies is a small, per-account-configured
     *  collection (tens, not millions, of rows) that targets MCP/agent servers by name/tag
     *  rather than api_collection ids, so there's no domain-collection filter to apply here
     *  either, and the missing index on `active` isn't worth adding. */
    private static Recommendation guardrailsOverview() {
        long count = 0;
        try {
            count = GuardrailPoliciesDao.instance.count(Filters.eq("active", true));
        } catch (Exception e) {
            logger.error("RecommendationCatalog: guardrailsOverview failed: " + e.getMessage());
        }
        return new Recommendation("guardrails_overview", "Active guardrail policies", count, "count", null,
                "Give me an overview of these " + count + " guardrail policies — coverage gaps and recent activity?",
                InsightRoutes.GUARDRAIL_POLICIES, params());
    }

    // ── Shared helpers ──────────────────────────────────────────────────────────────────

    private static boolean hasTrueTag(List<CollectionTags> tags, String keyName) {
        if (tags == null) return false;
        for (CollectionTags t : tags) {
            if (t != null && keyName.equals(t.getKeyName()) && "true".equals(t.getValue())) return true;
        }
        return false;
    }

    private static Map<String, Object> params(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
