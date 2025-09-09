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
import com.mongodb.client.model.Accumulators;
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
    private String patternType; // required: one of [HTTP_429, HTTP_5XX, CLOUDFLARE]

    @Getter
    private int count = 0;

    @Getter
    private boolean fromApiErrors = false;

    /**
     * HTTP 429 Rate Limiting Pattern
     * Detects JSON responses with statusCode: 429 indicating rate limiting.
     * Reference: https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/429
     */
    public static final String REGEX_429 = "\"statusCode\"\\s*:\\s*429";

    /**
     * HTTP 5xx Server Error Pattern
     * Detects JSON responses with statusCode: 500-599 indicating server errors.
     * Includes Cloudflare 520-530 series errors (Bad Gateway, Origin Down, etc.).
     * Reference:
     * https://developers.cloudflare.com/support/troubleshooting/http-status-codes/5xx-server-error/
     */
    public static final String REGEX_5XX = "\"statusCode\"\\s*:\\s*5[0-9][0-9]";

    /**
     * Cloudflare Security and CDN Error Pattern
     * Detects various Cloudflare blocking scenarios and error conditions:
     * 
     * Error Codes:
     * - 1xxx series (1000-1999): DNS resolution, firewall, security blocking
     * - 10xxx series (10000+): API limits, configuration issues
     * - Specific codes: 1012 (access denied), 1015 (rate limited), 1020 (access
     * denied)
     * 
     * Security Blocking:
     * - WAF (Web Application Firewall) triggered blocks
     * - Cloudflare security service protection messages
     * - Access denied and rate limiting scenarios
     * - Attention Required pages shown to users
     * 
     * Note: Excludes cf-ray headers which are present on all Cloudflare responses
     * including successful 2xx codes.
     * 
     * References:
     * -
     * -
     * https://developers.cloudflare.com/support/troubleshooting/http-status-codes/cloudflare-1xxx-errors/
     * - https://developers.cloudflare.com/waf/
     * - https://developers.cloudflare.com/fundamentals/reference/cloudflare-ray-id/
     * - Error page types:
     * https://developers.cloudflare.com/rules/custom-errors/reference/error-page-types/
     * - Block pages:
     * https://developers.cloudflare.com/cloudflare-one/policies/gateway/block-page/
     * - Custom errors: https://developers.cloudflare.com/rules/custom-errors/
     */
    public static final String REGEX_CLOUDFLARE = "(error\\s*1[0-9]{3}|error\\s*10[0-9]{3}|" + // CF-specific error
                                                                                               // codes
            "\\\"code\\\"\\s*:\\s*(10[0-9]{2}|1[0-9]{3,5})|" + // API error codes in JSON
            "attention\\s*required.*cloudflare|" + // Challenge page identifier
            "managed\\s*challenge.*cloudflare|cloudflare.*managed\\s*challenge|" + // Managed challenge variations
            "interactive\\s*challenge.*cloudflare|cloudflare.*interactive\\s*challenge|" + // Interactive challenges
            "under\\s*attack.*cloudflare|cloudflare.*under\\s*attack|" + // Under attack mode
            "ddos\\s*protection.*cloudflare|cloudflare.*ddos|" + // DDoS protection
            "anti[-\\s]*ddos.*cloudflare|cloudflare.*anti[-\\s]*ddos|" + // Anti-DDoS phrasing
            "ddos\\s*attack\\s*mitigation|attack\\s*mitigation.*cloudflare|" + // DDoS attack mitigation
            "ddos\\s*protection.*under\\s*attack.*enabled|under\\s*attack.*mode.*enabled|" + // Under attack mode
                                                                                             // enabled
            "(blocked|denied|limited|restricted).*cloudflare|" + // Generic blocking with CF context
            "cloudflare.*(blocked|denied|limited|restricted|security)|" + // CF context with blocking
            "waf.*cloudflare|cloudflare.*waf|" + // WAF by Cloudflare
            "\\bweb\\s*application\\s*firewall\\b|\\bWAF\\b|waf\\s*rule\\s*triggered|waf\\s*(block|security|alert|protection)|"
            + // WAF mentions without CF
            "rate.*limit.*cloudflare|cloudflare.*rate.*limit|" + // Rate limiting by CF
            "checking.*browser.*cloudflare|browser\\s*integrity\\s*check|verifying.*browser.*supports|" + // Browser
                                                                                                          // check
                                                                                                          // challenge
            "security.*check.*cloudflare|security.*verification.*cloudflare|" + // Security checks/verification
            "verification.*cloudflare|additional.*security.*cloudflare|" + // Additional security verification
            "security\\s*challenge.*cloudflare|cloudflare.*security\\s*challenge|" + // Security challenge variations
            "interactive\\s*challenge.*required|challenge.*required.*cloudflare|" + // Interactive challenge
                                                                                    // requirements
            "captcha\\s*verification|complete.*security\\s*check.*prove.*not.*robot|" + // CAPTCHA verification patterns
            "this\\s*website\\s*is\\s*using\\s*a\\s*security\\s*service\\s*to\\s*protect\\s*itself\\s*from\\s*online\\s*attacks|"
            + // Common CF block text
            "ray\\s*id.*blocked|blocked.*ray\\s*id)"; // Ray ID blocked variants

    public String fetchTestResultsStatsCount() {
        try {
            ObjectId testingRunResultSummaryId;

            // Input validation
            if (this.testingRunResultSummaryHexId == null || this.testingRunResultSummaryHexId.trim().isEmpty()) {
                addActionError("Missing required parameter: testingRunResultSummaryHexId");
                return ERROR.toUpperCase();
            }

            if (this.patternType == null || this.patternType.trim().isEmpty()) {
                addActionError("Missing required parameter: patternType");
                return ERROR.toUpperCase();
            }

            try {
                testingRunResultSummaryId = new ObjectId(this.testingRunResultSummaryHexId);
            } catch (Exception e) {
                addActionError("Invalid test summary id: " + e.getMessage());
                return ERROR.toUpperCase();
            }

            // Resolve regex pattern based on pattern type
            String resolvedRegex = resolveRegexPattern();
            if (resolvedRegex == null) {
                addActionError("Invalid pattern type. Supported types: HTTP_429, HTTP_5XX, CLOUDFLARE");
                return ERROR.toUpperCase();
            }

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

    /**
     * Resolves regex pattern from pattern type
     */
    private String resolveRegexPattern() {
        String type = this.patternType.trim().toUpperCase();
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
                return REGEX_CLOUDFLARE;
            default:
                return null; // Invalid pattern type
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
        if (REGEX_CLOUDFLARE.equals(regex))
            return "Cloudflare Blocking/Errors (1xxx, 10xxx, WAF, security blocks)";
        return "unknown pattern";
    }

    /**
     * Core aggregation pipeline for pattern-based error counting
     * Optimized for performance with proper indexing hints and limits
     */
    private int getCountByPattern(ObjectId testingRunResultSummaryId, String regex) {
        if (hasApiErrors(testingRunResultSummaryId)) {
            this.fromApiErrors = true;
            return getCountFromApiErrors(testingRunResultSummaryId);
        }

        this.fromApiErrors = false;

        List<Bson> pipeline = new ArrayList<>();

        // Stage 1: Filter documents by summary ID and ensure testResults.message exists
        pipeline.add(Aggregates.match(
                Filters.and(
                        Filters.eq("testRunResultSummaryId", testingRunResultSummaryId),
                        // Filters.eq("vulnerable", false),
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

        if (shouldRunExplain()) {
            explainAggregationPipeline(pipeline);
        }

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

    private boolean hasApiErrors(ObjectId testingRunResultSummaryId) {
        try {
            String key = resolveApiErrorsKey();
            if (key == null)
                return false;
            BasicDBObject filter = new BasicDBObject("testRunResultSummaryId", testingRunResultSummaryId)
                    .append("vulnerable", false)
                    .append("apiErrors." + key, new BasicDBObject("$exists", true));
            return TestingRunResultDao.instance.getMCollection().find(filter).limit(1).first() != null;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error checking apiErrors existence: " + e.getMessage());
            return false;
        }
    }

    private int getCountFromApiErrors(ObjectId testingRunResultSummaryId) {
        String key = resolveApiErrorsKey();
        if (key == null)
            return 0;

        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(
                Filters.and(
                        Filters.eq("testRunResultSummaryId", testingRunResultSummaryId),
                        Filters.eq("vulnerable", false),
                        Filters.exists("apiErrors." + key, true))));

        pipeline.add(Aggregates.group(null, Accumulators.sum("count", "$apiErrors." + key)));

        MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class).cursor();

        int resultCount = 0;
        if (cursor.hasNext()) {
            BasicDBObject result = cursor.next();
            Number n = (Number) result.get("count");
            resultCount = n != null ? n.intValue() : 0;
        }
        cursor.close();
        return resultCount;
    }

    private String resolveApiErrorsKey() {
        String type = this.patternType == null ? null : this.patternType.trim().toUpperCase();
        if (type == null)
            return null;
        switch (type) {
            case "HTTP_429":
            case "429":
            case "RATE_LIMIT":
                return "429";
            case "HTTP_5XX":
            case "5XX":
            case "SERVER_ERROR":
                return "5xx";
            case "CLOUDFLARE":
            case "CDN":
            case "CF":
                return "cloudflare";
            default:
                return null;
        }
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

    public String getPatternType() {
        return patternType;
    }

    public void setPatternType(String patternType) {
        this.patternType = patternType;
    }
}
