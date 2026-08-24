// Run via: docker exec -i mongo mongosh --quiet --eval "$(cat export_agentic_results.js)" <account_id>
//
// "Issue" rows = the full testing_run_issues registry (all-time, deduped by
// apiInfoKey+testSubCategory -- includes OPEN and IGNORED). For each issue, pulls the
// conversation transcript from its latestTestingRunSummaryId's vulnerable_testing_run_results
// doc (falling back to the most recent vulnerable doc for that key if the exact summary
// link doesn't resolve).
//
// "Non-Issue"/"Skipped-Error" rows = testing_run_result scoped to only the LATEST
// testing_run_result_summaries doc per testingRunId (same dedup the dashboard's
// Scan Results page uses) -- older reruns are excluded.
//
// Every row is joined against yaml_templates (category/name/severity) and testing_run
// (which job it came from), and agent_conversation_results (the actual prompts/responses).
// Prints one JSON object per line (JSONL) to stdout.

function templateInfo(subType) {
  const t = db.yaml_templates.findOne({ _id: subType }, { info: 1 });
  if (!t) return { name: subType, severity: "", category: "" };
  return {
    name: t.info.name || subType,
    severity: t.info.severity || "",
    category: (t.info.category && t.info.category.displayName) || ""
  };
}

function runName(summaryId) {
  if (!summaryId) return "";
  const summary = db.testing_run_result_summaries.findOne({ _id: summaryId }, { testingRunId: 1 });
  if (!summary) return "";
  const run = db.testing_run.findOne({ _id: summary.testingRunId }, { name: 1 });
  return run ? run.name : "";
}

function turnsFor(conversationId) {
  if (!conversationId) return [];
  return db.agent_conversation_results.find(
    { conversationId: conversationId },
    { _id: 0, finalSentPrompt: 1, response: 1, validationMessage: 1 }
  ).sort({ timestamp: 1 }).toArray();
}

function emit(base, resultType, extra) {
  const rec = Object.assign({ resultType: resultType }, base, extra || {});
  print(JSON.stringify(rec));
}

// ---- Issue rows: full all-time registry ----
db.testing_run_issues.find({}).toArray().forEach(issue => {
  const key = issue._id.apiInfoKey;
  const subType = issue._id.testSubCategory;

  let vresult = db.vulnerable_testing_run_results.findOne({
    "apiInfoKey.apiCollectionId": key.apiCollectionId,
    "apiInfoKey.url": key.url,
    "apiInfoKey.method": key.method,
    testSubType: subType,
    testRunResultSummaryId: issue.latestTestingRunSummaryId
  });
  if (!vresult) {
    vresult = db.vulnerable_testing_run_results.find({
      "apiInfoKey.apiCollectionId": key.apiCollectionId,
      "apiInfoKey.url": key.url,
      "apiInfoKey.method": key.method,
      testSubType: subType
    }).sort({ startTimestamp: -1 }).limit(1).next();
  }

  const info = templateInfo(subType);
  const conversationId = vresult && vresult.testResults && vresult.testResults[0]
    ? vresult.testResults[0].conversationId : null;

  emit(
    { testSubType: subType, method: key.method, url: key.url, name: info.name, severity: info.severity, category: info.category },
    "Issue",
    {
      issueStatus: issue.testRunIssueStatus,
      testingRun: runName(issue.latestTestingRunSummaryId),
      turns: turnsFor(conversationId)
    }
  );
});

// ---- Non-Issue / Skipped-Error rows: latest summary per testingRunId ----
const agenticRunIds = db.testing_run.find({ dashboardContext: "AGENTIC" }, { _id: 1 }).toArray().map(d => d._id);
const latestSummaries = db.testing_run_result_summaries.aggregate([
  { $match: { testingRunId: { $in: agenticRunIds } } },
  { $sort: { startTimestamp: -1 } },
  { $group: { _id: "$testingRunId", data: { $first: "$$ROOT" } } }
]).toArray();
const summaryIds = latestSummaries.map(d => d.data._id);

function exportScoped(matchExtra, resultType) {
  const match = Object.assign({ testRunResultSummaryId: { $in: summaryIds }, "testResults.resultTypeAgentic": true }, matchExtra);
  db.testing_run_result.find(match).forEach(doc => {
    const info = templateInfo(doc.testSubType);
    const conversationId = doc.testResults && doc.testResults[0] ? doc.testResults[0].conversationId : null;
    emit(
      { testSubType: doc.testSubType, method: doc.apiInfoKey.method, url: doc.apiInfoKey.url, name: info.name, severity: info.severity, category: info.category },
      resultType,
      { issueStatus: "", testingRun: runName(doc.testRunResultSummaryId), turns: turnsFor(conversationId) }
    );
  });
}

exportScoped({ vulnerable: false }, "Non-Issue");
exportScoped({ "testResults.errors.0": { $exists: true } }, "Skipped/Error");
