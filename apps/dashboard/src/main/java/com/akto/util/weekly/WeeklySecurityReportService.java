package com.akto.util.weekly;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.testing.TestingRunDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.Config.AktoHostUrlConfig;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.TestingRun;
import com.akto.listener.RuntimeListener;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates posture-style metrics for the last 7 days; email narrative via Cyborg getLLMResponseV2 when available.
 */
public final class WeeklySecurityReportService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WeeklySecurityReportService() {}

    public static Map<String, Object> buildReportPayload(int endEpochSeconds) {
        int startEpochSeconds = endEpochSeconds - 7 * Constants.ONE_DAY_TIMESTAMP;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("startEpochSeconds", startEpochSeconds);
        payload.put("endEpochSeconds", endEpochSeconds);

        long testingRunsCount = TestingRunDao.instance.countWithRbacAndContext(Filters.and(
                Filters.gte(TestingRun.SCHEDULE_TIMESTAMP, startEpochSeconds),
                Filters.lte(TestingRun.SCHEDULE_TIMESTAMP, endEpochSeconds)
        ));
        payload.put("testingRunsScheduledInWindow", testingRunsCount);

        long completedRuns = TestingRunDao.instance.countWithRbacAndContext(Filters.and(
                Filters.eq(TestingRun.STATE, TestingRun.State.COMPLETED),
                Filters.gt(TestingRun.END_TIMESTAMP, 0),
                Filters.gte(TestingRun.END_TIMESTAMP, startEpochSeconds),
                Filters.lte(TestingRun.END_TIMESTAMP, endEpochSeconds)
        ));
        payload.put("testingRunsCompletedInWindow", completedRuns);

        long failedRuns = TestingRunDao.instance.countWithRbacAndContext(Filters.and(
                Filters.eq(TestingRun.STATE, TestingRun.State.FAILED),
                Filters.gt(TestingRun.END_TIMESTAMP, 0),
                Filters.gte(TestingRun.END_TIMESTAMP, startEpochSeconds),
                Filters.lte(TestingRun.END_TIMESTAMP, endEpochSeconds)
        ));
        payload.put("testingRunsFailedInWindow", failedRuns);

        Map<String, Long> issuesBySeverity = new LinkedHashMap<>();
        long newIssuesTotal = 0;
        for (GlobalEnums.Severity sev : GlobalEnums.Severity.values()) {
            long c = TestingRunIssuesDao.instance.count(Filters.and(
                    Filters.gte(TestingRunIssues.CREATION_TIME, startEpochSeconds),
                    Filters.lte(TestingRunIssues.CREATION_TIME, endEpochSeconds),
                    Filters.eq(TestingRunIssues.KEY_SEVERITY, sev)
            ));
            issuesBySeverity.put(sev.name(), c);
            newIssuesTotal += c;
        }
        payload.put("newTestingIssuesBySeverity", issuesBySeverity);
        payload.put("newTestingIssuesTotal", newIssuesTotal);
        long criticalHighNew = issuesBySeverity.getOrDefault(GlobalEnums.Severity.CRITICAL.name(), 0L)
                + issuesBySeverity.getOrDefault(GlobalEnums.Severity.HIGH.name(), 0L);
        payload.put("newCriticalOrHighTestingIssuesInWindow", criticalHighNew);

        long openIssuesTotal = TestingRunIssuesDao.instance.count(
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN));
        payload.put("openTestingIssuesTotal", openIssuesTotal);

        long apiCollectionsCount = ApiCollectionsDao.instance.count(Filters.empty());
        payload.put("apiCollectionsCount", apiCollectionsCount);

        Set<Integer> demoCollections = buildDemoCollectionsForSti();
        long endpointsByEnd = SingleTypeInfoDao.instance.fetchEndpointsCount(0, endEpochSeconds, demoCollections);
        long endpointsByStart = SingleTypeInfoDao.instance.fetchEndpointsCount(0, startEpochSeconds, demoCollections);
        long epDelta = Math.max(0, endpointsByEnd - endpointsByStart);
        payload.put("newEndpointsDelta", epDelta);
        payload.put("totalEndpointsDiscoveredThroughEnd", endpointsByEnd);

        Bson apiDiscoveryFilter = Filters.and(
                UsageMetricCalculator.excludeDemosAndDeactivated(ApiInfo.ID_API_COLLECTION_ID),
                buildApiInfoDiscoveredInWindowFilter(startEpochSeconds, endEpochSeconds)
        );
        long newApisDiscovered = ApiInfoDao.instance.count(apiDiscoveryFilter);
        payload.put("newApisDiscoveredInWindow", newApisDiscovered);

        long sensitiveInWindow = ApiInfoDao.instance.count(Filters.and(
                apiDiscoveryFilter,
                Filters.eq(ApiInfo.IS_SENSITIVE, true)
        ));
        payload.put("sensitiveApisDiscoveredInWindow", sensitiveInWindow);

        int distinctHosts = countDistinctHostsFromApiInfos(apiDiscoveryFilter, 15_000);
        payload.put("distinctHostsInNewApis", distinctHosts);

        System.out.println("Weekly report payload before LLM summary: " + payload);

        applyEmailSummary(payload);

        return payload;
    }

    private static void applyEmailSummary(Map<String, Object> payload) {
        String dashboardHome = resolveDashboardHomeUrl();
        payload.put("dashboardHomeUrl", dashboardHome);
        Map<String, Object> forLlm = buildNonEmptyMetricsForLlm(payload);
        String llmBody = CyborgWeeklyReportLlmClient.getWeeklyEmailBodyFromLlm(forLlm, dashboardHome);
        System.out.println("The LLM-generated email body is: " + llmBody);
        if (StringUtils.isNotBlank(llmBody)) {
            payload.put("summaryText", llmBody);
            payload.put("summarySource", "CYBORG_GET_LLM_RESPONSE_V2");
        } else {
            payload.put("summaryText", buildSummaryText(payload));
            payload.put("summarySource", "FALLBACK_TEMPLATE");
        }
    }

    static String resolveDashboardHomeUrl() {
        String base = Constants.DEFAULT_AKTO_DASHBOARD_URL;
        if (DashboardMode.isOnPremDeployment()) {
            try {
                AktoHostUrlConfig cfg = (AktoHostUrlConfig) ConfigsDao.instance.findOne(
                        Filters.eq(Constants.ID, ConfigType.AKTO_DASHBOARD_HOST_URL.name()));
                if (cfg != null && StringUtils.isNotBlank(cfg.getHostUrl())) {
                    base = cfg.getHostUrl().trim();
                }
            } catch (Exception ignored) {
                // keep default
            }
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/dashboard/home";
    }

    /**
     * Cyborg input: include window + only metrics with data (omit numeric zeros, omit empty severity map).
     */
    private static Map<String, Object> buildNonEmptyMetricsForLlm(Map<String, Object> full) {
        Map<String, Object> out = new LinkedHashMap<>();
        Object start = full.get("startEpochSeconds");
        Object end = full.get("endEpochSeconds");
        if (start != null) {
            out.put("startEpochSeconds", start);
        }
        if (end != null) {
            out.put("endEpochSeconds", end);
        }

        copyIfPositiveLong(out, full, "testingRunsScheduledInWindow");
        copyIfPositiveLong(out, full, "testingRunsCompletedInWindow");
        copyIfPositiveLong(out, full, "testingRunsFailedInWindow");
        copyIfPositiveLong(out, full, "newTestingIssuesTotal");
        copyIfPositiveLong(out, full, "newCriticalOrHighTestingIssuesInWindow");
        copyIfPositiveLong(out, full, "openTestingIssuesTotal");
        copyIfPositiveLong(out, full, "apiCollectionsCount");
        copyIfPositiveLong(out, full, "newEndpointsDelta");
        copyIfPositiveLong(out, full, "totalEndpointsDiscoveredThroughEnd");
        copyIfPositiveLong(out, full, "newApisDiscoveredInWindow");
        copyIfPositiveLong(out, full, "sensitiveApisDiscoveredInWindow");
        copyIfPositiveLong(out, full, "distinctHostsInNewApis");

        Object rawSev = full.get("newTestingIssuesBySeverity");
        if (rawSev instanceof Map) {
            Map<String, Long> nz = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) rawSev).entrySet()) {
                long v = toLong(e.getValue());
                if (v > 0) {
                    nz.put(String.valueOf(e.getKey()), v);
                }
            }
            if (!nz.isEmpty()) {
                out.put("newTestingIssuesBySeverity", nz);
            }
        }

        return out;
    }

    private static void copyIfPositiveLong(Map<String, Object> dest, Map<String, Object> src, String key) {
        if (!src.containsKey(key)) {
            return;
        }
        long v = toLong(src.get(key));
        if (v > 0) {
            dest.put(key, v);
        }
    }

    private static long toLong(Object o) {
        if (o == null) {
            return 0L;
        }
        if (o instanceof Number) {
            return ((Number) o).longValue();
        }
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static Set<Integer> buildDemoCollectionsForSti() {
        Set<Integer> demoCollections = new HashSet<>(UsageMetricCalculator.getDeactivated());
        demoCollections.add(RuntimeListener.LLM_API_COLLECTION_ID);
        demoCollections.add(RuntimeListener.VULNERABLE_API_COLLECTION_ID);
        ApiCollection juiceshopCollection = ApiCollectionsDao.instance.findByName("juice_shop_demo");
        if (juiceshopCollection != null) {
            demoCollections.add(juiceshopCollection.getId());
        }
        return demoCollections;
    }

    /**
     * Same time semantics as {@code AgenticDashboardAction#fetchApiDiscoveryStatsInternal} for ApiInfo.
     */
    private static Bson buildApiInfoDiscoveredInWindowFilter(int startTimestamp, int endTimestamp) {
        if (startTimestamp <= 0) {
            return Filters.or(
                    Filters.and(
                            Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, true),
                            Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp)
                    ),
                    Filters.and(
                            Filters.or(
                                    Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                                    Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, 0)
                            ),
                            Filters.lte(ApiInfo.LAST_SEEN, endTimestamp)
                    )
            );
        }
        return Filters.and(
                Filters.or(
                        Filters.and(
                                Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, true),
                                Filters.gte(ApiInfo.DISCOVERED_TIMESTAMP, startTimestamp),
                                Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, endTimestamp)
                        ),
                        Filters.and(
                                Filters.or(
                                        Filters.exists(ApiInfo.DISCOVERED_TIMESTAMP, false),
                                        Filters.lte(ApiInfo.DISCOVERED_TIMESTAMP, 0)
                                ),
                                Filters.gte(ApiInfo.LAST_SEEN, startTimestamp),
                                Filters.lte(ApiInfo.LAST_SEEN, endTimestamp)
                        )
                )
        );
    }

    private static int countDistinctHostsFromApiInfos(Bson filter, int maxApisToScan) {
        Set<String> hosts = new HashSet<>();
        ApiInfoDao.instance.getMCollection()
                .find(filter)
                .projection(Projections.include(Constants.ID))
                .limit(maxApisToScan)
                .forEach(api -> {
                    if (api.getId() == null || api.getId().getUrl() == null) {
                        return;
                    }
                    String host = hostFromUrl(api.getId().getUrl());
                    if (host != null && !host.isEmpty()) {
                        hosts.add(host.toLowerCase());
                    }
                });
        return hosts.size();
    }

    private static String hostFromUrl(String url) {
        try {
            if (!url.contains("://")) {
                return null;
            }
            URI uri = new URI(url);
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public static String buildSummaryText(Map<String, Object> payload) {
        long runs = ((Number) payload.getOrDefault("testingRunsScheduledInWindow", 0)).longValue();
        long issues = ((Number) payload.getOrDefault("newTestingIssuesTotal", 0)).longValue();
        long epDelta = ((Number) payload.getOrDefault("newEndpointsDelta", 0)).longValue();
        long newApis = ((Number) payload.getOrDefault("newApisDiscoveredInWindow", 0)).longValue();
        int hosts = ((Number) payload.getOrDefault("distinctHostsInNewApis", 0)).intValue();
        Map<String, Long> sev = (Map<String, Long>) payload.get("newTestingIssuesBySeverity");
        StringBuilder sevLine = new StringBuilder();
        if (sev != null) {
            for (Map.Entry<String, Long> e : sev.entrySet()) {
                if (e.getValue() != null && e.getValue() > 0) {
                    if (sevLine.length() > 0) {
                        sevLine.append(", ");
                    }
                    sevLine.append(e.getKey()).append(": ").append(e.getValue());
                }
            }
        }
        return "In the last 7 days: " + runs + " test run(s) were scheduled; " + issues
                + " new testing issue(s) were recorded"
                + (sevLine.length() > 0 ? " (" + sevLine + ")" : "")
                + ". Inventory grew by approximately " + epDelta + " endpoint(s) (STI-based delta vs prior week boundary). "
                + newApis + " API info record(s) matched discovery activity in this window across "
                + hosts + " distinct host(s) (sampled).";
    }

    public static String buildHtmlEmailBody(Map<String, Object> payload) {
        String summary = String.valueOf(payload.getOrDefault("summaryText", ""));
        summary = summary.replace("**", "");
        String summaryHtml = escapeHtml(summary).replace("\n", "<br/>");
        StringBuilder html = new StringBuilder();
        html.append("<html><body style=\"font-family:system-ui,sans-serif;font-size:14px;color:#111;\">");
        html.append("<p>").append(summaryHtml).append("</p>");
        html.append("<p style=\"color:#666;font-size:12px;\">Sent from Akto.</p></body></html>");
        return html.toString();
    }

    private static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
