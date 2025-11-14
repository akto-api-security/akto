package com.akto.utils;

import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.Account;
import com.akto.dto.AccountSettings;
import com.akto.dto.dependency_flow.DependencyFlow;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.Key;
import com.akto.log.LoggerMaker;
import com.akto.util.AccountTask;
import com.mongodb.client.MongoCursor;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class TagMismatchCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TagMismatchCron.class, LoggerMaker.LogDb.CYBORG);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public void runCron() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        evaluateTagsMismatch(t.getId());
                    }
                }, "evaluateTagsMismatch");
            }
        }, 10000, 100000, TimeUnit.MINUTES);

    }

        /**
     * Checks if all samples in the list match the tags mismatch pattern.
     * Returns true if ALL samples contain both:
     * 1. destIp with value "127.0.0.1:15001" or "0.0.0.0:0"
     * 2. direction with value "1"
     */
    private static boolean isTagsMismatch(List<String> samples) {
        if (samples == null || samples.isEmpty()) {
            return false;
        }

        // Regex patterns to match destIp and direction with spacing variations
        Pattern destIpPattern = Pattern.compile("\"destIp\"\\s*:\\s*\"(127\\.0\\.0\\.1:15001|0\\.0\\.0\\.0:0)\"");
        Pattern directionPattern = Pattern.compile("\"direction\"\\s*:\\s*\"1\"");

        for (String sample : samples) {
            if (sample == null) {
                return false;
            }

            boolean hasDestIp = destIpPattern.matcher(sample).find();
            boolean hasDirection = directionPattern.matcher(sample).find();

            // If any sample doesn't match both patterns, return false
            if (!hasDestIp || !hasDirection) {
                return false;
            }
        }

        // All samples matched both patterns
        return true;
    }

    /**
     * Process all SampleData documents for the given account and evaluate tags mismatch.
     * Uses cursor-based pagination with batch size of 1000.
     */
    private void evaluateTagsMismatch(int accountId) {
        try {
            Context.accountId.set(accountId);
            loggerMaker.infoAndAddToDb("Starting tags mismatch evaluation for account: " + accountId);

            int batchSize = 1000;
            int totalProcessed = 0;
            int mismatchCount = 0;

            MongoCursor<SampleData> cursor = SampleDataDao.instance.getMCollection()
                .find()
                .batchSize(batchSize)
                .iterator();

            try {
                while (cursor.hasNext()) {
                    SampleData sampleData = cursor.next();
                    totalProcessed++;

                    List<String> samples = sampleData.getSamples();
                    if (samples != null && !samples.isEmpty()) {
                        boolean mismatch = isTagsMismatch(samples);
                        if (mismatch) {
                            mismatchCount++;
                            Key id = sampleData.getId();
                            loggerMaker.errorAndAddToDb(
                                String.format("Tags mismatch detected - apiCollectionId: %d, method: %s, url: %s, responseCode: %d",
                                    id.getApiCollectionId(), id.getMethod(), id.getUrl(), id.getResponseCode())
                            );
                        }
                    }
                }
            } finally {
                cursor.close();
            }

            loggerMaker.infoAndAddToDb(
                String.format("Completed tags mismatch evaluation for account: %d, total processed: %d, mismatches found: %d",
                    accountId, totalProcessed, mismatchCount)
            );

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                String.format("Error in evaluateTagsMismatch for account %d: %s", accountId, e.toString())
            );
        }
    }

}
