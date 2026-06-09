package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class TestRunStatusAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TestRunStatusAction.class, LogDb.DASHBOARD);
    private static final int MAX_SUMMARY_IDS = 50;
    private static final ExecutorService executorService = Executors.newFixedThreadPool(8);

    @Setter
    private List<String> testingRunResultSummaryHexIds;

    @Getter
    private Map<String, Map<String, Integer>> testRunStatusSummaries = new HashMap<>();

    public String fetchTestRunStatusSummaries() {
        try {
            if (testingRunResultSummaryHexIds == null || testingRunResultSummaryHexIds.isEmpty()) {
                addActionError("Missing required parameter: testingRunResultSummaryHexIds");
                return ERROR.toUpperCase();
            }

            if (testingRunResultSummaryHexIds.size() > MAX_SUMMARY_IDS) {
                addActionError("Too many summary ids. Maximum allowed is " + MAX_SUMMARY_IDS);
                return ERROR.toUpperCase();
            }

            int accountId = Context.accountId.get();
            Map<String, Map<String, Integer>> summaries = new ConcurrentHashMap<>();
            List<Future<?>> futures = new ArrayList<>();

            for (String summaryHexId : testingRunResultSummaryHexIds) {
                if (summaryHexId == null || summaryHexId.trim().isEmpty()) {
                    continue;
                }
                final String normalizedSummaryHexId = summaryHexId.trim();
                futures.add(executorService.submit(() -> {
                    Context.accountId.set(accountId);
                    try {
                        ObjectId summaryId = new ObjectId(normalizedSummaryHexId);
                        summaries.put(normalizedSummaryHexId,
                                TestRunStatusHelper.computeStatusCountsCached(accountId, summaryId));
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e,
                                "Error computing test run status for summary: " + normalizedSummaryHexId);
                    }
                }));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error computing test run status summary");
                }
            }

            this.testRunStatusSummaries = summaries;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error fetching test run status summaries: " + e.getMessage());
            addActionError("Error fetching test run status summaries");
            return ERROR.toUpperCase();
        }

        return SUCCESS.toUpperCase();
    }
}
