package com.akto.utils;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.data_actor.DbLayer;
import com.akto.dto.Account;
import com.akto.dto.ApiCollection;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.traffic.Key;
import com.akto.log.LoggerMaker;
import com.akto.util.AccountTask;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class TagMismatchCron {

    private static final LoggerMaker loggerMaker = new LoggerMaker(TagMismatchCron.class, LoggerMaker.LogDb.CYBORG);
    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    public static final List<Integer> TAGS_MISMATCH_ACCOUNT_IDS = Arrays.asList(1736798101, 1718042191);

    // Static in-memory HashMap to store API collection ID to hostname mapping
    private static Map<Integer, String> apiCollectionIdHostNameMap = new HashMap<>();

    // Static block to initialize the HashMap when class is loaded
    static {
        loadApiCollections();
    }

    /**
     * Loads all API collections from database into the static HashMap.
     * Maps apiCollectionId -> hostName for quick lookup.
     * Only stores collections with non-null and non-blank hostNames.
     */
    public static void loadApiCollections() {
        try {
            List<ApiCollection> apiCollections = DbLayer.fetchAllApiCollections();
            Map<Integer, String> newMap = new HashMap<>();

            if (apiCollections != null) {
                for (ApiCollection collection : apiCollections) {
                    String hostName = collection.getHostName();
                    // Only add to map if hostName is not null and not blank
                    if (hostName != null && !hostName.trim().isEmpty()) {
                        newMap.put(collection.getId(), hostName);
                    }
                }
            }

            apiCollectionIdHostNameMap = newMap;
            loggerMaker.infoAndAddToDb(
                String.format("Loaded %d API collections with hostNames into memory", apiCollectionIdHostNameMap.size())
            );
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                String.format("Error loading API collections: %s", e.toString())
            );
        }
    }

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
     * Filters out collection IDs that should be ignored based on hostname matching logic.
     * Directly modifies the input set by removing collection IDs that should be ignored.
     *
     * Logic: Extract service name from hostname and check if it's a substring of the remaining hostname.
     * Example: "local.ebe-my-payment-service.svc:5000-my-payment-service"
     * - Split by "."
     * - Last segment: "svc:5000-my-payment-service"
     * - Find first dash in last segment: at index 8 (after "5000")
     * - Extract service name (everything after first dash in last segment): "my-payment-service"
     * - Remaining hostname: everything before that dash = "local.ebe-my-payment-service.svc:5000"
     * - Check if remaining hostname contains the extracted service name
     * - If yes, ignore this collection (remove from set)
     *
     * @param uniqueCollectionIds Set of collection IDs to filter (modified in place)
     */
    public static void ignoreSameHostNameCollections(Set<Integer> uniqueCollectionIds) {
        // Use iterator to safely remove elements while iterating
        Iterator<Integer> iterator = uniqueCollectionIds.iterator();

        while (iterator.hasNext()) {
            Integer collectionId = iterator.next();
            String hostname = apiCollectionIdHostNameMap.get(collectionId);

            if (hostname == null) {
                continue;
            }

            // Split hostname by "."
            String[] parts = hostname.split("\\.");

            // Check if hostname has the expected format
            if (parts.length == 0) {
                continue;
            }

            // Get the last segment
            String lastPart = parts[parts.length - 1];

            // Check if last part contains "-"
            if (!lastPart.contains("-")) {
                continue;
            }

            // Find the first dash in the last segment
            int dashIndexInLastSegment = lastPart.indexOf("-");

            // Calculate position of this dash in the full hostname
            int dashIndexInHostname = hostname.length() - lastPart.length() + dashIndexInLastSegment;

            // Extract service name: everything after the dash
            String extractedServiceName = lastPart.substring(dashIndexInLastSegment+ 1);

            // If extracted service name is empty, skip
            if (extractedServiceName.isEmpty()) {
                continue;
            }

            // Remaining hostname: everything before the dash
            String remainingHostName = hostname.substring(0, dashIndexInHostname);

            // Check if remaining hostname contains the extracted service name
            if (remainingHostName.contains(extractedServiceName)) {
                iterator.remove();
                loggerMaker.infoAndAddToDb(
                    String.format("Ignoring collection %d - remaining hostname: %s, extracted service: %s",
                        collectionId, remainingHostName, extractedServiceName)
                );
            }
        }
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

            // Store original size to track how many were filtered
            int originalSize = uniqueCollectionIds.size();

            // Filter out collections that should be ignored based on hostname logic
            // This method modifies uniqueCollectionIds in place
            ignoreSameHostNameCollections(uniqueCollectionIds);

            int filteredCount = originalSize - uniqueCollectionIds.size();
            if (filteredCount > 0) {
                loggerMaker.infoAndAddToDb(
                    String.format("Ignored %d collections based on hostname filtering", filteredCount)
                );
            }

            if (uniqueCollectionIds.isEmpty()) {
                loggerMaker.infoAndAddToDb("All collections were filtered out, nothing to update");
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
     * Removes tags-mismatch tags from collections that no longer have mismatches.
     *
     * @param allCollectionsSeen All collection IDs that were evaluated in this cron run
     * @param collectionsWithMismatches Collection IDs that currently have mismatches
     */
    private void removeTagsFromResolvedCollections(Set<Integer> allCollectionsSeen, Set<Integer> collectionsWithMismatches) {
        try {
            // Find collections that were evaluated but don't have mismatches
            Set<Integer> collectionsToCleanup = new HashSet<>(allCollectionsSeen);
            collectionsToCleanup.removeAll(collectionsWithMismatches);

            if (collectionsToCleanup.isEmpty()) {
                loggerMaker.infoAndAddToDb("No collections need tags-mismatch tag removal");
                return;
            }

            // Build bulk operations to REMOVE tags-mismatch from these collections
            List<WriteModel<com.akto.dto.ApiCollection>> pullOperations = new ArrayList<>();
            for (Integer collectionId : collectionsToCleanup) {
                Bson filter = Filters.eq("_id", collectionId);
                Bson pullUpdate = Updates.pull(
                    com.akto.dto.ApiCollection.TAGS_STRING,
                    Filters.eq(CollectionTags.KEY_NAME, "tags-mismatch")
                );
                pullOperations.add(new UpdateOneModel<>(filter, pullUpdate));
            }

            // Execute bulk write to remove tags
            if (!pullOperations.isEmpty()) {
                ApiCollectionsDao.instance.getMCollection().bulkWrite(pullOperations);
                loggerMaker.infoAndAddToDb(
                    String.format("Removed tags-mismatch tag from %d API collections that no longer have mismatches",
                        collectionsToCleanup.size())
                );
            }

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                String.format("Error in removeTagsFromResolvedCollections: %s", e.toString())
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

            // Refresh API collections cache at the start of each cron run
            loadApiCollections();

            loggerMaker.infoAndAddToDb("Starting tags mismatch evaluation for account: " + accountId);
            long cronStartTime = System.currentTimeMillis();

            int batchSize = 1000;
            int totalProcessed = 0;
            int mismatchCount = 0;

            // Track all collections seen and those with mismatches
            Set<Integer> allCollectionsSeen = new HashSet<>();
            Set<Integer> collectionsWithMismatches = new HashSet<>();

            // Cursor pagination state
            Integer lastApiCollectionId = null;
            String lastUrl = null;
            String lastMethod = null;

            while (true) {
                // Build cursor filter
                Bson cursorFilter;
                if (lastApiCollectionId == null) {
                    // First batch - no filter
                    cursorFilter = new BasicDBObject();
                } else {
                    // Subsequent batches - continue from last seen
                    cursorFilter = Filters.or(
                        Filters.gt("_id.apiCollectionId", lastApiCollectionId),
                        Filters.and(
                            Filters.eq("_id.apiCollectionId", lastApiCollectionId),
                            Filters.gt("_id.url", lastUrl)
                        ),
                        Filters.and(
                            Filters.eq("_id.apiCollectionId", lastApiCollectionId),
                            Filters.eq("_id.url", lastUrl),
                            Filters.gt("_id.method", lastMethod)
                        )
                    );
                }

                // Sort by the 3 key fields
                Bson sort = Sorts.orderBy(
                    Sorts.ascending("_id.apiCollectionId"),
                    Sorts.ascending("_id.url"),
                    Sorts.ascending("_id.method")
                );

                // Fetch batch
                long queryStartTime = System.currentTimeMillis();
                List<SampleData> batch = SampleDataDao.instance.getMCollection()
                    .find(cursorFilter)
                    .sort(sort)
                    .limit(batchSize)
                    .into(new ArrayList<>());
                long queryTime = System.currentTimeMillis() - queryStartTime;

                if (batch.isEmpty()) {
                    break;
                }

                long batchStartTime = System.currentTimeMillis();

                loggerMaker.infoAndAddToDb(
                    String.format("Processing batch with %d documents (query time: %d ms, last seen: apiCollectionId=%s, url=%s, method=%s)",
                        batch.size(), queryTime, lastApiCollectionId, lastUrl, lastMethod)
                );

                List<SampleData> mismatchedSamples = new ArrayList<>();

                for (SampleData sampleData : batch) {
                    totalProcessed++;

                    int collectionId = sampleData.getId().getApiCollectionId();
                    allCollectionsSeen.add(collectionId);

                    List<String> samples = sampleData.getSamples();
                    if (samples != null && !samples.isEmpty()) {
                        boolean mismatch = isTagsMismatch(samples);
                        if (mismatch) {
                            mismatchCount++;
                            mismatchedSamples.add(sampleData);
                            collectionsWithMismatches.add(collectionId);
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

                // Update cursor position
                if (!batch.isEmpty()) {
                    SampleData lastDoc = batch.get(batch.size() - 1);
                    lastApiCollectionId = lastDoc.getId().getApiCollectionId();
                    lastUrl = lastDoc.getId().getUrl();
                    lastMethod = lastDoc.getId().getMethod().toString();
                }

                long batchDuration = System.currentTimeMillis() - batchStartTime;
                loggerMaker.infoAndAddToDb(
                    String.format("Completed batch, total processed so far: %d, mismatches so far: %d, batch processing time: %d ms",
                        totalProcessed, mismatchCount, batchDuration)
                );
            }

            // Remove tags from collections that no longer have mismatches
            removeTagsFromResolvedCollections(allCollectionsSeen, collectionsWithMismatches);

            long cronDuration = System.currentTimeMillis() - cronStartTime;
            loggerMaker.infoAndAddToDb(
                String.format("Completed tags mismatch evaluation for account: %d, total processed: %d, mismatches found: %d, time: %d ms",
                    accountId, totalProcessed, mismatchCount, cronDuration)
            );

        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(
                String.format("Error in evaluateTagsMismatch for account %d: %s", accountId, e.toString())
            );
        }
    }

}
