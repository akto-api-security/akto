package com.akto.utility;

import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpResponse;

import lombok.Getter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for API execution jobs (akto-utility-server).
 * Evicts oldest entries when max size is reached; optional TTL enforced on read.
 */
public class ApiExecutionJobStore {

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED
    }

    public static final class JobEntry {
        @Getter
        private final Status status;
        @Getter
        private final OriginalHttpResponse response;
        @Getter
        private final String errorMessage;
        @Getter
        private final long createdAtMs;
        @Getter
        private final String conversationId;

        public JobEntry(Status status, OriginalHttpResponse response, String errorMessage, long createdAtMs, String conversationId) {
            this.status = status;
            this.response = response;
            this.errorMessage = errorMessage;
            this.createdAtMs = createdAtMs;
            this.conversationId = conversationId;
        }

        public static JobEntry pending(String conversationId) {
            return new JobEntry(Status.PENDING, null, null, Context.epochInMillis(), conversationId);
        }

        public static JobEntry completed(OriginalHttpResponse response, String conversationId) {
            return new JobEntry(Status.COMPLETED, response, null, Context.epochInMillis(), conversationId);
        }

        public static JobEntry failed(String errorMessage, String conversationId) {
            return new JobEntry(Status.FAILED, null, errorMessage, Context.epochInMillis(), conversationId);
        }
    }

    private static final int DEFAULT_MAX_SIZE = 10_000;
    private static final long DEFAULT_TTL_MS = 60 * 60 * 1000; // 1 hour

    private final Map<String, JobEntry> store = new ConcurrentHashMap<>();
    private final int maxSize;
    private final long ttlMs;

    public ApiExecutionJobStore() {
        this(DEFAULT_MAX_SIZE, DEFAULT_TTL_MS);
    }

    public ApiExecutionJobStore(int maxSize, long ttlMs) {
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.ttlMs = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;
    }

    /**
     * Put a new pending job. If at capacity, evicts oldest entry.
     */
    public void put(String jobId, JobEntry entry) {
        if (store.size() >= maxSize) {
            evictOldest();
        }
        store.put(jobId, entry);
    }

    public JobEntry get(String jobId) {
        JobEntry entry = store.get(jobId);
        if (entry == null) {
            return null;
        }
        if (ttlMs > 0 && (Context.epochInMillis() - entry.getCreatedAtMs() > ttlMs)) {
            store.remove(jobId);
            return null;
        }
        return entry;
    }

    public void update(String jobId, JobEntry entry) {
        store.put(jobId, entry);
    }

    public void remove(String jobId) {
        store.remove(jobId);
    }

    private void evictOldest() {
        String oldestKey = null;
        long oldestMs = Long.MAX_VALUE;
        for (Map.Entry<String, JobEntry> e : store.entrySet()) {
            long created = e.getValue().getCreatedAtMs();
            if (created < oldestMs) {
                oldestMs = created;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            store.remove(oldestKey);
        }
    }
}

