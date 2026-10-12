package com.akto.service.collections;

import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.service.TimedService;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Facet;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.akto.service.collections.AggregationExpressions.field;

/**
 * The issue and test numbers of the home dashboard in one aggregation over testing_run_issues (one
 * $facet branch per number) plus one over the test results that need configuring, instead of eight
 * calls that each aggregated the issues again and returned whole maps for the page to count.
 *
 * Scoped by TestingRunIssuesDao.addCollectionsFilterForDashboard (roles and dashboard context), so it
 * is served live per user rather than cached.
 */
public class IssuesSnapshotService extends TimedService {

    // the sub categories of these categories have their own tiles
    static final String VEM_CATEGORY = "VEM";
    static final String MHH_CATEGORY = "MHH";
    static final String TEMPLATE_CATEGORY_NAME = "info.category.name";
    // issues of the test editor's own runs are not findings of the account
    static final String TEST_ERROR_SOURCE = Constants.ID + ".testErrorSource";
    static final String TEST_EDITOR = "TEST_EDITOR";
    // a test sub category is a remediation playbook when more apis than this have an open issue for it
    static final int URLS_PER_PLAYBOOK_THRESHOLD = 1;
    static final int HIGH_VALUE_MIN_ISSUES = 2;
    static final int QUERY_MAX_TIME_SECONDS = 20;

    private static final String COUNT = "count";
    private static final String SUB_CATEGORY_PATH = Constants.ID + "." + TestingIssuesId.TEST_SUB_CATEGORY;

    private static final String OPEN_AT_END = "openAtEnd";
    private static final String OPEN_AND_FIXED_AT_START = "openAndFixedAtStart";
    private static final String BY_SEVERITY = "bySeverity";
    private static final String VULNERABLE = "vulnerable";
    private static final String BROKEN_AUTH = "brokenAuth";
    private static final String HIGH_VALUE = "highValue";
    private static final String VEM = "vem";
    private static final String MHH = "mhh";
    private static final String PLAYBOOKS = "playbooks";
    private static final String BY_SUB_CATEGORY = "bySubCategory";

    public IssuesSnapshotResponse fetch(int startTimestamp, int endTimestamp) {
        Set<Integer> excluded = UsageMetricCalculator.getDemosAndDeactivated();
        List<String> vemTests = timed("test ids of " + VEM_CATEGORY, () -> testIdsOfCategory(VEM_CATEGORY));
        List<String> mhhTests = timed("test ids of " + MHH_CATEGORY, () -> testIdsOfCategory(MHH_CATEGORY));

        BasicDBObject row = timed("issue counts", () -> TestingRunIssuesDao.instance.getMCollection()
                .aggregate(Collections.singletonList(Aggregates.facet(branches(excluded, startTimestamp, endTimestamp, vemTests, mhhTests))), BasicDBObject.class)
                .maxTime(QUERY_MAX_TIME_SECONDS, TimeUnit.SECONDS).first());

        Map<String, Long> severityCounts = new HashMap<>();
        for (Object entry : list(row, BY_SEVERITY)) {
            BasicDBObject severity = (BasicDBObject) entry;
            severityCounts.put(severity.getString(Constants.ID), severity.getLong(COUNT));
        }
        long misConfigured = timed("misconfigured tests", () -> countMisConfiguredTests(excluded));
        Map<String, Long> subCategoryCounts = new HashMap<>();
        for (Object entry : list(row, BY_SUB_CATEGORY)) {
            BasicDBObject subCategory = (BasicDBObject) entry;
            subCategoryCounts.put(subCategory.getString(Constants.ID), subCategory.getLong(COUNT));
        }

        return new IssuesSnapshotResponse(count(row, OPEN_AT_END), count(row, OPEN_AND_FIXED_AT_START), severityCounts,
                count(row, VULNERABLE), count(row, BROKEN_AUTH), count(row, HIGH_VALUE), count(row, VEM), count(row, MHH),
                count(row, PLAYBOOKS), misConfigured, subCategoryCounts);
    }

    private List<Facet> branches(Set<Integer> excluded, int start, int end, List<String> vemTests, List<String> mhhTests) {
        // what the dashboard shows of the issue collection, less the demo and deactivated collections
        Bson visible = TestingRunIssuesDao.instance.addCollectionsFilterForDashboard(
                Filters.nin(TestingRunIssues.ID_API_COLLECTION_ID, excluded));
        Bson open = Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, GlobalEnums.TestRunIssueStatus.OPEN);
        Bson notFromTestEditor = Filters.ne(TEST_ERROR_SOURCE, TEST_EDITOR);
        Bson openFinding = Filters.and(visible, open, notFromTestEditor);

