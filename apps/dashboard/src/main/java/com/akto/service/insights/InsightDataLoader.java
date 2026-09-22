package com.akto.service.insights;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.SkillSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.McpAllowlistDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.agentic_sessions.UserAnalysisDataDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dao.nhi_governance.NhiIdentityDao;
import com.akto.dto.AgenticUsers;
import com.akto.dto.ApiCollection;
import com.akto.dto.DeviceTag;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAllowlist;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.agentic_sessions.UserAnalysisData;
import com.akto.dto.nhi_governance.NhiIdentity;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Builds one InsightDataBundle per request. Performs each underlying read exactly once
 * so the list endpoint (10 providers) doesn't re-scan api_collections ten times.
 *
 * Deliberately does NOT reuse AgenticObserveAction.getOrBuildClassification() — that is
 * a private, static, 60s-TTL cache that a caller with empty traffic/risk/sensitive maps
 * can poison for every other caller for the rest of the TTL window. This loader builds
 * its own view, reusing only the stateless helpers in AgenticObserveUtil/InsightUtil.
 *
 * Every Mongo read below catches its own exception, logs, and returns an empty
 * collection — matching the "swallow and return empty" convention already used by
 * AbstractThreatDetectionAction and ElasticSearchClient, rather than a bespoke
 * success/failure wrapper type. The one exception is threatBackendAvailable: the threat
 * backend is genuinely expected to be flaky sometimes, and a couple of providers need to
 * tell "confirmed zero violations" apart from "couldn't check" — everything else here is
 * our own database, where a failure is exceptional enough that treating it as "zero" for
 * this one feature is an acceptable simplification.
 */
public class InsightDataLoader {

    private static final LoggerMaker logger = new LoggerMaker(InsightDataLoader.class, LogDb.DASHBOARD);
    // Up to 13 tasks can be in flight at once from a single load() call (10 Mongo reads + 3
    // threat-backend calls, see below) — sized to fit that without queueing, since every one of
    // them is I/O-bound (waiting on Mongo/HTTP, not CPU), not the "one thread per CPU core" case.
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(16);
    private static final int EXTERNAL_CALL_TIMEOUT_SECONDS = 8;

