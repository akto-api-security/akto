package com.akto.data_actor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestResult.Confidence;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.enums.GlobalEnums.Severity;
import com.akto.util.enums.GlobalEnums.TestErrorSource;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

/** Pure unit tests (no Mongo) for the summary count that must not include IGNORED issues. */
public class TestDbLayerIssueCountExcludingIgnored {

    private static final ObjectId SUMMARY = new ObjectId();
    private static final Function<String, com.akto.dto.testing.sources.TestSourceConfig> NO_CONFIG = s -> null;

    private static TestingRunResult result(int collection, String url, String subType, Confidence confidence) {
        TestingRunResult r = new TestingRunResult();
        r.setApiInfoKey(new ApiInfoKey(collection, url, Method.GET));
        r.setTestSubType(subType);
        r.setVulnerable(true);
        r.setTestRunResultSummaryId(SUMMARY);
        TestResult tr = new TestResult();
        tr.setConfidence(confidence);
        r.setTestResults(Collections.singletonList((GenericTestResult) tr));
        return r;
    }

    private static TestingIssuesId idOf(TestingRunResult r) {
        return new TestingIssuesId(r.getApiInfoKey(), TestErrorSource.AUTOMATED_TESTING, r.getTestSubType(), null);
    }

    private static TestingRunIssues issue(TestingRunResult r, TestRunIssueStatus status) {
        return new TestingRunIssues(idOf(r), Severity.HIGH, status, 0, 0, SUMMARY, null, 0);
    }

    private static BiFunction<Integer, Integer, List<TestingRunResult>> pages(List<TestingRunResult> all) {
        return (limit, skip) -> skip >= all.size()
                ? new ArrayList<>()
                : new ArrayList<>(all.subList(skip, Math.min(all.size(), skip + limit)));
    }

    /** Issue store that returns only the requested ids that exist in {@code existing}. */
    private static Function<Set<TestingIssuesId>, List<TestingRunIssues>> store(List<TestingRunIssues> existing) {
        return ids -> {
            List<TestingRunIssues> out = new ArrayList<>();
            for (TestingRunIssues i : existing) {
                if (ids.contains(i.getId())) {
                    out.add(i);
                }
            }
            return out;
        };
    }

    @Test
    public void ignoredIssueIsNotCounted_openAndMissingAre() {
        TestingRunResult ignored = result(1, "/a", "SQLI", Confidence.HIGH);
        TestingRunResult open = result(1, "/b", "SQLI", Confidence.HIGH);
        TestingRunResult noIssue = result(1, "/c", "SQLI", Confidence.MEDIUM);
        List<TestingRunResult> all = Arrays.asList(ignored, open, noIssue);

        DbLayer.IssueCountResult res = DbLayer.countIssuesExcludingIgnored(pages(all),
                store(Arrays.asList(issue(ignored, TestRunIssueStatus.IGNORED), issue(open, TestRunIssueStatus.OPEN))),
                NO_CONFIG);

        assertEquals(1, (int) res.countIssues.get("HIGH"));
        assertEquals(1, (int) res.countIssues.get("MEDIUM"));
        assertEquals(0, (int) res.countIssues.get("LOW"));
        assertEquals(0, (int) res.countIssues.get("CRITICAL"));
        assertEquals(3, res.resultsExamined);
        assertEquals(1, res.ignoredExcluded);
    }

    @Test
    public void ignoringAnIssueNeverRaisesTheCount() {
        TestingRunResult a = result(1, "/a", "SQLI", Confidence.CRITICAL);
        TestingRunResult b = result(1, "/b", "XSS", Confidence.LOW);
        List<TestingRunResult> all = Arrays.asList(a, b);

        int allOpen = total(DbLayer.countIssuesExcludingIgnored(pages(all),
                store(Arrays.asList(issue(a, TestRunIssueStatus.OPEN), issue(b, TestRunIssueStatus.OPEN))), NO_CONFIG));
        int oneIgnored = total(DbLayer.countIssuesExcludingIgnored(pages(all),
                store(Arrays.asList(issue(a, TestRunIssueStatus.IGNORED), issue(b, TestRunIssueStatus.OPEN))), NO_CONFIG));

        assertEquals(2, allOpen);
        assertEquals(1, oneIgnored);
    }

