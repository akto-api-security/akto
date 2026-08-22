package com.akto.service.insights;

import com.akto.action.threat_detection.HostSeverityCount;
import com.akto.action.threat_detection.ThreatCategoryCount;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
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
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);
    private static final int EXTERNAL_CALL_TIMEOUT_SECONDS = 8;

    public InsightDataBundle load(InsightContext ctx) {
        // Threat-backend calls run in worker threads — Context ThreadLocals must be
        // captured here and re-set inside each task, or the worker queries the wrong
        // account (see the plan's "Correctness traps": this is the single largest bug risk).
        final int accountId = ctx.getAccountId();
        final Integer userId = ctx.getUserId();
        final CONTEXT_SOURCE contextSource = ctx.getContextSource();
        InsightsThreatBackendAccess threatAccess = new InsightsThreatBackendAccess();

        Future<List<HostSeverityCount>> hostSeverityFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                () -> threatAccess.hostSeverityCounts(ctx.getStartTs(), ctx.getEndTs())));
        Future<List<ThreatCategoryCount>> subCategoryFuture = EXECUTOR.submit(withContext(accountId, userId, contextSource,
                () -> threatAccess.subcategoryWiseCounts(ctx.getStartTs(), ctx.getEndTs())));

        List<ApiCollection> collections = ApiCollectionsDao.instance.findAll(
                Filters.empty(),
                Projections.include(ApiCollection.ID, ApiCollection.HOST_NAME, ApiCollection.TAGS_STRING,
                        ApiCollection.SKILLS, ApiCollection.START_TS, ApiCollection.BASE_RISK_SCORE,
                        ApiCollection.BASE_RISK_SCORE_REASON, ApiCollection.DESCRIPTION, ApiCollection._DEACTIVATED));
        Map<String, List<ApiCollection>> collectionsByServiceName = indexByServiceName(collections);

        Map<String, String> deviceIdToUsername = loadDeviceIdToUsername();
        Map<String, List<DeviceTag>> userTags = loadUserTags();
        List<McpAuditInfo> auditRows = loadAuditRows(contextSource);
        List<GuardrailPolicies> policies = loadPolicies();
        Set<String> allowlistNamesLower = loadAllowlistNames();
        Map<Integer, List<String>> sensitiveByCollection = loadSensitiveByCollection(collections);
        List<UserAnalysisData> userAnalysis = loadUserAnalysis();
        List<NhiIdentity> nhiIdentities = loadNhiIdentities();

        boolean threatBackendAvailable = true;
        List<HostSeverityCount> hostSeverityCounts;
        List<ThreatCategoryCount> subCategoryCounts;
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

        return new InsightDataBundle(ctx, collections, collectionsByServiceName, deviceIdToUsername, userTags,
                auditRows, policies, allowlistNamesLower, sensitiveByCollection, userAnalysis, nhiIdentities,
                hostSeverityCounts, subCategoryCounts, threatBackendAvailable, threatAccess);
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
                    p.setApplyToDeviceIds(AgentUsersDao.instance.findDeviceIdsByTags(p.getTargetTags(), p.getTargetDeviceIds()));
                }
                active.add(p);
            }
            return active;
        } catch (Exception e) {
            logger.error("InsightDataLoader: loadPolicies failed: " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private Set<String> loadAllowlistNames() {
        try {
            List<McpAllowlist> rows = McpAllowlistDao.instance.findAll(Filters.empty(), Projections.include(McpAllowlist.NAME));
            Set<String> names = new HashSet<>();
            for (McpAllowlist a : rows) {
                if (a.getName() != null) names.add(a.getName().toLowerCase(Locale.ROOT));
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
}