    public InsightDataBundle load(InsightContext ctx) {
        long loadStart = System.currentTimeMillis();
        // Threat-backend calls run in worker threads — Context ThreadLocals must be
        // captured here and re-set inside each task, or the worker queries the wrong
        // account (see the plan's "Correctness traps": this is the single largest bug risk).
        final int accountId = ctx.getAccountId();
        final Integer userId = ctx.getUserId();
        final CONTEXT_SOURCE contextSource = ctx.getContextSource();
        InsightsThreatBackendAccess threatAccess = new InsightsThreatBackendAccess();

        Future<List<HostSeverityCount>> hostSeverityFuture = submitTimed(accountId, userId, contextSource,
                "hostSeverityFuture (threat backend)", () -> threatAccess.hostSeverityCounts(ctx.getStartTs(), ctx.getEndTs()), List::size);
        Future<List<ThreatCategoryCount>> subCategoryFuture = submitTimed(accountId, userId, contextSource,
                "subCategoryFuture (threat backend)", () -> threatAccess.subcategoryWiseCounts(ctx.getStartTs(), ctx.getEndTs()), List::size);
        Future<List<SkillSeverityCount>> skillSeverityFuture = submitTimed(accountId, userId, contextSource,
                "skillSeverityFuture (threat backend)", () -> threatAccess.skillSeverityCounts(ctx.getStartTs(), ctx.getEndTs()), List::size);

        // collections/activeCollections run first and synchronously — cheap on their own (tens of
        // ms), but sensitiveByCollection/collectionLastTrafficSeen below need their results, so
        // they can't be dispatched until these two resolve. Every other step here is independent
        // of every other one, so all nine go out as futures together right after.
        long t0 = System.currentTimeMillis();
        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                Filters.empty(),
                Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING,
                        ApiCollection.SKILLS, ApiCollection.START_TS, ApiCollection.BASE_RISK_SCORE,
                        ApiCollection.BASE_RISK_SCORE_REASON, ApiCollection.DESCRIPTION, ApiCollection._DEACTIVATED));
        logStep("collections (findAll, unbounded)", t0, collections.size());
        Map<String, List<ApiCollection>> collectionsByServiceName = indexByServiceName(collections);

        t0 = System.currentTimeMillis();
        List<ApiCollection> activeCollections = loadActiveCollections();
        logStep("activeCollections", t0, activeCollections.size());

        Future<Map<String, String>> deviceIdToUsernameFuture =
                submitTimed(accountId, userId, contextSource, "deviceIdToUsername", this::loadDeviceIdToUsername, Map::size);
        Future<Map<String, List<DeviceTag>>> userTagsFuture =
                submitTimed(accountId, userId, contextSource, "userTags (AgentUsersDao.findAll, unbounded)", this::loadUserTags, Map::size);
        Future<List<McpAuditInfo>> auditRowsFuture = submitTimed(accountId, userId, contextSource,
                "auditRows (limit 5000)", () -> loadAuditRows(contextSource), List::size);
        Future<List<GuardrailPolicies>> policiesFuture = submitTimed(accountId, userId, contextSource,
                "policies (limit 5000 + per-policy device-tag resolution)", this::loadPolicies, List::size);
        Future<Set<String>> allowlistNamesLowerFuture = submitTimed(accountId, userId, contextSource,
                "allowlistNamesLower (McpAllowlistDao.findAll, unbounded)", this::loadAllowlistNames, Set::size);
        Future<Map<Integer, List<String>>> sensitiveByCollectionFuture = submitTimed(accountId, userId, contextSource,
                "sensitiveByCollection (3x SingleTypeInfoDao scans + per-collection lookup)",
                () -> loadSensitiveByCollection(collections), Map::size);
        Future<List<UserAnalysisData>> userAnalysisFuture = submitTimed(accountId, userId, contextSource,
                "userAnalysis (UserAnalysisDataDao.findAll, unbounded)", this::loadUserAnalysis, List::size);
        Future<List<NhiIdentity>> nhiIdentitiesFuture = submitTimed(accountId, userId, contextSource,
                "nhiIdentities (NhiIdentityDao.findAll, unbounded)", this::loadNhiIdentities, List::size);
        Future<Map<Integer, Integer>> collectionLastTrafficSeenFuture = submitTimed(accountId, userId, contextSource,
                "collectionLastTrafficSeen", () -> loadCollectionLastTrafficSeen(activeCollections), Map::size);

        Map<String, String> deviceIdToUsername = getOrEmpty(deviceIdToUsernameFuture, new HashMap<>(), "deviceIdToUsername");
        Map<String, List<DeviceTag>> userTags = getOrEmpty(userTagsFuture, new HashMap<>(), "userTags");
        List<McpAuditInfo> auditRows = getOrEmpty(auditRowsFuture, Collections.emptyList(), "auditRows");
        List<GuardrailPolicies> policies = getOrEmpty(policiesFuture, Collections.emptyList(), "policies");
        Set<String> allowlistNamesLower = getOrEmpty(allowlistNamesLowerFuture, Collections.emptySet(), "allowlistNamesLower");
        Map<Integer, List<String>> sensitiveByCollection = getOrEmpty(sensitiveByCollectionFuture, Collections.emptyMap(), "sensitiveByCollection");
        List<UserAnalysisData> userAnalysis = getOrEmpty(userAnalysisFuture, Collections.emptyList(), "userAnalysis");
        List<NhiIdentity> nhiIdentities = getOrEmpty(nhiIdentitiesFuture, Collections.emptyList(), "nhiIdentities");
        Map<Integer, Integer> collectionLastTrafficSeen = getOrEmpty(collectionLastTrafficSeenFuture, Collections.emptyMap(), "collectionLastTrafficSeen");

        boolean threatBackendAvailable = true;
        List<HostSeverityCount> hostSeverityCounts;
        List<ThreatCategoryCount> subCategoryCounts;
        List<SkillSeverityCount> skillSeverityCounts;
        try {
            hostSeverityCounts = hostSeverityFuture.get(EXTERNAL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("InsightDataLoader: host severity counts call failed/timed out: " + e.getMessage());
            hostSeverityCounts = Collections.emptyList();
            threatBackendAvailable = false;
        }
        try {
            subCategoryCounts = subCategoryFuture.get(EXTERNAL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("InsightDataLoader: subcategory counts call failed/timed out: " + e.getMessage());
            subCategoryCounts = Collections.emptyList();
            threatBackendAvailable = false;
        }
        try {
            skillSeverityCounts = skillSeverityFuture.get(EXTERNAL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("InsightDataLoader: skill severity counts call failed/timed out: " + e.getMessage());
            skillSeverityCounts = Collections.emptyList();
            threatBackendAvailable = false;
        }

        logger.info("InsightDataLoader: load() total " + (System.currentTimeMillis() - loadStart)
                + "ms for accountId=" + accountId);

        return new InsightDataBundle(ctx, collections, collectionsByServiceName, deviceIdToUsername, userTags,
                auditRows, policies, allowlistNamesLower, sensitiveByCollection, userAnalysis, nhiIdentities,
                hostSeverityCounts, subCategoryCounts, skillSeverityCounts, threatBackendAvailable,
                activeCollections, collectionLastTrafficSeen, threatAccess);
    }

    /** Submits one load() step to run concurrently with every other one, timing its actual work
     *  (not the caller's wait for it, since these are all dispatched together) and logging the row
     *  count it produced — the row count is what tells "slow because unindexed" apart from "slow
     *  because genuinely large", which is exactly what decides whether the fix is an index/
     *  projection or a limit/skip-based page size. Context ThreadLocals are re-set the same way
     *  the three threat-backend futures already do. */
    private <T> Future<T> submitTimed(int accountId, Integer userId, CONTEXT_SOURCE contextSource, String label,
                                       Callable<T> body, java.util.function.ToIntFunction<T> rowCount) {
        return EXECUTOR.submit(withContext(accountId, userId, contextSource, () -> {
            long t0 = System.currentTimeMillis();
            T result = body.call();
            logStep(label, t0, rowCount.applyAsInt(result));
            return result;
        }));
    }

    /** Every one of submitTimed's own callables already catches its own exceptions and returns an
     *  empty collection (same "log and return empty" convention as the rest of this class) — this
     *  only guards the future itself timing out or being interrupted, which would otherwise fail
     *  the whole bundle for one slow/stuck Mongo call. */
    private <T> T getOrEmpty(Future<T> future, T empty, String label) {
        try {
            return future.get(EXTERNAL_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.error("InsightDataLoader: " + label + " future failed/timed out: " + e.getMessage());
            return empty;
        }
    }

    private void logStep(String label, long startMs, int rowCount) {
        logger.info("InsightDataLoader: " + label + " took " + (System.currentTimeMillis() - startMs)
                + "ms, " + rowCount + " rows");
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

    private Map<String, List<ApiCollection>> indexByServiceName(List<ApiCollection> collections) {
        Map<String, List<ApiCollection>> out = new HashMap<>();
        for (ApiCollection c : collections) {
            String serviceName = AgenticObserveUtil.extractServiceName(c.getHostName());
            if (StringUtils.isNotBlank(serviceName)) {
                out.computeIfAbsent(serviceName.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(c);
            }
        }
        return out;
    }

    private Map<String, String> loadDeviceIdToUsername() {
        Map<String, String> out = new HashMap<>();
        try {
            Map<String, Set<String>> byUser = ModuleInfoDao.instance.fetchUsernameToDeviceIdsForEndpointShield();
            for (Map.Entry<String, Set<String>> e : byUser.entrySet()) {
                for (String deviceId : e.getValue()) out.put(deviceId, e.getKey());
            }
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadDeviceIdToUsername failed: " + e.getMessage());
        }
        return out;
    }

    private Map<String, List<DeviceTag>> loadUserTags() {
        Map<String, List<DeviceTag>> out = new HashMap<>();
        try {
            List<AgenticUsers> users = AgentUsersDao.instance.findAll(Filters.empty(),
                    Projections.include(AgenticUsers.USER_NAME, AgenticUsers.DEVICE_TAGS));
            for (AgenticUsers u : users) {
                if (u.getUserName() != null) out.put(u.getUserName(), u.getDeviceTags() != null ? u.getDeviceTags() : new ArrayList<>());
            }
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadUserTags failed: " + e.getMessage());
        }
        return out;
    }

    /** contextSource == current, or the field is entirely missing (legacy docs predate it). */
    private List<McpAuditInfo> loadAuditRows(CONTEXT_SOURCE contextSource) {
        try {
            Bson filter = Filters.or(
                    Filters.eq(McpAuditInfo.CONTEXT_SOURCE, contextSource != null ? contextSource.name() : null),
                    Filters.exists(McpAuditInfo.CONTEXT_SOURCE, false));
            return McpAuditInfoDao.instance.findAll(filter, 0, 5000, null);
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadAuditRows failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Reuses GuardrailPoliciesDao's own contextSource filter, then resolves device targeting per policy. */
    private List<GuardrailPolicies> loadPolicies() {
        try {
            List<GuardrailPolicies> all = GuardrailPoliciesDao.instance.findAllSortedByCreatedTimestamp(0, 5000);
            List<GuardrailPolicies> active = new ArrayList<>();
            for (GuardrailPolicies p : all) {
                if (!p.isActive()) continue;
                boolean hasTagTargeting = p.getTargetTags() != null && !p.getTargetTags().isEmpty();
                boolean hasTargeting = hasTagTargeting || (p.getTargetDeviceIds() != null && !p.getTargetDeviceIds().isEmpty());
                if (hasTargeting) {
                    p.setApplyToDeviceIds(AgentUsersDao.instance.findDeviceIdsByTags(
                            p.getTargetTags(), p.getNegatedTargetTags(), p.getTargetDeviceIds(), p.isNegatedTargetDeviceIds()));
                }
                active.add(p);
            }
            return active;
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadPolicies failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * VENDOR-typed entries are canonicalized the same way InsightUtil#endpointVendorName
     * canonicalizes observed traffic (claude/claude-desktop/... -> anthropic, etc.) — otherwise an
     * approval saved under a raw alias ("chatgpt.com") would never match traffic that resolves to
     * the merged canonical name ("openai"), and would silently read back as still-unapproved.
     * MCP_SERVER-typed entries are left as-is: that alias map is vendor-specific and would
     * misclassify an unrelated MCP server whose name happens to contain one of those substrings
     * (e.g. "claude-mcp-server").
     */
    private Set<String> loadAllowlistNames() {
        try {
            List<McpAllowlist> rows = McpAllowlistDao.instance.findAll(Filters.empty(),
                    Projections.include(McpAllowlist.NAME, McpAllowlist.ENTRY_TYPE));
            Set<String> names = new HashSet<>();
            for (McpAllowlist a : rows) {
                if (a.getName() == null) continue;
                String nameLower = a.getName().toLowerCase(Locale.ROOT);
                boolean isVendor = McpAllowlist.ENTRY_TYPE_VENDOR.equals(a.getEntryType());
                // canonicalVendorName returns a display-cased name ("OpenAI", "Anthropic") for use
                // as a UI label elsewhere (RiskScoreCalculator's vendor table) — this set is
                // strictly lowercase (every consumer lowercases its query side before checking
                // membership), so the canonicalized name must be lowered again here or a vendor
                // approval would never match.
                names.add(isVendor ? InsightUtil.canonicalVendorName(nameLower).toLowerCase(Locale.ROOT) : nameLower);
            }
            return names;
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadAllowlistNames failed: " + e.getMessage());
            return Collections.emptySet();
        }
    }

    private Map<Integer, List<String>> loadSensitiveByCollection(List<ApiCollection> collections) {
        try {
            List<String> subtypes = new ArrayList<>();
            subtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeInRequestNames());
            subtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeNames());
            subtypes.addAll(SingleTypeInfoDao.instance.sensitiveSubTypeInResponseNames());
            List<Integer> ids = new ArrayList<>();
            for (ApiCollection c : collections) ids.add(c.getId());
            return SingleTypeInfoDao.instance.getSensitiveSubtypesDetectedForCollections(subtypes, ids);
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadSensitiveByCollection failed: " + e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<UserAnalysisData> loadUserAnalysis() {
        try {
            return UserAnalysisDataDao.instance.findAll(Filters.empty());
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadUserAnalysis failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<NhiIdentity> loadNhiIdentities() {
        try {
            return NhiIdentityDao.instance.findAll(Filters.empty());
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadNhiIdentities failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * A narrower projection than `collections` above (id/hostName/startTs only) — policy-coverage
     * checking (PolicyHygieneProvider) needs no traffic/risk/tag data, and mirrors
     * ApiCollectionsDao.fetchAllActiveHosts()'s own filter (hostName exists, not deactivated)
     * rather than inventing a different one.
     */
    private List<ApiCollection> loadActiveCollections() {
        try {
            Bson filter = Filters.and(Filters.exists(ApiCollection.HOST_NAME, true), Filters.ne(ApiCollection._DEACTIVATED, true));
            return ApiCollectionsDao.instance.findAll(filter,
                    Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.START_TS));
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadActiveCollections failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    /** Scoped to just the collections already loaded above, rather than ApiInfoDao's unscoped
     *  getLastTrafficSeen() (whole account) — cheaper, and this is all PolicyHygieneProvider needs. */
    private Map<Integer, Integer> loadCollectionLastTrafficSeen(List<ApiCollection> activeCollections) {
        try {
            List<Integer> ids = new ArrayList<>();
            for (ApiCollection c : activeCollections) ids.add(c.getId());
            return ApiInfoDao.instance.getLastTrafficSeenForCollections(ids);
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadCollectionLastTrafficSeen failed: " + e.getMessage());
            return Collections.emptyMap();
        }
    }
}
