package com.akto.utils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.Key;
import com.akto.log.LoggerMaker;
import com.akto.util.AccountTask;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class TagMismatchCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TagMismatchCron.class, LoggerMaker.LogDb.CYBORG);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final List<Integer> TAGS_MISMATCH_ACCOUNT_IDS = Arrays.asList(1736798101, 1718042191);

    public void runCron() {
        scheduler.scheduleAtFixedRate(new Runnable() {
            public void run() {
                AccountTask.instance.executeTask(new Consumer<Account>() {
                    @Override
                    public void accept(Account t) {
                        if(!TAGS_MISMATCH_ACCOUNT_IDS.contains(t.getId())){
                            return;
                        }
                        evaluateTagsMismatch(t.getId());
                    }
                }, "evaluateTagsMismatch");
            }
        }, 0, 1, TimeUnit.HOURS);

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
     * Handles mismatched samples by updating ApiCollection tags in bulk.
     * Adds or updates "tags-mismatch" tag for all affected collections.
     *
     * @param mismatchedSamples List of SampleData objects that have tag mismatches
     */
    private void handleMismatchedSamples(List<SampleData> mismatchedSamples) {
        if (mismatchedSamples == null || mismatchedSamples.isEmpty()) {
            return;
        }

        try {
            // Extract unique apiCollectionIds
            Set<Integer> uniqueCollectionIds = new HashSet<>();
            for (SampleData sampleData : mismatchedSamples) {
                uniqueCollectionIds.add(sampleData.getId().getApiCollectionId());
            }

            if (uniqueCollectionIds.isEmpty()) {
                return;
            }

            // Create the tag to insert/update
            CollectionTags tagsMismatchTag = new CollectionTags(
                Context.now(),
                "tags-mismatch",
                "true",
                CollectionTags.TagSource.USER
            );

            // Step 1: Build bulk operations to REMOVE existing tags
            List<WriteModel<com.akto.dto.ApiCollection>> pullOperations = new ArrayList<>();
            for (Integer collectionId : uniqueCollectionIds) {
                Bson filter = Filters.eq("_id", collectionId);
                Bson pullUpdate = Updates.pull(
                    com.akto.dto.ApiCollection.TAGS_STRING,
                    Filters.eq(CollectionTags.KEY_NAME, "tags-mismatch")
                );
                pullOperations.add(new UpdateOneModel<>(filter, pullUpdate));
            }

            // Execute first bulk write to remove existing tags
            if (!pullOperations.isEmpty()) {
                ApiCollectionsDao.instance.getMCollection().bulkWrite(pullOperations);
            }

            // Step 2: Build bulk operations to ADD new tags
            List<WriteModel<com.akto.dto.ApiCollection>> pushOperations = new ArrayList<>();
            for (Integer collectionId : uniqueCollectionIds) {
                Bson filter = Filters.eq("_id", collectionId);
                Bson pushUpdate = Updates.push(
                    com.akto.dto.ApiCollection.TAGS_STRING,
                    tagsMismatchTag
                );
                pushOperations.add(new UpdateOneModel<>(filter, pushUpdate));
            }

            // Execute second bulk write to add new tags
            if (!pushOperations.isEmpty()) {
                ApiCollectionsDao.instance.getMCollection().bulkWrite(pushOperations);
                loggerMaker.infoAndAddToDb(
                    String.format("Updated tags-mismatch tag for %d API collections", uniqueCollectionIds.size())
                );
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                String.format("Error in handleMismatchedSamples: %s", e.toString())
            );
        }
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
            int skip = 0;

            while (true) {
                List<SampleData> batch = SampleDataDao.instance.getMCollection()
                    .find()
                    .skip(skip)
                    .limit(batchSize)
                    .into(new ArrayList<>());

                if (batch.isEmpty()) {
                    break;
                }

                long batchStartTime = System.currentTimeMillis();

                loggerMaker.infoAndAddToDb(
                    String.format("Processing batch at skip %d with %d documents", skip, batch.size())
                );

                List<SampleData> mismatchedSamples = new ArrayList<>();

                for (SampleData sampleData : batch) {
                    totalProcessed++;

                    List<String> samples = sampleData.getSamples();
                    if (samples != null && !samples.isEmpty()) {
                        boolean mismatch = isTagsMismatch(samples);
                        if (mismatch) {
                            mismatchCount++;
                            mismatchedSamples.add(sampleData);
                            Key id = sampleData.getId();
                            loggerMaker.infoAndAddToDb(
                                String.format("Tags mismatch detected - apiCollectionId: %d, method: %s, url: %sd",
                                    id.getApiCollectionId(), id.getMethod(), id.getUrl())
                            );
                        }
                    }
                }

                // Handle mismatched samples for this batch
                handleMismatchedSamples(mismatchedSamples);

                skip += batchSize;

                long batchDuration = System.currentTimeMillis() - batchStartTime;
                loggerMaker.infoAndAddToDb(
                    String.format("Completed batch, total processed so far: %d, mismatches so far: %d, batch processing time: %d ms",
                        totalProcessed, mismatchCount, batchDuration)
                );
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
