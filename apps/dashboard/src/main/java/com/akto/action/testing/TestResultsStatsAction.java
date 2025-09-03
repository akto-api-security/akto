package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestingRunResultDao;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import lombok.Getter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

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
                            //Filters.eq("vulnerable", false), 
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

            // Execute aggregation with proper resource cleanup
            MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection()
                    .aggregate(pipeline, BasicDBObject.class).cursor();

            if (cursor.hasNext()) {
                BasicDBObject result = cursor.next();
                this.count = result.getInt("count", 0);
            } else {
                this.count = 0; // No documents matched the criteria
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

    // Standard getters and setters for Struts2 action parameter binding
    public String getTestingRunResultSummaryHexId() {
        return testingRunResultSummaryHexId;
    }

    public void setTestingRunResultSummaryHexId(String testingRunResultSummaryHexId) {
        this.testingRunResultSummaryHexId = testingRunResultSummaryHexId;
    }
}
