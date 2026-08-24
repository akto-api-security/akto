// Run via: docker exec -i mongo mongosh --quiet --eval "$(cat export_agentic_results.js)" <account_id>
//
// "Issue" rows = the full testing_run_issues registry (all-time, deduped by
// apiInfoKey+testSubCategory -- includes OPEN and IGNORED). For each issue, pulls the
// conversation transcript from its latestTestingRunSummaryId's vulnerable_testing_run_results
// doc (falling back to the most recent vulnerable doc for that key if the exact summary
// link doesn't resolve).
//
// "Non-Issue" rows = testing_run_result scoped to only the LATEST
// testing_run_result_summaries doc per testingRunId (same dedup the dashboard's
// Scan Results page uses) -- older reruns are excluded. A handful of these also had
// an execution error (timeout/API failure); those are kept as Non-Issue rows (matching
// the dashboard's "Passed" count exactly) but flagged via Issue Status = "Execution Error".
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
// Track every (apiCollectionId,url,method,testSubType) key that's ever been flagged, so
// the Non-Issue query below can exclude them -- an issue that happens to be passing in
// the latest run (e.g. an Ignored one that's since been fixed) must NOT also show up as
// a separate Non-Issue row for the same test.
const issueKeys = new Set();
db.testing_run_issues.find({}).toArray().forEach(issue => {
  const key = issue._id.apiInfoKey;
  const subType = issue._id.testSubCategory;
  issueKeys.add(key.apiCollectionId + "|" + key.url + "|" + key.method + "|" + subType);

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

// NOTE: do NOT additionally filter on "testResults.resultTypeAgentic": true here --
// ~10 genuinely-agentic docs (testSuperType in AGENT_GOAL_HIJACK, MEMORY_AND_CONTEXT_POISONING,
// etc.) have that flag missing/false on every testResults[] entry despite being real agentic
// results. The dashboard's own "Passed" count (2,182) only relies on `vulnerable: false`
// scoped to the latest-per-run set -- verified to match exactly without the extra filter.
db.testing_run_result.find({
  testRunResultSummaryId: { $in: summaryIds },
  vulnerable: false
}).forEach(doc => {
  const k = doc.apiInfoKey.apiCollectionId + "|" + doc.apiInfoKey.url + "|" + doc.apiInfoKey.method + "|" + doc.testSubType;
  if (issueKeys.has(k)) return; // already emitted as an Issue row above -- don't duplicate

  const info = templateInfo(doc.testSubType);
  const conversationId = doc.testResults && doc.testResults[0] ? doc.testResults[0].conversationId : null;
  const hasError = doc.testResults && doc.testResults.some(t => t.errors && t.errors.length > 0);
  emit(
    { testSubType: doc.testSubType, method: doc.apiInfoKey.method, url: doc.apiInfoKey.url, name: info.name, severity: info.severity, category: info.category },
    "Non-Issue",
    { issueStatus: hasError ? "Execution Error" : "", testingRun: runName(doc.testRunResultSummaryId), turns: turnsFor(conversationId) }
  );
});