        List<Facet> facets = new ArrayList<>();
        facets.add(new Facet(OPEN_AT_END, Aggregates.match(Filters.and(visible, open, Filters.lte(TestingRunIssues.CREATION_TIME, end))),
                Aggregates.count(COUNT)));
        facets.add(new Facet(OPEN_AND_FIXED_AT_START, Aggregates.match(Filters.and(visible,
                Filters.in(TestingRunIssues.TEST_RUN_ISSUES_STATUS, Arrays.asList(GlobalEnums.TestRunIssueStatus.OPEN, GlobalEnums.TestRunIssueStatus.FIXED)),
                Filters.lte(TestingRunIssues.CREATION_TIME, start))), Aggregates.count(COUNT)));
        facets.add(new Facet(BY_SEVERITY, Aggregates.match(openFinding),
                Aggregates.group(field(TestingRunIssues.KEY_SEVERITY), Accumulators.sum(COUNT, 1))));
        facets.add(apisWith(VULNERABLE, Filters.and(openFinding, Filters.eq(TestingRunIssues.KEY_SEVERITY, GlobalEnums.Severity.CRITICAL.name()))));
        facets.add(apisWith(BROKEN_AUTH, openFinding));
        facets.add(new Facet(HIGH_VALUE, Aggregates.match(Filters.and(visible, open)),
                Aggregates.group(apiKey(), Accumulators.sum(COUNT, 1)),
                Aggregates.match(Filters.gte(COUNT, HIGH_VALUE_MIN_ISSUES)), Aggregates.count(COUNT)));
        facets.add(categoryApis(VEM, openFinding, vemTests));
        facets.add(categoryApis(MHH, openFinding, mhhTests));
        // as the critical findings chart always counted: open issues last seen within the range
        facets.add(new Facet(BY_SUB_CATEGORY, Aggregates.match(Filters.and(visible, open,
                        Filters.gte(TestingRunIssues.LAST_SEEN, start), Filters.lte(TestingRunIssues.LAST_SEEN, end))),
                Aggregates.group(field(SUB_CATEGORY_PATH), Accumulators.sum(COUNT, 1))));
        facets.add(new Facet(PLAYBOOKS, Aggregates.match(Filters.and(visible, open)),
                Aggregates.group(Projections.fields(Projections.computed("subCategory", field(SUB_CATEGORY_PATH)), Projections.computed("api", apiKey())),
                        Accumulators.sum(COUNT, 1)),
                Aggregates.group(field(Constants.ID + ".subCategory"), Accumulators.sum(COUNT, 1)),
                Aggregates.match(Filters.gt(COUNT, URLS_PER_PLAYBOOK_THRESHOLD)), Aggregates.count(COUNT)));
        return facets;
    }

    /** How many distinct apis the filter's issues are on. */
    private Facet apisWith(String name, Bson filter) {
        return new Facet(name, Aggregates.match(filter), Aggregates.group(apiKey()), Aggregates.count(COUNT));
    }

    private Facet categoryApis(String name, Bson openFinding, List<String> testIds) {
        // no test of the category: nothing can match, but the branch still has to exist
        Bson inCategory = testIds.isEmpty() ? Filters.expr(false) : Filters.in(SUB_CATEGORY_PATH, testIds);
        return apisWith(name, Filters.and(openFinding, inCategory));
    }

    private Object apiKey() {
        return Projections.fields(
                Projections.computed(SingleTypeInfo._API_COLLECTION_ID, field(TestingRunIssues.ID_API_COLLECTION_ID)),
                Projections.computed(SingleTypeInfo._URL, field(TestingRunIssues.ID_URL)),
                Projections.computed(SingleTypeInfo._METHOD, field(TestingRunIssues.ID_METHOD)));
    }

    static List<String> testIdsOfCategory(String category) {
        return YamlTemplateDao.instance.findAll(Filters.eq(TEMPLATE_CATEGORY_NAME, category), Projections.include(Constants.ID))
                .stream().map(YamlTemplate::getId).collect(Collectors.toList());
    }

    /** Test results that need configuring, of apis in the collections the user may see. */
    static Bson misConfiguredFilter(Set<Integer> excluded) {
        String collectionPath = TestingRunResult.API_INFO_KEY + "." + SingleTypeInfo._API_COLLECTION_ID;
        List<Bson> filters = new ArrayList<>(Arrays.asList(
                Filters.eq(TestingRunResult.REQUIRES_CONFIG, true), Filters.nin(collectionPath, excluded)));
        try {
            List<Integer> visible = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if (visible != null) filters.add(Filters.in(collectionPath, visible));
        } catch (Exception e) {
            // a failure to resolve the user's collections is treated as unscoped, as elsewhere
        }
        return Filters.and(filters);
    }

    /** Distinct apis with a test result that needs configuring. */
    private long countMisConfiguredTests(Set<Integer> excluded) {
        BasicDBObject row = TestingRunResultDao.instance.getMCollection().withDocumentClass(BasicDBObject.class).aggregate(Arrays.asList(
                Aggregates.match(misConfiguredFilter(excluded)),
                Aggregates.group(field(TestingRunResult.API_INFO_KEY)),
                Aggregates.count(COUNT))).maxTime(QUERY_MAX_TIME_SECONDS, TimeUnit.SECONDS).first();
        return row == null ? 0 : row.getLong(COUNT);
    }

    private static List<?> list(BasicDBObject row, String branch) {
        Object value = row == null ? null : row.get(branch);
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    private static long count(BasicDBObject row, String branch) {
        List<?> rows = list(row, branch);
        return rows.isEmpty() ? 0 : ((Number) ((BasicDBObject) rows.get(0)).get(COUNT)).longValue();
    }
}
