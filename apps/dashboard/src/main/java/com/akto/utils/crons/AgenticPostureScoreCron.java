package com.akto.utils.crons;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.AgenticPostureScoreHistoryDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.GuardrailPoliciesDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.Account;
import com.akto.dto.AgenticPostureScoreHistory;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.service.insights.HostCollectionResolver;
import com.akto.service.insights.InsightUtil;
import com.akto.task.Cluster;
import com.akto.util.AccountTask;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.akto.task.Cluster.callDibs;

// Hourly per-agent posture score: six 0-100 categories, weighted-averaged into ApiCollection.postureScore,
// plus one account-wide AgenticPostureScoreHistory row per run.
public class AgenticPostureScoreCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgenticPostureScoreCron.class, LogDb.DASHBOARD);

    private static final int PER_ACCOUNT_LIMIT = 10000;
    private static final int MALICIOUS_EVENTS_WINDOW_SECONDS = 90 * 86400;
    private static final int MAX_MALICIOUS_EVENTS = 100_000;

    private static final int POINTS_RED_TEAM             = 30;
    private static final int POINTS_GUARDRAIL_MALICIOUS  = 30;
    private static final int POINTS_COVERAGE             = 10;
    private static final int POINTS_SENSITIVE_DATA       = 10;
    private static final int POINTS_ACCESS_AUTH          = 10;
    private static final int POINTS_OVERPRIVILEGED_TOOLS = 10;

    // Follows ToolCapabilityClassifier's severity order.
    private static final Map<String, Integer> CAPABILITY_POINTS = new HashMap<>();
    static {
        CAPABILITY_POINTS.put("RESOURCE_DELETE", 100);
        CAPABILITY_POINTS.put("CREDENTIAL_OR_PII_READ", 85);
        CAPABILITY_POINTS.put("CRITICAL_RESOURCE_WRITE", 70);
        CAPABILITY_POINTS.put("FILE_WRITE", 55);
        CAPABILITY_POINTS.put("SAFE", 0);
    }

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void setUpAgenticPostureScoreCronScheduler() {
        scheduler.scheduleWithFixedDelay(this::run, 0, 60, TimeUnit.MINUTES);
    }

    private void run() {
        try {
            Context.accountId.set(1_000_000);
            if (!callDibs(Cluster.AGENTIC_POSTURE_SCORE_CRON_INFO, 3300, 60)) {
                loggerMaker.debugAndAddToDb("Agentic posture score cron dibs not acquired, thus skipping cron");
                return;
            }
            AccountTask.instance.executeTask(this::processAccount, "agentic-posture-score-cron");
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in agentic posture score cron: " + e.getMessage());
        }
    }

    private void processAccount(Account account) {
        int accountId = account.getId();
        try {
            Context.accountId.set(accountId);
            // Argus reads, same context the posture page's own requests run in; also sent to the threat backend as x-context-source.
            Context.contextSource.set(CONTEXT_SOURCE.AGENTIC);

            List<ApiCollection> agentCollections = findAgentCollections();
            if (agentCollections.isEmpty()) {
                return;
            }

            List<Integer> collectionIds = new ArrayList<>();
            for (ApiCollection c : agentCollections) collectionIds.add(c.getId());

            List<GuardrailPolicies> policies = GuardrailPoliciesDao.instance.findAllSortedByCreatedTimestamp(0, 5000);

            BasicDBObject groupedId = new BasicDBObject(SingleTypeInfo._API_COLLECTION_ID, "$" + TestingRunIssues.ID_API_COLLECTION_ID)
                    .append(TestingRunIssues.KEY_SEVERITY, "$" + TestingRunIssues.KEY_SEVERITY);
            Map<Integer, Map<String, Integer>> redTeamSeverities = TestingRunIssuesDao.instance.getSeveritiesMapForCollections(
                    Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, collectionIds), false, groupedId);
            // Only picks the wording of the "not scanned" gap message.
            boolean anyRedTeamScanEverRun = TestingRunDao.instance.findOne(
                    Filters.eq(TestingRun.DASHBOARD_CONTEXT, CONTEXT_SOURCE.AGENTIC)) != null;

            List<ApiInfo> apiInfos = ApiInfoDao.instance.findAll(Filters.in(ApiInfo.ID_API_COLLECTION_ID, collectionIds),
                    Projections.include(ApiInfo.LAST_TESTED, ApiInfo.API_ACCESS_TYPES,
                            ApiInfo.ALL_AUTH_TYPES_FOUND, ApiInfo.TOOL_INFO));
            Map<Integer, List<ApiInfo>> apiInfosByCollection = new HashMap<>();
            for (ApiInfo a : apiInfos) {
                if (a == null || a.getId() == null) continue;
                apiInfosByCollection.computeIfAbsent(a.getId().getApiCollectionId(), k -> new ArrayList<>()).add(a);
            }
            // Warms the in-memory custom data type cache so TOKEN/USERNAME etc. count as sensitive.
            SingleTypeInfo.fetchCustomDataTypes(accountId);
            Map<Integer, List<String>> sensitiveByCollection = SingleTypeInfoDao.instance.getSensitiveSubtypesDetectedForCollection(null);

            int now = Context.now();
            // Attributed to agents by host, then actor (see HostCollectionResolver.resolveEvent).
            List<DashboardMaliciousEvent> hostCounts = new AbstractThreatDetectionAction().fetchAllMaliciousEvents(
                    now - MALICIOUS_EVENTS_WINDOW_SECONDS, now, MAX_MALICIOUS_EVENTS, null, null, true);
            Map<Integer, Map<String, Integer>> maliciousSeverities =
                    new HostCollectionResolver(agentCollections).severityByCollection(hostCounts);
            List<WriteModel<ApiCollection>> updates = new ArrayList<>();
            double scoredSum = 0;
            for (ApiCollection c : agentCollections) {
                List<ApiInfo> apis = apiInfosByCollection.getOrDefault(c.getId(), new ArrayList<>());
                // An open red-team finding proves a scan ran even when ApiInfo.lastTested wasn't stamped.
                boolean collectionEverTested = redTeamSeverities.containsKey(c.getId())
                        || apis.stream().anyMatch(a -> a != null && a.getLastTested() > 0);
                boolean coveredByPolicy = isCoveredByGuardrailPolicy(policies, c);

                double redTeam = worstSeverityScore(redTeamSeverities.get(c.getId()));
                double guardrailMalicious = worstSeverityScore(maliciousSeverities.get(c.getId()));
                double coverage = coverageSubScore(coveredByPolicy, collectionEverTested);
                double sensitiveData = sensitiveDataSubScore(c.getId(), sensitiveByCollection);
                double accessAuth = accessAuthSubScore(apis);
                double overprivilegedTools = overprivilegedToolsSubScore(apis);

                Map<String, Object> subScores = new HashMap<>();
                subScores.put("redTeam", redTeam);
                subScores.put("guardrailMalicious", guardrailMalicious);
                subScores.put("coverage", coverage);
                subScores.put("sensitiveData", sensitiveData);
                subScores.put("accessAuth", accessAuth);
                subScores.put("overprivilegedTools", overprivilegedTools);

                Map<String, String> gaps = new HashMap<>();
                if (!collectionEverTested) {
                    gaps.put("redTeam", anyRedTeamScanEverRun
                            ? "Red-teaming scan not run for this agent"
                            : "No red-teaming scans have been run for this account yet");
                }

                double composite = agentComposite(redTeam, guardrailMalicious, coverage, sensitiveData, accessAuth, overprivilegedTools);
                scoredSum += composite;

                updates.add(new UpdateOneModel<>(
                        Filters.eq(ApiCollection.ID, c.getId()),
                        Updates.combine(
                                Updates.set(ApiCollection.POSTURE_SCORE, composite),
                                Updates.set(ApiCollection.POSTURE_SUB_SCORES, subScores),
                                Updates.set(ApiCollection.POSTURE_GAPS, gaps),
                                Updates.set(ApiCollection.POSTURE_SCORE_CALCULATED_AT, now)
                        ),
                        new UpdateOptions().upsert(false)
                ));
            }

            if (!updates.isEmpty()) {
                ApiCollectionsDao.instance.bulkWrite(updates, new BulkWriteOptions().ordered(false));
                loggerMaker.infoAndAddToDb("Agentic posture score cron scored " + updates.size()
                        + " agent collections for accountId=" + accountId);
            }

            // Every agent always gets a composite, so agentsWithNoSignal is 0.
            AgenticPostureScoreHistoryDao.instance.insertOne(
                    new AgenticPostureScoreHistory(scoredSum / agentCollections.size(), agentCollections.size(), 0, now));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error in agentic posture score cron for accountId=" + accountId + ": " + e.getMessage());
        }
    }

    // Same gate as UsersCollectionsList#getContextCollections(AGENTIC); crons have no request to apply it.
    private List<ApiCollection> findAgentCollections() {
        List<ApiCollection> candidates = ApiCollectionsDao.instance.findAll(
                Filters.or(Filters.exists(ApiCollection._DEACTIVATED, false), Filters.eq(ApiCollection._DEACTIVATED, false)));
        List<ApiCollection> agents = new ArrayList<>();
        for (ApiCollection c : candidates) {
            if (c == null) continue;
            if ((c.isMcpCollection() || c.isGenAICollection()) && !c.isEndpointCollection()) {
                agents.add(c);
            }
            if (agents.size() >= PER_ACCOUNT_LIMIT) break;
        }
        return agents;
    }

    // Weighted average of six 0-100 sub-scores.
    private static double agentComposite(double redTeam, double guardrailMalicious, double coverage,
                                          double sensitiveData, double accessAuth, double overprivilegedTools) {
        double earned = POINTS_RED_TEAM * (redTeam / 100.0)
                + POINTS_GUARDRAIL_MALICIOUS * (guardrailMalicious / 100.0)
                + POINTS_COVERAGE * (coverage / 100.0)
                + POINTS_SENSITIVE_DATA * (sensitiveData / 100.0)
                + POINTS_ACCESS_AUTH * (accessAuth / 100.0)
                + POINTS_OVERPRIVILEGED_TOOLS * (overprivilegedTools / 100.0);
        double available = POINTS_RED_TEAM + POINTS_GUARDRAIL_MALICIOUS + POINTS_COVERAGE
                + POINTS_SENSITIVE_DATA + POINTS_ACCESS_AUTH + POINTS_OVERPRIVILEGED_TOOLS;
        return (earned / available) * 100.0;
    }

    // Severity of the worst finding, so extra low-severity findings never dilute a high one; 0 when none.
    private static double worstSeverityScore(Map<String, Integer> bySeverity) {
        if (bySeverity == null) return 0.0;
        int worst = 0;
        for (Map.Entry<String, Integer> e : bySeverity.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) worst = Math.max(worst, severityWeight(e.getKey()));
        }
        return worst;
    }

    private static int severityWeight(String severity) {
        if (severity == null) return 0;
        switch (severity) {
            case "CRITICAL":
            case "HIGH":
                return 100;
            case "MEDIUM":
                return 50;
            case "LOW":
                return 25;
            default:
                return 0;
        }
    }

    private static boolean isCoveredByGuardrailPolicy(List<GuardrailPolicies> policies, ApiCollection c) {
        for (GuardrailPolicies p : policies) {
            // null device ids: no device-level targeting on this branch, host coverage only.
            if (p != null && InsightUtil.policyCoversCollection(p, null, c)) return true;
        }
        return false;
    }

    // +50 if no guardrail policy covers it, +50 if never red-team scanned.
    private static double coverageSubScore(boolean coveredByPolicy, boolean collectionEverTested) {
        double score = 0;
        if (!coveredByPolicy) score += 50;
        if (!collectionEverTested) score += 50;
        return score;
    }

    // Most dangerous classified tool capability; 0 if no tools are classified yet.
    private static double overprivilegedToolsSubScore(List<ApiInfo> apis) {
        int maxPoints = 0;
        for (ApiInfo a : apis) {
            if (a == null || a.getToolInfo() == null || a.getToolInfo().getCapability() == null) continue;
            Integer points = CAPABILITY_POINTS.get(a.getToolInfo().getCapability());
            if (points != null && points > maxPoints) maxPoints = points;
        }
        return maxPoints;
    }

    // +50 if any endpoint is PUBLIC, +50 if any endpoint is unauthenticated or has no auth recorded.
    private static double accessAuthSubScore(List<ApiInfo> apis) {
        boolean isPublic = false;
        boolean isUnauthenticated = false;
        for (ApiInfo a : apis) {
            if (a == null) continue;
            if (a.getApiAccessTypes() != null && a.getApiAccessTypes().contains(ApiInfo.ApiAccessType.PUBLIC)) {
                isPublic = true;
            }
            if (a.getAllAuthTypesFound() == null) {
                isUnauthenticated = true;
                continue;
            }
            a.calculateActualAuth();
            List<String> authTypes = a.getActualAuthType();
            if (authTypes == null || authTypes.isEmpty() || authTypes.contains(ApiInfo.AuthType.UNAUTHENTICATED)) {
                isUnauthenticated = true;
            }
        }
        double score = 0;
        if (isPublic) score += 50;
        if (isUnauthenticated) score += 50;
        return score;
    }

    // Same source as the inventory page's "Sensitive data" column.
    private static double sensitiveDataSubScore(int collectionId, Map<Integer, List<String>> sensitiveByCollection) {
        List<String> subtypes = sensitiveByCollection.get(collectionId);
        return (subtypes == null || subtypes.isEmpty()) ? 0.0 : 100.0;
    }

    public void forceRunForAccount(int accountId) {
        Account account = com.akto.dao.AccountsDao.instance.findOne(Filters.eq(com.akto.util.Constants.ID, accountId));
        if (account == null) {
            loggerMaker.errorAndAddToDb("forceRunForAccount: no account found for accountId=" + accountId);
            return;
        }
        processAccount(account);
    }
}