    @Test
    public void onlyIgnoredIsExcluded_otherStatusesStillCount() {
        TestingRunResult fixed = result(1, "/a", "SQLI", Confidence.HIGH);
        TestingRunResult nullStatus = result(1, "/b", "SQLI", Confidence.HIGH);
        List<TestingRunIssues> existing = Arrays.asList(issue(fixed, TestRunIssueStatus.FIXED), issue(nullStatus, null));

        DbLayer.IssueCountResult res = DbLayer.countIssuesExcludingIgnored(pages(Arrays.asList(fixed, nullStatus)),
                store(existing), NO_CONFIG);

        assertEquals(2, (int) res.countIssues.get("HIGH"));
        assertEquals(0, res.ignoredExcluded);
    }

    @Test
    public void ignoredIssueOfAnotherTestOrApiDoesNotExclude() {
        TestingRunResult target = result(1, "/a", "SQLI", Confidence.HIGH);
        TestingRunResult otherSubType = result(1, "/a", "XSS", Confidence.HIGH);
        TestingRunResult otherApi = result(1, "/z", "SQLI", Confidence.HIGH);

        DbLayer.IssueCountResult res = DbLayer.countIssuesExcludingIgnored(pages(Arrays.asList(target)),
                store(Arrays.asList(issue(otherSubType, TestRunIssueStatus.IGNORED), issue(otherApi, TestRunIssueStatus.IGNORED))),
                NO_CONFIG);

        assertEquals(1, (int) res.countIssues.get("HIGH"));
    }

    @Test
    public void spansPagesAndLooksUpIssuesOncePerPage() {
        List<TestingRunResult> all = new ArrayList<>();
        for (int i = 0; i < 1001; i++) {
            all.add(result(1, "/u" + i, "SQLI", Confidence.HIGH));
        }
        // one ignored on each page
        List<TestingRunIssues> existing = Arrays.asList(
                issue(all.get(5), TestRunIssueStatus.IGNORED), issue(all.get(1000), TestRunIssueStatus.IGNORED));
        AtomicInteger lookups = new AtomicInteger();
        Function<Set<TestingIssuesId>, List<TestingRunIssues>> counting = ids -> {
            lookups.incrementAndGet();
            return store(existing).apply(ids);
        };

        DbLayer.IssueCountResult res = DbLayer.countIssuesExcludingIgnored(pages(all), counting, NO_CONFIG);

        assertEquals(999, (int) res.countIssues.get("HIGH"));
        assertEquals(1001, res.resultsExamined);
        assertEquals(2, res.ignoredExcluded);
        assertEquals(2, lookups.get());
    }

    @Test
    public void resultWithoutDerivableIssueIdIsCountedWithoutLookup() {
        TestingRunResult noSubType = result(1, "/a", null, Confidence.HIGH);
        AtomicInteger lookups = new AtomicInteger();

        DbLayer.IssueCountResult res = DbLayer.countIssuesExcludingIgnored(pages(Arrays.asList(noSubType)),
                ids -> {
                    lookups.incrementAndGet();
                    return new ArrayList<>();
                }, NO_CONFIG);

        assertEquals(1, (int) res.countIssues.get("HIGH"));
        assertEquals(0, lookups.get());
    }

    @Test
    public void noResultsGivesAllFourSeveritiesAtZero() {
        DbLayer.IssueCountResult res = DbLayer.countIssuesExcludingIgnored(pages(new ArrayList<>()),
                ids -> {
                    throw new AssertionError("no lookup expected for empty summary");
                }, NO_CONFIG);

        assertEquals(4, res.countIssues.size());
        assertEquals(0, total(res));
    }

    @Test
    public void issueLookupFailurePropagatesInsteadOfCountingIgnoredAsOpen() {
        TestingRunResult r = result(1, "/a", "SQLI", Confidence.HIGH);
        IllegalStateException boom = new IllegalStateException("mongo down");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> DbLayer.countIssuesExcludingIgnored(pages(Arrays.asList(r)), ids -> {
                    throw boom;
                }, NO_CONFIG));
        assertSame(boom, thrown);
    }

    private static int total(DbLayer.IssueCountResult res) {
        return res.countIssues.values().stream().mapToInt(Integer::intValue).sum();
    }
}
