package com.akto.agent;

import com.akto.dao.context.Context;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;

import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * The pre/post request script selection of the testing run a conversation belongs to. The two values
     * are the raw TestingRunConfig ids and must stay raw: null means "default script", "" means "no script"
     * (see ScriptSelection), so nothing here may normalise, trim or default them.
     */
    public static final class ConversationScripts {
        @Getter
        private final String preRequestScriptId;
        @Getter
        private final String postRequestScriptId;
        private final long registeredAtMs;

        public ConversationScripts(String preRequestScriptId, String postRequestScriptId) {
            this.preRequestScriptId = preRequestScriptId;
            this.postRequestScriptId = postRequestScriptId;
            this.registeredAtMs = Context.epochInMillis();
        }
    }

    private static final int DEFAULT_MAX_SIZE = 10_000;
    private static final long DEFAULT_TTL_MS = 60 * 60 * 1000;
    /**
     * Backstop only: the owner unregisters when its conversation ends. Longer than any conversation
     * (AGENT_CHAT_READ_TIMEOUT_SECONDS per turn), so a live one is not dropped back to the default script.
     */
    private static final int MAX_TRACKED_CONVERSATION_SCRIPTS = 10_000;
    private static final long CONVERSATION_SCRIPTS_TTL_MS = 24L * 60 * 60 * 1000;

    private final Map<String, JobEntry> store = new ConcurrentHashMap<>();
    private final Map<String, ConversationScripts> conversationScripts = Collections.synchronizedMap(
            new LinkedHashMap<String, ConversationScripts>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ConversationScripts> eldest) {
                    return size() > MAX_TRACKED_CONVERSATION_SCRIPTS;
                }
            });
    private final Map<String, List<String>> conversationToJobIds = new ConcurrentHashMap<>();
    private final Set<String> liteLLMConversationIds = ConcurrentHashMap.newKeySet();
    private final Map<String, String> jobIdToConversationResultDocId = new ConcurrentHashMap<>();
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

    public void markLiteLLMConversation(String conversationId) {
        if (conversationId != null && !conversationId.isEmpty()) {
            liteLLMConversationIds.add(conversationId);
        }
    }

    public boolean isLiteLLMConversation(String conversationId) {
        return conversationId != null && liteLLMConversationIds.contains(conversationId);
    }
    /** Idempotent: registering the same conversation again replaces its selection. */
    public void registerConversationScripts(String conversationId, String preRequestScriptId, String postRequestScriptId) {
        if (conversationId == null || conversationId.isEmpty()) {
            return;
        }
        conversationScripts.put(conversationId, new ConversationScripts(preRequestScriptId, postRequestScriptId));
    }

    /** Null when nothing is registered (never registered, already unregistered, or expired); that is not "no script". */
    public ConversationScripts getConversationScripts(String conversationId) {
        if (conversationId == null) {
            return null;
        }
        ConversationScripts scripts = conversationScripts.get(conversationId);
        if (scripts != null && Context.epochInMillis() - scripts.registeredAtMs > CONVERSATION_SCRIPTS_TTL_MS) {
            conversationScripts.remove(conversationId);
            return null;
        }
        return scripts;
    }

    public void unregisterConversationScripts(String conversationId) {
        if (conversationId != null) {
            conversationScripts.remove(conversationId);
        }
    }

    public void putConversationResultDocId(String jobId, String docId) {
        if (jobId != null && !jobId.isEmpty() && docId != null && !docId.isEmpty()) {
            jobIdToConversationResultDocId.put(jobId, docId);
        }
    }

    public String getConversationResultDocId(String jobId) {
        return jobId != null ? jobIdToConversationResultDocId.get(jobId) : null;
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
