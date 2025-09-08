package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.ExplainVerbosity;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import lombok.Getter;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import com.mongodb.ConnectionString;
import com.akto.DaoInit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestResultsStatsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestResultsStatsAction.class, LogDb.DASHBOARD);

    private String testingRunResultSummaryHexId;
    private String testingRunHexId;
    private String regexPattern;
    private String patternType; // optional: one of [HTTP_429, HTTP_5XX, CLOUDFLARE]

    @Getter
    private int count = 0;

    @Getter
    private String statusCounts = "";

    /**
     * Comprehensive regex patterns for different error types
     * 
     * HTTP 429 Rate Limiting Errors:
     * - Standard rate limiting detection
     * 
     * HTTP 5xx Server Errors (includes ALL Cloudflare 5xx errors):
     * - 500-599: Standard server errors and Cloudflare 520-530 series
     * - References:
     * https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-5xx-errors/
     * 
     * Cloudflare Non-HTTP Error Identifiers:
     * - 1xxx series (1000-1999): DNS, firewall, security issues
     * Reference:
     * https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/
     * - 10xxx series (10000+): API, redirects, configuration issues
     * Reference:
     * https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-10xxx-errors/
     * - cf-ray, cloudflare identifiers: Request tracking and troubleshooting
     * Reference:
     * https://developers.cloudflare.com/fundamentals/reference/cloudflare-ray-id/
     */
    private static final String REGEX_429 = "\"statusCode\"\\s*:\\s*429";
    private static final String REGEX_5XX = "\"statusCode\"\\s*:\\s*5[0-9][0-9]";
    /**
     * Cloudflare Blocking/Error Detection (NOT including cf-ray headers)
     * 
     * This regex focuses on actual Cloudflare blocking scenarios, not all CF
     * traffic:
     * - 1xxx series errors: DNS, firewall, security blocks
     * - 10xxx series errors: API/redirect configuration issues
     * - Specific blocking messages: Access denied, rate limited, etc.
     * - Does NOT include cf-ray header (which appears on ALL responses including
     * 2xx)
     */
    private static final String REGEX_CLOUDFLARE_BLOCKED = "(error\\s*1[0-9]{3}|error\\s*10[0-9]{3}|" +
            "access\\s*denied|rate\\s*limited|" +
            "attention\\s*required.*cloudflare|" +
            "blocked.*cloudflare|" +
            "security\\s*service.*protect|" +
            "ray\\s*id.*blocked)";

    public String fetchTestResultsStatsCount() {
        try {
            ObjectId testingRunResultSummaryId;

            // Input validation
            if (this.testingRunResultSummaryHexId == null || this.testingRunResultSummaryHexId.trim().isEmpty()) {
                addActionError("Missing required parameter: testingRunResultSummaryHexId");
                return ERROR.toUpperCase();
            }

            try {
                testingRunResultSummaryId = new ObjectId(this.testingRunResultSummaryHexId);
            } catch (Exception e) {
                addActionError("Invalid test summary id: " + e.getMessage());
                return ERROR.toUpperCase();
            }

            // Check if requesting comprehensive stats (all error types)
            if (shouldFetchAllStats()) {
                return fetchAllErrorStats(testingRunResultSummaryId);
            }

            // Single pattern request
            String resolvedRegex = resolveRegexPattern();
            String description = describePattern(resolvedRegex);

            this.count = getCountByPattern(testingRunResultSummaryId, resolvedRegex);

            loggerMaker.debugAndAddToDb(
                    "Found " + count + " requests matching " + description + " for test summary: "
                            + testingRunResultSummaryHexId,
                    LogDb.DASHBOARD);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching test results stats: " + e.getMessage());
            addActionError("Error fetching test results stats");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    private String fetchAllErrorStats(ObjectId testingRunResultSummaryId) {
        try {
            // Get counts for each error type using predefined patterns
            int count429 = getCountByPattern(testingRunResultSummaryId, REGEX_429);
            int count5xx = getCountByPattern(testingRunResultSummaryId, REGEX_5XX);
            int countCloudflare = getCountByPattern(testingRunResultSummaryId, REGEX_CLOUDFLARE_BLOCKED);

            // Format response for frontend consumption (pipe-separated)
            this.statusCounts = count429 + "|" + count5xx + "|" + countCloudflare;
            this.count = count429 + count5xx + countCloudflare; // Total for backward compatibility

            loggerMaker.debugAndAddToDb(
                    "Comprehensive error stats for test summary " + testingRunResultSummaryHexId +
                            " - 429: " + count429 + ", 5xx: " + count5xx + ", Cloudflare: " + countCloudflare,
                    LogDb.DASHBOARD);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching comprehensive error stats: " + e.getMessage());
            addActionError("Error fetching comprehensive error stats");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    private boolean shouldFetchAllStats() {
        return "ALL".equalsIgnoreCase(this.patternType) ||
                "COMPREHENSIVE".equalsIgnoreCase(this.patternType) ||
                (this.patternType == null && this.regexPattern == null);
    }

    /**
     * Resolves regex pattern from explicit pattern, type, or default
     */
    private String resolveRegexPattern() {
        if (this.regexPattern != null && !this.regexPattern.trim().isEmpty()) {
            return this.regexPattern;
        }

        String type = this.patternType != null ? this.patternType.trim().toUpperCase() : "";
        switch (type) {
            case "HTTP_429":
            case "429":
            case "RATE_LIMIT":
                return REGEX_429;
            case "HTTP_5XX":
            case "5XX":
            case "SERVER_ERROR":
                return REGEX_5XX;
            case "CLOUDFLARE":
            case "CDN":
            case "CF":
                return REGEX_CLOUDFLARE_BLOCKED;
            default:
                return REGEX_429; // Default for backward compatibility
        }
    }

    /**
     * Provides human-readable description of the pattern being used
     */
    private String describePattern(String regex) {
        if (REGEX_429.equals(regex))
            return "429 Rate Limiting";
        if (REGEX_5XX.equals(regex))
            return "5xx Server Errors (includes Cloudflare 520-530)";
        if (REGEX_CLOUDFLARE_BLOCKED.equals(regex))
            return "Cloudflare Blocking/Errors (1xxx, 10xxx, blocking phrases)";
        return "custom pattern";
    }

    /**
     * Core aggregation pipeline for pattern-based error counting
     * Optimized for performance with proper indexing hints and limits
     */
    private int getCountByPattern(ObjectId testingRunResultSummaryId, String regex) {
        List<Bson> pipeline = new ArrayList<>();

        // Stage 1: Filter documents by summary ID and ensure testResults.message exists
        pipeline.add(Aggregates.match(
                Filters.and(
                        Filters.eq("testRunResultSummaryId", testingRunResultSummaryId),
                        Filters.eq("vulnerable", false),
                        Filters.exists("testResults.message", true))));

        // Stage 2: Sort by latest results and limit to prevent memory exhaustion
        pipeline.add(Aggregates.sort(Sorts.descending("endTimestamp")));
        pipeline.add(Aggregates.limit(10000));

        // Stage 3: Project last message from testResults array for processing
        pipeline.add(Aggregates.project(
                Projections.computed("lastMessage",
                        new BasicDBObject("$arrayElemAt",
                                Arrays.asList("$testResults.message", -1)))));

        // Stage 4: Filter for pattern via regex (case insensitive for Cloudflare
        // errors)
        pipeline.add(Aggregates.match(
                Filters.regex("lastMessage", regex, "i")));

        // Stage 5: Count matching documents and return single result
        pipeline.add(Aggregates.count("count"));

        // Performance monitoring in development/debug mode
        if (shouldRunExplain()) {
            explainAggregationPipeline(pipeline);
        }

        // Execute aggregation with proper resource cleanup
        MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();

        int resultCount = 0;
        if (cursor.hasNext()) {
            BasicDBObject result = cursor.next();
            resultCount = result.getInt("count", 0);
        }

        cursor.close();
        return resultCount;
    }

    private void explainAggregationPipeline(List<Bson> pipeline) {
        try {
            loggerMaker.debugAndAddToDb("=== RUNNING AGGREGATION EXPLAIN ===", LogDb.DASHBOARD);

            Document explainResult = TestingRunResultDao.instance.getRawCollection()
                    .aggregate(pipeline)
                    .explain(ExplainVerbosity.EXECUTION_STATS);

            if (explainResult != null) {
                loggerMaker.debugAndAddToDb("=== AGGREGATION EXPLAIN RESULTS ===", LogDb.DASHBOARD);
                loggerMaker.debugAndAddToDb("Full Explain: " + explainResult.toJson(), LogDb.DASHBOARD);

                loggerMaker.infoAndAddToDb("=== AGGREGATION EXPLAIN RESULTS ===", LogDb.DASHBOARD);
                loggerMaker.infoAndAddToDb("Full Explain: " + explainResult.toJson(), LogDb.DASHBOARD);

                analyzeExplainResults(explainResult);
            } else {
                loggerMaker.debugAndAddToDb("No explain result returned", LogDb.DASHBOARD);
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error running explain on aggregation pipeline: " + e.getMessage());
        }
    }

    /**
     * Analyzes explain results to determine if indexes are being used effectively
     */
    private void analyzeExplainResults(Document explanation) {
        try {
            loggerMaker.debugAndAddToDb("=== ANALYZING EXPLAIN RESULTS ===", LogDb.DASHBOARD);

            @SuppressWarnings("unchecked")
            List<Document> stages = explanation.get("stages", List.class);
            if (stages != null && !stages.isEmpty()) {
                loggerMaker.debugAndAddToDb("Found " + stages.size() + " stages", LogDb.DASHBOARD);
                for (int i = 0; i < stages.size(); i++) {
                    Document stage = stages.get(i);
                    loggerMaker.debugAndAddToDb("Analyzing stage " + (i + 1) + ": " + stage.keySet(), LogDb.DASHBOARD);
                    analyzeStage(stage);
                }
            } else {
                loggerMaker.debugAndAddToDb("No stages found in explain result", LogDb.DASHBOARD);
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error analyzing explain results: " + e.getMessage());
        }
    }

    /**
     * Analyzes individual aggregation stages to check for index usage
     */
    private void analyzeStage(Document stage) {
        try {
            Document cursor = stage.get("$cursor", Document.class);
            if (cursor != null) {
                loggerMaker.debugAndAddToDb("Found $cursor stage", LogDb.DASHBOARD);

                Document queryPlanner = cursor.get("queryPlanner", Document.class);
                if (queryPlanner != null) {
                    Document winningPlan = queryPlanner.get("winningPlan", Document.class);
                    if (winningPlan != null) {
                        String stageName = winningPlan.getString("stage");
                        loggerMaker.debugAndAddToDb("Query Stage: " + stageName, LogDb.DASHBOARD);

                        loggerMaker.infoAndAddToDb("Query Stage: " + stageName, LogDb.DASHBOARD);

                        if ("IXSCAN".equals(stageName)) {
                            String indexName = winningPlan.getString("indexName");
                            loggerMaker.debugAndAddToDb("✓ USING INDEX: " + indexName, LogDb.DASHBOARD);
                            loggerMaker.infoAndAddToDb("✓ Using Index: " + indexName, LogDb.DASHBOARD);
                            checkCompoundIndexUsage(winningPlan);
                        } else if ("COLLSCAN".equals(stageName)) {
                            loggerMaker.debugAndAddToDb("⚠ COLLECTION SCAN - NO INDEX USED", LogDb.DASHBOARD);
                            loggerMaker.infoAndAddToDb("⚠ Using Collection Scan - No Index Used!", LogDb.DASHBOARD);
                        } else {
                            loggerMaker.debugAndAddToDb("Stage type: " + stageName, LogDb.DASHBOARD);
                        }
                    }
                }

                Document executionStats = cursor.get("executionStats", Document.class);
                if (executionStats != null) {
                    Integer executionTimeMillis = executionStats.getInteger("executionTimeMillis");
                    Integer nReturned = executionStats.getInteger("nReturned");
                    Integer totalDocsExamined = executionStats.getInteger("totalDocsExamined");
                    Integer totalKeysExamined = executionStats.getInteger("totalKeysExamined");

                    String statsMsg = String.format(
                            "Execution Stats - Time: %dms, Returned: %d, DocsExamined: %d, KeysExamined: %d",
                            executionTimeMillis, nReturned, totalDocsExamined, totalKeysExamined);

                    loggerMaker.infoAndAddToDb(statsMsg, LogDb.DASHBOARD);
                }
            } else {
                loggerMaker.debugAndAddToDb("No $cursor stage found in: " + stage.keySet(), LogDb.DASHBOARD);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error analyzing stage: " + e.getMessage());
        }
    }

    /**
     * Checks if our specific partial index is being used
     */
    private void checkCompoundIndexUsage(Document winningPlan) {
        try {
            String indexName = winningPlan.getString("indexName");
            if (indexName != null) {
                loggerMaker.debugAndAddToDb("Index being used: " + indexName, LogDb.DASHBOARD);

                if (indexName.contains("testRunResultSummaryId") &&
                        indexName.contains("vulnerable") &&
                        indexName.contains("endTimestamp")) {
                    loggerMaker.debugAndAddToDb("✓ USING OUR OPTIMIZED PARTIAL INDEX: " + indexName, LogDb.DASHBOARD);
                    loggerMaker.infoAndAddToDb("✓ Using our optimized partial index: " + indexName, LogDb.DASHBOARD);
                } else {
                    loggerMaker.debugAndAddToDb("Using different index: " + indexName, LogDb.DASHBOARD);
                    loggerMaker.infoAndAddToDb("Using different index: " + indexName, LogDb.DASHBOARD);
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error checking compound index usage: " + e.getMessage());
        }
    }

    /**
     * Determines when to run explain - controlled by system property or environment
     */
    private boolean shouldRunExplain() {
        String explainMode = System.getProperty("mongodb.explain.aggregation", "false");
        boolean shouldExplain = "true".equalsIgnoreCase(explainMode) ||
                "development".equalsIgnoreCase(System.getProperty("environment"));
        return shouldExplain;
    }

    /**
     * Helper method to list all indexes in the collection for debugging
     */
    public void listAllIndexes() {
        try {
            loggerMaker.debugAndAddToDb("=== LISTING ALL INDEXES ===", LogDb.DASHBOARD);

            List<Document> indexes = TestingRunResultDao.instance.getRawCollection()
                    .listIndexes().into(new ArrayList<>());

            for (Document index : indexes) {
                String indexName = index.getString("name");
                Document keys = index.get("key", Document.class);
                Document partialFilter = index.get("partialFilterExpression", Document.class);

                loggerMaker.debugAndAddToDb("Index: " + indexName, LogDb.DASHBOARD);
                loggerMaker.debugAndAddToDb("  Keys: " + (keys != null ? keys.toJson() : "null"), LogDb.DASHBOARD);
                if (partialFilter != null) {
                    loggerMaker.debugAndAddToDb("  Partial Filter: " + partialFilter.toJson(), LogDb.DASHBOARD);
                }
                loggerMaker.debugAndAddToDb("---", LogDb.DASHBOARD);
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error listing indexes: " + e.getMessage());
        }
    }

    // Standard getters and setters for Struts2 action parameter binding
    public String getTestingRunResultSummaryHexId() {
        return testingRunResultSummaryHexId;
    }

    public void setTestingRunResultSummaryHexId(String testingRunResultSummaryHexId) {
        this.testingRunResultSummaryHexId = testingRunResultSummaryHexId;
    }

    public String getTestingRunHexId() {
        return testingRunHexId;
    }

    public void setTestingRunHexId(String testingRunHexId) {
        this.testingRunHexId = testingRunHexId;
    }

    public String getRegexPattern() {
        return regexPattern;
    }

    public void setRegexPattern(String regexPattern) {
        this.regexPattern = regexPattern;
    }

    public String getPatternType() {
        return patternType;
    }

    public void setPatternType(String patternType) {
        this.patternType = patternType;
    }
}
