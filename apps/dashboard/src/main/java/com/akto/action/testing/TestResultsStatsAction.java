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

    @Getter
    private int count = 0;

    public String fetchTestResultsStatsCount() {
        try {
            ObjectId testingRunResultSummaryId;

            // Input validation to prevent invalid ObjectId parsing errors
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

            // Build optimized aggregation pipeline for 429 status code counting
            List<Bson> pipeline = new ArrayList<>();

            // Stage 1: Filter documents by summary ID and ensure testResults.message exists
            pipeline.add(Aggregates.match(
                    Filters.and(
                            Filters.eq("testRunResultSummaryId", testingRunResultSummaryId),
                            Filters.eq("vulnerable", false),
                            Filters.exists("testResults.message", true)
                    )));

            // Stage 2: Sort by latest results and limit to prevent memory exhaustion
            pipeline.add(Aggregates.sort(Sorts.descending("endTimestamp")));
            pipeline.add(Aggregates.limit(10000));

            // Stage 3: Project last message from testResults array for processing
            pipeline.add(Aggregates.project(
                    Projections.computed("lastMessage",
                            new BasicDBObject("$arrayElemAt", 
                                    Arrays.asList("$testResults.message", -1)))));

            // Stage 4: Filter for HTTP 429 (Too Many Requests) status codes via regex
            pipeline.add(Aggregates.match(
                    Filters.regex("lastMessage", "\"statusCode\"\\s*:\\s*429")));

            // Stage 5: Count matching documents and return single result
            pipeline.add(Aggregates.count("count"));


            //Run the Main Method for testing of index
            // Run explain functionality
            if (shouldRunExplain()) {
                explainAggregationPipeline(pipeline);
            }

            // Execute aggregation with proper resource cleanup
            MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection()
                    .aggregate(pipeline, BasicDBObject.class).cursor();

            if (cursor.hasNext()) {
                BasicDBObject result = cursor.next();
                this.count = result.getInt("count", 0);
            } else {
                this.count = 0;
            }

            cursor.close();

            loggerMaker.debugAndAddToDb(
                    "Found " + count + " requests with 429 status code for test summary: " + testingRunResultSummaryHexId,
                    LogDb.DASHBOARD);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching 429 requests count: " + e.getMessage());
            addActionError("Error fetching 429 requests count");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    /**
     * Explains the aggregation pipeline to check index usage and performance
     */
    private void explainAggregationPipeline(List<Bson> pipeline) {
        try {
            System.out.println("=== RUNNING AGGREGATION EXPLAIN ===");
            
            // CORRECTED: Use explain() on the collection, not on the result
            Document explainResult = TestingRunResultDao.instance.getRawCollection()
                    .aggregate(pipeline)
                    .explain(ExplainVerbosity.EXECUTION_STATS);

            if (explainResult != null) {
                System.out.println("=== AGGREGATION EXPLAIN RESULTS ===");
                System.out.println("Full Explain: " + explainResult.toJson());
                
                // Also log using LoggerMaker
                loggerMaker.infoAndAddToDb("=== AGGREGATION EXPLAIN RESULTS ===", LogDb.DASHBOARD);
                loggerMaker.infoAndAddToDb("Full Explain: " + explainResult.toJson(), LogDb.DASHBOARD);
                
                // Extract and log key performance metrics
                analyzeExplainResults(explainResult);
            } else {
                System.out.println("No explain result returned");
            }
            
        } catch (Exception e) {
            System.err.println("Error running explain on aggregation pipeline: " + e.getMessage());
            e.printStackTrace();
            loggerMaker.errorAndAddToDb(e, "Error running explain on aggregation pipeline: " + e.getMessage());
        }
    }

    /**
     * Analyzes explain results to determine if indexes are being used effectively
     */
    private void analyzeExplainResults(Document explanation) {
        try {
            System.out.println("=== ANALYZING EXPLAIN RESULTS ===");
            
            // Check stages for index usage
            @SuppressWarnings("unchecked")
            List<Document> stages = explanation.get("stages", List.class);
            if (stages != null && !stages.isEmpty()) {
                System.out.println("Found " + stages.size() + " stages");
                for (int i = 0; i < stages.size(); i++) {
                    Document stage = stages.get(i);
                    System.out.println("Analyzing stage " + (i + 1) + ": " + stage.keySet());
                    analyzeStage(stage);
                }
            } else {
                System.out.println("No stages found in explain result");
            }

        } catch (Exception e) {
            System.err.println("Error analyzing explain results: " + e.getMessage());
            e.printStackTrace();
            loggerMaker.errorAndAddToDb(e, "Error analyzing explain results: " + e.getMessage());
        }
    }

    /**
     * Analyzes individual aggregation stages to check for index usage
     */
    private void analyzeStage(Document stage) {
        try {
            // Look for $cursor stage which indicates index usage
            Document cursor = stage.get("$cursor", Document.class);
            if (cursor != null) {
                System.out.println("Found $cursor stage");
                
                Document queryPlanner = cursor.get("queryPlanner", Document.class);
                if (queryPlanner != null) {
                    Document winningPlan = queryPlanner.get("winningPlan", Document.class);
                    if (winningPlan != null) {
                        String stageName = winningPlan.getString("stage");
                        System.out.println("Query Stage: " + stageName);
                        
                        loggerMaker.infoAndAddToDb("Query Stage: " + stageName, LogDb.DASHBOARD);
                        
                        // Check if using index scan vs collection scan
                        if ("IXSCAN".equals(stageName)) {
                            String indexName = winningPlan.getString("indexName");
                            System.out.println("✓ USING INDEX: " + indexName);
                            loggerMaker.infoAndAddToDb("✓ Using Index: " + indexName, LogDb.DASHBOARD);
                            checkCompoundIndexUsage(winningPlan);
                        } else if ("COLLSCAN".equals(stageName)) {
                            System.out.println("⚠ COLLECTION SCAN - NO INDEX USED");
                            loggerMaker.infoAndAddToDb("⚠ Using Collection Scan - No Index Used!", LogDb.DASHBOARD);
                        } else {
                            System.out.println("Stage type: " + stageName);
                        }
                    }
                }
                
                // Check execution stats if available
                Document executionStats = cursor.get("executionStats", Document.class);
                if (executionStats != null) {
                    Integer executionTimeMillis = executionStats.getInteger("executionTimeMillis");
                    Integer nReturned = executionStats.getInteger("nReturned");
                    Integer totalDocsExamined = executionStats.getInteger("totalDocsExamined");
                    Integer totalKeysExamined = executionStats.getInteger("totalKeysExamined");
                    
                    String statsMsg = String.format(
                        "Execution Stats - Time: %dms, Returned: %d, DocsExamined: %d, KeysExamined: %d",
                        executionTimeMillis, nReturned, totalDocsExamined, totalKeysExamined);
                    
                    System.out.println(statsMsg);
                    loggerMaker.infoAndAddToDb(statsMsg, LogDb.DASHBOARD);
                }
            } else {
                System.out.println("No $cursor stage found in: " + stage.keySet());
            }
        } catch (Exception e) {
            System.err.println("Error analyzing stage: " + e.getMessage());
            e.printStackTrace();
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
                System.out.println("Index being used: " + indexName);
                
                if (indexName.contains("testRunResultSummaryId") && 
                    indexName.contains("vulnerable") && 
                    indexName.contains("endTimestamp")) {
                    System.out.println("✓ USING OUR OPTIMIZED PARTIAL INDEX: " + indexName);
                    loggerMaker.infoAndAddToDb("✓ Using our optimized partial index: " + indexName, LogDb.DASHBOARD);
                } else {
                    System.out.println("Using different index: " + indexName);
                    loggerMaker.infoAndAddToDb("Using different index: " + indexName, LogDb.DASHBOARD);
                }
            }
        } catch (Exception e) {
            System.err.println("Error checking compound index usage: " + e.getMessage());
            loggerMaker.errorAndAddToDb(e, "Error checking compound index usage: " + e.getMessage());
        }
    }

    /**
     * Determines when to run explain - can be controlled by system property or environment
     */
    private boolean shouldRunExplain() {
        String explainMode = System.getProperty("mongodb.explain.aggregation", "false");
        boolean shouldExplain = "true".equalsIgnoreCase(explainMode) || 
                               "development".equalsIgnoreCase(System.getProperty("environment"));
        
        System.out.println("Should run explain: " + shouldExplain + " (property: " + explainMode + ")");
        return shouldExplain;
    }

    /**
     * Helper method to list all indexes in the collection for debugging
     */
    public void listAllIndexes() {
        try {
            System.out.println("=== LISTING ALL INDEXES ===");
            
            List<Document> indexes = TestingRunResultDao.instance.getRawCollection()
                    .listIndexes().into(new ArrayList<>());
            
            for (Document index : indexes) {
                String indexName = index.getString("name");
                Document keys = index.get("key", Document.class);
                Document partialFilter = index.get("partialFilterExpression", Document.class);
                
                System.out.println("Index: " + indexName);
                System.out.println("  Keys: " + (keys != null ? keys.toJson() : "null"));
                if (partialFilter != null) {
                    System.out.println("  Partial Filter: " + partialFilter.toJson());
                }
                System.out.println("---");
            }
            
        } catch (Exception e) {
            System.err.println("Error listing indexes: " + e.getMessage());
            e.printStackTrace();
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
}

