package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.billing.UsageMetricUtils;
import com.akto.dao.context.Context;
import com.akto.dao.test_editor.YamlTemplateDao;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.dao.testing.VulnerableTestingRunResultDao;
import com.akto.dao.testing_run_findings.TestingRunIssuesDao;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.YamlTemplate;
import com.akto.dto.test_run_findings.TestingIssuesId;
import com.akto.dto.test_run_findings.TestingRunIssues;
import com.akto.dto.testing.GenericTestResult;
import com.akto.dto.testing.MultiExecTestResult;
import com.akto.dto.testing.TestResult;
import com.akto.dto.testing.TestingRunResult;
import com.akto.gpt.handlers.gpt_prompts.FalsePositiveVerifierPromptHandler;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing_utils.TestingUtils;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.TestRunIssueStatus;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * "Verify with AI" on the test run page: asks the LLM about every vulnerable result of a test run
 * and moves the ones it judges false positives to ignored issues (same as a manual "False positive"
 * ignore). Runs in the background; the page shows the outcome on refresh.
 */
public class AiVerificationAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AiVerificationAction.class, LogDb.DASHBOARD);

    private static final String FEATURE_LABEL = "AKTO_GPT_AI";
    private static final int MAX_RESULTS_PER_RUN = 500;
    private static final int MIN_CONFIDENCE_TO_IGNORE = 70;
    private static final int MAX_ATTEMPTS_PER_RESULT = 3;
    private static final String IGNORE_REASON = "False positive";
    private static final String DESCRIPTION_PREFIX = "[AI verified] ";
    private static final String UPDATED_BY = "Akto AI";

    // One background job per test run at a time; jobs run here, LLM calls in a separate pool so a
    // job waiting on its calls cannot starve them.
    private static final Set<String> IN_PROGRESS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService JOB_EXECUTOR = Executors.newFixedThreadPool(2);
    private static final ExecutorService LLM_EXECUTOR = Executors.newFixedThreadPool(4);

    private String testingRunResultSummaryHexId;
    // Optional: only these results (rows selected in the table); empty = every vulnerable result of the run.
    private List<String> testingRunResultHexIds;
    // Issues page: the selected issues.
    private List<TestingIssuesId> issueIdArray;

    /** An OPEN issue and the vulnerable result the AI judges it by. */
    private static class Target {
        final TestingRunIssues issue;
        final TestingRunResult result;

        Target(TestingRunIssues issue, TestingRunResult result) {
            this.issue = issue;
            this.result = result;
        }
    }

    /** Background work; adds every test run summary whose counts it may have changed. */
    private interface Job {
        void run(Set<ObjectId> summariesToRecount) throws Exception;
    }

    /** Test run page: every vulnerable result of the run, or only the selected ones. */
    public String verifyTestRunWithAi() {
        ObjectId summaryId;
        List<ObjectId> resultIds = new ArrayList<>();
        try {
            summaryId = new ObjectId(testingRunResultSummaryHexId);
            if (testingRunResultHexIds != null) {
                for (String hexId : testingRunResultHexIds) {
                    resultIds.add(new ObjectId(hexId));
                }
            }
        } catch (Exception e) {
            addActionError("Invalid test run or test result id");
            return ERROR.toUpperCase();
        }

        int accountId = Context.accountId.get();
        return startJob(accountId + ":run:" + testingRunResultSummaryHexId,
            "Verification already running for this test run",
            summaries -> {
                summaries.add(summaryId);
                verifyTargets("test run " + summaryId, targetsForSummary(summaryId, resultIds));
            });
    }

    /** Issues page: the selected issues, each judged by its latest vulnerable result. */
    public String verifyIssuesWithAi() {
        if (issueIdArray == null || issueIdArray.isEmpty()) {
            addActionError("Select at least one issue to verify");
            return ERROR.toUpperCase();
        }
        if (issueIdArray.size() > MAX_RESULTS_PER_RUN) {
            addActionError("Select at most " + MAX_RESULTS_PER_RUN + " issues to verify at once");
            return ERROR.toUpperCase();
        }
        // Built here, with the user's context, so only issues the user can access are verified.
        Bson openIssuesFilter = Filters.and(
            TestingRunIssuesDao.instance.addCollectionsFilterForDashboard(Filters.in(Constants.ID, issueIdArray)),
            Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN));

        int accountId = Context.accountId.get();
        return startJob(accountId + ":issues",
            "Verification already running for selected issues",
            summaries -> verifyTargets(issueIdArray.size() + " selected issues", targetsForIssues(openIssuesFilter, summaries)));
    }

    /** Checks the feature and the per-scope guard, then runs the job in the background. */
    private String startJob(String key, String busyMessage, Job job) {
        int accountId = Context.accountId.get();
        if (!UsageMetricUtils.getFeatureAccessSaas(accountId, FEATURE_LABEL).getIsGranted()) {
            addActionError("AI features are not enabled for this account");
            return ERROR.toUpperCase();
        }
        if (!FalsePositiveVerifierPromptHandler.isConfigured()) {
            addActionError("AI provider is not configured. Set the AZURE_OPENAI_* environment variables or configure a model for 'false-positive-verification'.");
            return ERROR.toUpperCase();
        }
        if (!IN_PROGRESS.add(key)) {
            addActionError(busyMessage);
            return ERROR.toUpperCase();
        }

        JOB_EXECUTOR.submit(() -> {
            Context.accountId.set(accountId);
            Set<ObjectId> summariesToRecount = new HashSet<>();
            try {
                job.run(summariesToRecount);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "AI verification failed for " + key + ": " + e.getMessage());
            } finally {
                for (ObjectId summaryId : summariesToRecount) {
                    try {
                        TestingUtils.recountAndUpdateSummaryCount(summaryId);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Recount after AI verification failed for summary " + summaryId);
                    }
                }
                IN_PROGRESS.remove(key);
            }
        });

        return SUCCESS.toUpperCase();
    }

    private static List<Target> targetsForSummary(ObjectId summaryId, List<ObjectId> resultIds) {
        boolean inVulnerableCollection = VulnerableTestingRunResultDao.instance.isStoredInVulnerableCollection(summaryId, true);
        List<Bson> filters = new ArrayList<>();
        filters.add(Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryId));
        filters.add(Filters.eq(TestingRunResult.VULNERABLE, true));
        filters.add(Filters.ne(TestingRunResult.IS_IGNORED_RESULT, true));
        if (!resultIds.isEmpty()) {
            filters.add(Filters.in(Constants.ID, resultIds));
        }
        List<TestingRunResult> results = resultCollection(inVulnerableCollection)
            .find(Filters.and(filters)).limit(MAX_RESULTS_PER_RUN).into(new ArrayList<>());
        List<Target> targets = new ArrayList<>();
        if (results.isEmpty()) {
            return targets;
        }

        // Only results whose issue is still OPEN are verified.
        Map<TestingIssuesId, TestingRunResult> resultByIssue = new HashMap<>();
        for (TestingRunResult result : results) {
            resultByIssue.put(TestingUtils.getTestingIssueIdFromRunResult(result), result);
        }
        List<TestingRunIssues> openIssues = TestingRunIssuesDao.instance.findAll(
            Filters.and(
                Filters.in(Constants.ID, new ArrayList<>(resultByIssue.keySet())),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN)
            ),
            Projections.include(TestingRunIssues.DESCRIPTION)
        );
        for (TestingRunIssues issue : openIssues) {
            TestingRunResult result = resultByIssue.get(issue.getId());
            if (result != null) targets.add(new Target(issue, result));
        }
        return targets;
    }

    /** For each OPEN issue, the vulnerable result from the latest test run that found it. */
    private static List<Target> targetsForIssues(Bson openIssuesFilter, Set<ObjectId> summariesToRecount) {
        List<TestingRunIssues> issues = TestingRunIssuesDao.instance.findAll(openIssuesFilter,
            Projections.include(TestingRunIssues.DESCRIPTION, TestingRunIssues.LATEST_TESTING_RUN_SUMMARY_ID));
        Map<ObjectId, Boolean> inVulnerableCollection = new HashMap<>();
        List<Target> targets = new ArrayList<>();
        int skipped = 0;
        for (TestingRunIssues issue : issues) {
            // One bad issue must not stop the rest, so each lookup is isolated.
            try {
                ObjectId summaryId = issue.getLatestTestingRunSummaryId();
                TestingIssuesId id = issue.getId();
                ApiInfoKey apiInfoKey = id == null ? null : id.getApiInfoKey();
                if (summaryId == null || apiInfoKey == null || apiInfoKey.getMethod() == null
                    || apiInfoKey.getUrl() == null || id.getTestSubCategory() == null) {
                    skipped++;
                    continue;
                }
                boolean inVuln = inVulnerableCollection.computeIfAbsent(summaryId,
                    s -> VulnerableTestingRunResultDao.instance.isStoredInVulnerableCollection(s, true));
                TestingRunResult result = resultCollection(inVuln).find(Filters.and(
                    Filters.eq(TestingRunResult.TEST_RUN_RESULT_SUMMARY_ID, summaryId),
                    Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.API_COLLECTION_ID, apiInfoKey.getApiCollectionId()),
                    Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.URL, apiInfoKey.getUrl()),
                    Filters.eq(TestingRunResult.API_INFO_KEY + "." + ApiInfoKey.METHOD, apiInfoKey.getMethod().name()),
                    Filters.eq(TestingRunResult.TEST_SUB_TYPE, id.getTestSubCategory()),
                    Filters.eq(TestingRunResult.VULNERABLE, true)
                )).first();
                if (result == null) {
                    skipped++;
                    continue;
                }
                targets.add(new Target(issue, result));
                summariesToRecount.add(summaryId);
            } catch (Exception e) {
                skipped++;
                loggerMaker.errorAndAddToDb(e, "AI verification could not load evidence for issue " + issue.getId() + ": " + e.getMessage());
            }
        }
        if (skipped > 0) {
            loggerMaker.infoAndAddToDb("AI verification skipped " + skipped + " of " + issues.size()
                + " issues with no vulnerable result in their latest test run");
        }
        return targets;
    }

    private static MongoCollection<TestingRunResult> resultCollection(boolean inVulnerableCollection) {
        return inVulnerableCollection
            ? VulnerableTestingRunResultDao.instance.getMCollection()
            : TestingRunResultDao.instance.getMCollection();
    }

    /** Result of judging one target. */
    private enum Outcome { IGNORED, KEPT, ERROR }

    /** Asks the LLM about every target in parallel; false positives are moved to ignored. */
    private void verifyTargets(String label, List<Target> targets) {
        if (targets.isEmpty()) {
            loggerMaker.infoAndAddToDb("AI verification for " + label + ": nothing to verify");
            return;
        }
        List<TestingRunResult> results = new ArrayList<>();
        for (Target target : targets) results.add(target.result);
        Map<String, String> templates = loadTemplates(results);

        int accountId = Context.accountId.get();
        List<Future<Outcome>> calls = new ArrayList<>();
        for (Target target : targets) {
            String template = templates.get(target.result.getTestSubType());
            calls.add(LLM_EXECUTOR.submit(() -> {
                Context.accountId.set(accountId);
                return verifyResult(target.issue, target.result, template);
            }));
        }

        int ignored = 0;
        int kept = 0;
        int errors = 0;
        for (Future<Outcome> call : calls) {
            Outcome outcome;
            try {
                outcome = call.get();
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "AI verification call failed: " + e.getMessage());
                outcome = Outcome.ERROR;
            }
            if (outcome == Outcome.IGNORED) ignored++;
            else if (outcome == Outcome.KEPT) kept++;
            else errors++;
        }

        String summary = "AI verification for " + label + ": checked " + calls.size() + ", moved " + ignored
            + " false positives to ignored, kept " + kept + ", errors " + errors;
        if (errors == calls.size()) {
            loggerMaker.errorAndAddToDb(summary + ". Every AI call failed; check the AI provider configuration and logs.");
        } else {
            loggerMaker.infoAndAddToDb(summary);
        }
    }

    /** The test template YAML of every test in the batch, by test id, in one query. */
    private static Map<String, String> loadTemplates(List<TestingRunResult> results) {
        Set<String> testIds = new HashSet<>();
        for (TestingRunResult result : results) {
            if (result.getTestSubType() != null) testIds.add(result.getTestSubType());
        }
        Map<String, String> templates = new HashMap<>();
        if (testIds.isEmpty()) return templates;
        for (YamlTemplate template : YamlTemplateDao.instance.findAll(
                Filters.in(Constants.ID, new ArrayList<>(testIds)), Projections.include(YamlTemplate.CONTENT))) {
            templates.put(template.getId(), template.getContent());
        }
        return templates;
    }

    /** Asks the LLM about one result and moves its issue to ignored if it is a false positive. */
    private Outcome verifyResult(TestingRunIssues issue, TestingRunResult result, String template) {
        List<TestResult> attempts = attemptsToJudge(result);
        if (attempts.isEmpty()) return Outcome.KEPT;

        List<String> attemptMessages = new ArrayList<>();
        Set<String> reasons = new LinkedHashSet<>();
        for (TestResult attempt : attempts) {
            attemptMessages.add(attempt.getMessage());
            if (attempt.getValidationReason() != null && !attempt.getValidationReason().trim().isEmpty()) {
                reasons.add(attempt.getValidationReason().trim());
            }
        }
        String prompt = FalsePositiveVerifierPromptHandler.buildPrompt(result.getTestSubType(), template, String.join("; ", reasons),
            attempts.get(0).getOriginalMessage(), attemptMessages);
        BasicDBObject verdict = new FalsePositiveVerifierPromptHandler().handle(new BasicDBObject("prompt", prompt));
        if (verdict.containsField("error")) {
            loggerMaker.infoAndAddToDb("AI verification skipped result " + result.getHexId() + ": " + verdict.get("error"));
            return Outcome.ERROR;
        }
        if (!FalsePositiveVerifierPromptHandler.FALSE_POSITIVE.equals(verdict.getString(FalsePositiveVerifierPromptHandler.VERDICT))
            || verdict.getInt(FalsePositiveVerifierPromptHandler.CONFIDENCE) < MIN_CONFIDENCE_TO_IGNORE) {
            return Outcome.KEPT;
        }

        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.IGNORED));
        updates.add(Updates.set(TestingRunIssues.IGNORE_REASON, IGNORE_REASON));
        updates.add(Updates.set(TestingRunIssues.LAST_UPDATED, Context.now()));
        updates.add(Updates.set(TestingRunIssues.LAST_UPDATED_BY, UPDATED_BY));
        String reason = verdict.getString(FalsePositiveVerifierPromptHandler.REASON);
        if ((issue.getDescription() == null || issue.getDescription().trim().isEmpty()) && reason != null && !reason.isEmpty()) {
            updates.add(Updates.set(TestingRunIssues.DESCRIPTION, DESCRIPTION_PREFIX + reason));
        }

        // Only while still OPEN, so a user's ignore/fix made in the meantime is never overridden.
        boolean updated = TestingRunIssuesDao.instance.getMCollection().updateOne(
            Filters.and(
                Filters.eq(Constants.ID, issue.getId()),
                Filters.eq(TestingRunIssues.TEST_RUN_ISSUES_STATUS, TestRunIssueStatus.OPEN)
            ),
            Updates.combine(updates)
        ).getModifiedCount() > 0;
        return updated ? Outcome.IGNORED : Outcome.KEPT;
    }

    /**
     * The attempts the scanner flagged vulnerable (up to MAX_ATTEMPTS_PER_RESULT), since those are what
     * the verdict is about. Falls back to the last attempt if none carries the vulnerable flag.
     */
    private static List<TestResult> attemptsToJudge(TestingRunResult result) {
        List<GenericTestResult> testResults = result.getTestResults();
        List<TestResult> picked = new ArrayList<>();
        if (testResults == null || testResults.isEmpty()) return picked;
        if (testResults.get(0) instanceof MultiExecTestResult) {
            testResults = ((MultiExecTestResult) testResults.get(0)).convertToExistingTestResult(result);
        }
        TestResult last = null;
        for (GenericTestResult attempt : testResults) {
            if (!(attempt instanceof TestResult)) continue;
            last = (TestResult) attempt;
            if (attempt.isVulnerable() && picked.size() < MAX_ATTEMPTS_PER_RESULT) {
                picked.add(last);
            }
        }
        if (picked.isEmpty() && last != null) picked.add(last);
        return picked;
    }

    public void setTestingRunResultSummaryHexId(String testingRunResultSummaryHexId) {
        this.testingRunResultSummaryHexId = testingRunResultSummaryHexId;
    }

    public void setTestingRunResultHexIds(List<String> testingRunResultHexIds) {
        this.testingRunResultHexIds = testingRunResultHexIds;
    }

    public void setIssueIdArray(List<TestingIssuesId> issueIdArray) {
        this.issueIdArray = issueIdArray;
    }
}
