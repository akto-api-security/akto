package com.akto.jobs.executors.salesforce;

import com.akto.log.LoggerMaker;

import java.util.*;

/**
 * Manages state for Salesforce sync operations.
 * Tracks processed IDs and pagination offset to avoid reprocessing data.
 */
public class SalesforceStateManager {

    private static final LoggerMaker logger = new LoggerMaker(SalesforceStateManager.class);

    private final Set<String> processedIds;
    private int offset;
    private final int maxCachedIds;

    public SalesforceStateManager(int maxCachedIds) {
        this.processedIds = Collections.synchronizedSet(new HashSet<>());
        this.offset = 0;
        this.maxCachedIds = maxCachedIds;
    }

    /**
     * Mark IDs as processed.
     * Automatically cleans up old IDs when cache exceeds max size.
     */
    public void markAsProcessed(String id) {
        processedIds.add(id);

        // Clean up old IDs if cache is getting too large
        if (processedIds.size() > maxCachedIds) {
            // Keep only the most recent IDs
            List<String> idList = new ArrayList<>(processedIds);
            idList.sort(String::compareTo);

            Set<String> newSet = new HashSet<>();
            for (int i = Math.max(0, idList.size() - (maxCachedIds / 2)); i < idList.size(); i++) {
                newSet.add(idList.get(i));
            }

            processedIds.clear();
            processedIds.addAll(newSet);

            logger.info("Cleaned up processed IDs cache. Size: {}", processedIds.size());
        }
    }

    /**
     * Mark multiple IDs as processed.
     */
    public void markMultipleAsProcessed(List<String> ids) {
        if (ids != null) {
            ids.forEach(this::markAsProcessed);
        }
    }

    /**
     * Check if an ID has been processed.
     */
    public boolean isProcessed(String id) {
        return processedIds.contains(id);
    }

    /**
     * Get unprocessed IDs from a list.
     */
    public List<String> filterNewIds(List<String> ids) {
        List<String> newIds = new ArrayList<>();
        for (String id : ids) {
            if (!isProcessed(id)) {
                newIds.add(id);
            }
        }
        return newIds;
    }

    /**
     * Get current offset.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Increment offset.
     */
    public void incrementOffset(int count) {
        this.offset += count;
        logger.debug("Offset incremented to: {}", this.offset);
    }

    /**
     * Reset offset to start from beginning.
     */
    public void resetOffset() {
        this.offset = 0;
        logger.info("Offset reset to 0");
    }

    /**
     * Get number of processed IDs.
     */
    public int getProcessedCount() {
        return processedIds.size();
    }

    /**
     * Clear all state.
     */
    public void clear() {
        processedIds.clear();
        offset = 0;
        logger.info("State cleared");
    }
}
