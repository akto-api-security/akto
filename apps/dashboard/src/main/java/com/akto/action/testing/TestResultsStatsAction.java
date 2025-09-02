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
            ObjectId testingRunId;

            // Check if parameters are provided
            if (this.testingRunResultSummaryHexId == null || this.testingRunResultSummaryHexId.trim().isEmpty() ||
                    this.testingRunHexId == null || this.testingRunHexId.trim().isEmpty()) {
                addActionError("Missing required parameters: testingRunResultSummaryHexId and testingRunHexId");
                return ERROR.toUpperCase();
            }

            try {
                testingRunResultSummaryId = new ObjectId(this.testingRunResultSummaryHexId);
                testingRunId = new ObjectId(this.testingRunHexId);
            } catch (Exception e) {
                addActionError("Invalid test summary id or test run id: " + e.getMessage());
                return ERROR.toUpperCase();
            }

            // Build aggregation pipeline similar to the MongoDB command
            List<Bson> pipeline = new ArrayList<>();

            // Match stage
            pipeline.add(Aggregates.match(
                    Filters.eq("testRunResultSummaryId", testingRunResultSummaryId)));

            // Project stage to get the last result
            pipeline.add(Aggregates.project(
                    Projections.computed("lastResult",
                            new BasicDBObject("$arrayElemAt",
                                    Arrays.asList(
                                            new BasicDBObject("$slice", Arrays.asList("$testResults", -1)),
                                            0)))));

            // Match stage to filter for 429 status codes
            pipeline.add(Aggregates.match(
                    Filters.and(
                            Filters.exists("lastResult.message"),
                            Filters.regex("lastResult.message", "\"statusCode\"\\s*:\\s*429"))));

            // Count stage
            pipeline.add(Aggregates.count("totalCount"));

            // Execute aggregation
            MongoCursor<BasicDBObject> cursor = TestingRunResultDao.instance.getMCollection()
                    .aggregate(pipeline, BasicDBObject.class).cursor();

            if (cursor.hasNext()) {
                BasicDBObject result = cursor.next();
                this.count = result.getInt("totalCount", 0);
            } else {
                this.count = 0;
            }

            cursor.close();

            loggerMaker.debugAndAddToDb(
                    "Found " + count + " requests with 429 status code for test run: " + testingRunHexId,
                    LogDb.DASHBOARD);

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching 429 requests count: " + e.getMessage());
            addActionError("Error fetching 429 requests count");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }

    // Getters and setters
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