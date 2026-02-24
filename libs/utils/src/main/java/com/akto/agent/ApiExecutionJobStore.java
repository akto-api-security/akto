package com.akto.agent;

import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
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
        private final OriginalHttpRequest request;
        @Getter
        private final OriginalHttpResponse response;
        @Getter
        private final String errorMessage;
        @Getter
        private final long createdAtMs;
        @Getter
        private final String conversationId;

        public JobEntry(Status status, OriginalHttpRequest request, OriginalHttpResponse response, String errorMessage, long createdAtMs, String conversationId) {
            this.status = status;
            this.request = request;
            this.response = response;
            this.errorMessage = errorMessage;
            this.createdAtMs = createdAtMs;
            this.conversationId = conversationId;
        }

        public static JobEntry pending(String conversationId) {
            return new JobEntry(Status.PENDING, null, null, null, Context.epochInMillis(), conversationId);
        }

        public static JobEntry completed(OriginalHttpResponse response, String conversationId, OriginalHttpRequest request) {
            return new JobEntry(Status.COMPLETED, request, response, null, Context.epochInMillis(), conversationId);
        }

        public static JobEntry failed(String errorMessage, String conversationId, OriginalHttpRequest request) {
            return new JobEntry(Status.FAILED, request, null, errorMessage, Context.epochInMillis(), conversationId);
        }
    }

    private static final int DEFAULT_MAX_SIZE = 10_000;
    private static final long DEFAULT_TTL_MS = 60 * 60 * 1000;

    private final Map<String, JobEntry> store = new ConcurrentHashMap<>();
    private final Map<String, List<String>> conversationToJobIds = new ConcurrentHashMap<>();
    private final int maxSize;
    private final long ttlMs;

    public ApiExecutionJobStore() {
        this(DEFAULT_MAX_SIZE, DEFAULT_TTL_MS);
    }

    public ApiExecutionJobStore(int maxSize, long ttlMs) {
        this.maxSize = maxSize > 0 ? maxSize : DEFAULT_MAX_SIZE;
        this.ttlMs = ttlMs > 0 ? ttlMs : DEFAULT_TTL_MS;
    }

    public void put(String jobId, JobEntry entry) {
        if (store.size() >= maxSize) {
            evictOldest();
        }
        store.put(jobId, entry);
        String cid = entry.getConversationId();
        if (cid != null && !cid.isEmpty()) {
            conversationToJobIds.computeIfAbsent(cid, k -> new ArrayList<>()).add(jobId);
        }
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

    public List<String> getJobIdsByConversationId(String conversationId) {
        List<String> list = conversationToJobIds.get(conversationId);
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
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
