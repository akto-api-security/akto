package com.akto.service.collections;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class TestIssuesSnapshotService extends MongoBasedTest {

    @Before
    public void reset() {
        TestingRunIssuesDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
    }

    private TestingRunIssues issue(int collectionId, String url, String subCategory, Severity severity,
                                   TestRunIssueStatus status, TestErrorSource source, int createdAt) {
        TestingIssuesId id = new TestingIssuesId(new ApiInfoKey(collectionId, url, Method.GET), source, subCategory);
        return new TestingRunIssues(id, severity, status, createdAt, createdAt, null, null, createdAt);
    }

    @Test
    public void countsMatchWhatTheEightOldCallsCounted() {
        int now = Context.now();
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issue(1, "/a", "t1", Severity.CRITICAL, TestRunIssueStatus.OPEN, TestErrorSource.AUTOMATED_TESTING, now - 10),
                issue(1, "/a", "t2", Severity.HIGH, TestRunIssueStatus.OPEN, TestErrorSource.AUTOMATED_TESTING, now - 10),
                issue(1, "/b", "t1", Severity.CRITICAL, TestRunIssueStatus.FIXED, TestErrorSource.AUTOMATED_TESTING, now - 5_000),
                issue(2, "/c", "t1", Severity.MEDIUM, TestRunIssueStatus.OPEN, TestErrorSource.AUTOMATED_TESTING, now - 10),
                // the test editor's own run: counted as an open issue, but not as a finding of an api
                issue(1, "/b", "t3", Severity.LOW, TestRunIssueStatus.OPEN, TestErrorSource.TEST_EDITOR, now - 10)));

        IssuesSnapshotResponse snapshot = new IssuesSnapshotService().fetch(now - 1_000, now);

        assertEquals("open issues at the end of the range", 4, snapshot.getTotalIssuesCount());
        assertEquals("open and fixed issues at the start", 1, snapshot.getOldOpenCount());
        assertEquals(1, snapshot.getSeverityCounts().get("CRITICAL").longValue());
        assertEquals(1, snapshot.getSeverityCounts().get("HIGH").longValue());
        assertEquals(1, snapshot.getSeverityCounts().get("MEDIUM").longValue());
        assertEquals(false, snapshot.getSeverityCounts().containsKey("LOW"));
        // what the critical findings chart counted: open issues last seen within the range, whatever their source
        assertEquals(2, snapshot.getSubCategoryCounts().get("t1").longValue());
        assertEquals(1, snapshot.getSubCategoryCounts().get("t2").longValue());
        assertEquals(1, snapshot.getSubCategoryCounts().get("t3").longValue());
        assertEquals("apis with an open critical issue", 1, snapshot.getVulnerableApis());
        assertEquals("apis with an open issue", 2, snapshot.getBrokenAuthApis());
        assertEquals("apis with two open issues (the test editor's counted)", 1, snapshot.getHighValueIssuesApis());
        assertEquals("t1 has an open issue on two apis", 1, snapshot.getRemediationPlaybooks());
        assertEquals(0, snapshot.getVemVulnerableApis());
        assertEquals(0, snapshot.getMhhVulnerableApis());
        assertEquals(0, snapshot.getMisConfiguredTests());
    }

    private void seedIssuesAndApis(int now) {
        TestingRunIssuesDao.instance.insertMany(Arrays.asList(
                issue(1, "/a", "t1", Severity.CRITICAL, TestRunIssueStatus.OPEN, TestErrorSource.AUTOMATED_TESTING, now - 10),
                issue(1, "/a", "t2", Severity.HIGH, TestRunIssueStatus.OPEN, TestErrorSource.AUTOMATED_TESTING, now - 10),
                issue(2, "/c", "t1", Severity.MEDIUM, TestRunIssueStatus.OPEN, TestErrorSource.AUTOMATED_TESTING, now - 10)));
        ApiInfoDao.instance.insertMany(Arrays.asList(
                new ApiInfo(new ApiInfoKey(1, "/a", Method.GET)), new ApiInfo(new ApiInfoKey(2, "/c", Method.GET))));
    }

    @Test
    public void issueLevelItemsListTheApisTheirCountsCount() {
        seedIssuesAndApis(Context.now());
        PostureActionItemsService service = new PostureActionItemsService();

        PostureActionItemsResponse vulnerable = service.fetch(PostureActionItemsService.Type.VULNERABLE, 0, 10);
        assertEquals(1, vulnerable.getTotal());
        assertEquals("/a", vulnerable.getRows().get(0).getUrl());

        PostureActionItemsResponse brokenAuth = service.fetch(PostureActionItemsService.Type.BROKEN_AUTH, 0, 1);
        assertEquals("the total is of every page", 2, brokenAuth.getTotal());
        assertEquals(1, brokenAuth.getRows().size());
        assertEquals(1, service.fetch(PostureActionItemsService.Type.BROKEN_AUTH, 1, 1).getRows().size());

        PostureActionItemsResponse multiple = service.fetch(PostureActionItemsService.Type.MULTIPLE_ISSUES, 0, 10);
        assertEquals(1, multiple.getTotal());
        assertEquals(new java.util.HashSet<>(Arrays.asList("t1", "t2")), new java.util.HashSet<>(multiple.getRows().get(0).getIssueNames()));
    }

    @Test
    public void subCategoriesListTheirApisAndOnlyTheOnesWithMoreThanOne() {
        seedIssuesAndApis(Context.now());

        PostureActionItemsResponse page = new PostureActionItemsService().fetch(PostureActionItemsService.Type.URLS_BY_ISSUE, 0, 10);

        assertEquals(1, page.getTotal());
        assertEquals("t1", page.getSubCategories().get(0).getTestSubCategory());
        assertEquals(2, page.getSubCategories().get(0).getEndpoints().size());
    }
}
