package com.akto.action;

import com.akto.action.threat_detection.AbstractThreatDetectionAction;
import com.akto.action.threat_detection.DashboardMaliciousEvent;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.test_editor.Info;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.AgenticObserveUtil;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Violations for agentic observe flyouts and assets table.
 * Inventory grouping uses existing collection APIs on the frontend.
 */
public class AgenticObserveAction extends AbstractThreatDetectionAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgenticObserveAction.class, LogDb.DASHBOARD);
    private static final int MAX_THREAT_FETCH_LIMIT = 100_000;

    @Getter
    private BasicDBObject response = new BasicDBObject();

    @Setter
    private String deviceId;

    @Setter
    private String assetId;

    @Setter
    private List<Integer> apiCollectionIds;

    @Setter
    private int startTimestamp;

    @Setter
    private int endTimestamp;

    public String fetchAgenticViolations() {
        response = new BasicDBObject();
        try {
            if (endTimestamp == 0) {
                endTimestamp = Context.now();
            }
            if (startTimestamp <= 0) {
                startTimestamp = endTimestamp - 90 * Constants.ONE_DAY_TIMESTAMP;
            }
            Set<Integer> demoCollections = getDemoCollections();
            Set<Integer> collectionIds = resolveCollectionIdsForViolations();
            List<BasicDBObject> rows = new ArrayList<>();

            List<DashboardMaliciousEvent> threats = super.fetchAllMaliciousEvents(
                    startTimestamp, endTimestamp, MAX_THREAT_FETCH_LIMIT, null);
            int id = 0;
            for (DashboardMaliciousEvent event : threats) {
                if (demoCollections.contains(event.getApiCollectionId())) {
                    continue;
                }
                if (!collectionIds.isEmpty() && !collectionIds.contains(event.getApiCollectionId())) {
                    continue;
                }
                String sev = AgenticObserveUtil.normalizeSeverityKey(event.getSeverity());
                if (sev == null) {
                    sev = "medium";
                }
                BasicDBObject row = new BasicDBObject();
                row.put("id", id++);
                row.put("severity", sev);
                row.put("title", StringUtils.defaultIfBlank(event.getFilterId(), "Policy violation"));
                row.put("description", StringUtils.defaultIfBlank(event.getUrl(), event.getHost()));
                row.put("agent", StringUtils.defaultIfBlank(event.getHost(), "Unknown"));
                row.put("agentType", AgenticObserveUtil.CLIENT_TYPE_MCP_SERVER);
                row.put("apiCollectionId", event.getApiCollectionId());
                row.put("time", event.getTimestamp());
                rows.add(row);
            }

            Bson issuesFilter = Filters.and(
                    Filters.gte(TestingRunIssues.CREATION_TIME, startTimestamp),
                    Filters.lte(TestingRunIssues.CREATION_TIME, endTimestamp),
                    Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN),
                    Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, demoCollections)
            );
            if (!collectionIds.isEmpty()) {
                issuesFilter = Filters.and(issuesFilter, Filters.in(TestingRunIssues.ID_API_COLLECTION_ID, collectionIds));
            }
            List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(issuesFilter);
            Map<String, String> testNames = fetchTestNames(issues);

            for (TestingRunIssues issue : issues) {
                if (issue.getId() == null) {
                    continue;
                }
                GlobalEnums.Severity severity = issue.getSeverity();
                String sev = severity != null
                        ? AgenticObserveUtil.normalizeSeverityKey(severity.name())
                        : "medium";
                if (sev == null) {
                    sev = "medium";
                }
                String subCategory = issue.getId().getTestSubCategory();
                BasicDBObject row = new BasicDBObject();
                row.put("id", id++);
                row.put("severity", sev);
                row.put("title", testNames.getOrDefault(subCategory, StringUtils.defaultString(subCategory, "Security issue")));
                row.put("description", subCategory);
                row.put("agent", issue.getId().getApiInfoKey() != null ? issue.getId().getApiInfoKey().getUrl() : "Unknown");
                row.put("agentType", AgenticObserveUtil.CLIENT_TYPE_AI_AGENT);
                if (issue.getId().getApiInfoKey() != null) {
                    row.put("apiCollectionId", issue.getId().getApiInfoKey().getApiCollectionId());
                }
                row.put("time", issue.getCreationTime());
                rows.add(row);
            }

            rows.sort((a, b) -> Integer.compare(b.getInt("time", 0), a.getInt("time", 0)));
            response.put("violations", rows);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching agentic violations: " + e.getMessage());
            addActionError("Error fetching agentic violations: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private Set<Integer> resolveCollectionIdsForViolations() {
        if (apiCollectionIds != null && !apiCollectionIds.isEmpty()) {
            return new HashSet<>(apiCollectionIds);
        }
        if (StringUtils.isNotBlank(assetId)) {
            return Collections.emptySet();
        }
        return resolveCollectionIdsForDevice();
    }

    private Set<Integer> resolveCollectionIdsForDevice() {
        if (StringUtils.isBlank(deviceId)) {
            return Collections.emptySet();
        }
        return com.akto.dao.ApiCollectionsDao.instance.findAll(
                Filters.regex(com.akto.dto.ApiCollection.HOST_NAME, "^" + Pattern.quote(deviceId) + "\\."),
                Projections.include(Constants.ID)
        ).stream().map(com.akto.dto.ApiCollection::getId).collect(java.util.stream.Collectors.toSet());
    }

    private Map<String, String> fetchTestNames(List<TestingRunIssues> issues) {
        Set<String> ids = new HashSet<>();
        for (TestingRunIssues issue : issues) {
            if (issue.getId() != null && issue.getId().getTestSubCategory() != null
                    && !issue.getId().getTestSubCategory().startsWith("http")) {
                ids.add(issue.getId().getTestSubCategory());
            }
        }
        Map<String, String> names = new java.util.HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        List<YamlTemplate> templates = YamlTemplateDao.instance.findAll(
                Filters.in("_id", ids),
                Projections.include(YamlTemplate.INFO)
        );
        for (YamlTemplate t : templates) {
            if (t != null && t.getInfo() != null) {
                Info info = t.getInfo();
                if (StringUtils.isNotBlank(info.getName())) {
                    names.put(t.getId(), info.getName());
                }
            }
        }
        return names;
    }

    private Set<Integer> getDemoCollections() {
        Set<Integer> demoCollections = new HashSet<>(UsageMetricCalculator.getDeactivated());
        com.akto.dto.ApiCollection juiceshop = com.akto.dao.ApiCollectionsDao.instance.findByName("juice_shop_demo");
        if (juiceshop != null) {
            demoCollections.add(juiceshop.getId());
        }
        return demoCollections;
    }
}
