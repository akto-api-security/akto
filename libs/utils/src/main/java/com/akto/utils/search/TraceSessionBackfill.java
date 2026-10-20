package com.akto.utils.search;

import com.akto.utils.elasticsearch.AgentQueryRecord;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Groups Bedrock traces stored before ingest assigned sessions into conversations. Stored traces
 * don't keep the caller ARN live ingest groups by, so these are grouped by the same service and
 * prompt with no more than 20 minutes between calls. Only traces without a session are touched.
 */
public final class TraceSessionBackfill {

    static final long SESSION_GAP_MS = 20 * 60 * 1000L;
    static final String SESSION_ID_PREFIX = "bedrock-legacy-";
    static final int BATCH_SIZE = 500;
    private static final int MAX_RECORDS = 200_000;

    private final Map<String, String> sessionByKey = new HashMap<>();
    private final Map<String, Long> lastSeenByKey = new HashMap<>();

    /** Returns how many traces were given a session. */
    public static int run(SearchClient client, int accountId, long startMs, long endMs) {
        TraceSessionBackfill backfill = new TraceSessionBackfill();
        Map<String, String> batch = new LinkedHashMap<>();
        int[] updated = {0};
        client.scrollRecordsWithoutSession(accountId, "bedrock-runtime.", startMs, endMs, BATCH_SIZE, MAX_RECORDS, record -> {
            batch.put(record.getDocId(), backfill.sessionIdFor(record));
            if (batch.size() >= BATCH_SIZE) {
                updated[0] += client.bulkUpdateSessionIds(batch);
                batch.clear();
            }
        });
        return updated[0] + client.bulkUpdateSessionIds(batch);
    }

    /** Records must arrive oldest first, as scrollRecordsWithoutSession delivers them. */
    String sessionIdFor(AgentQueryRecord record) {
        // Prompt is hashed so the maps hold short keys, not whole prompts.
        String key = record.getServiceId() + "|" + uuid(record.getQueryPayload());
        long ts = record.getTimeStampMs();
        Long lastSeen = lastSeenByKey.put(key, ts);
        if (lastSeen == null || ts - lastSeen > SESSION_GAP_MS) {
            sessionByKey.put(key, SESSION_ID_PREFIX + uuid(key + "|" + ts));
        }
        return sessionByKey.get(key);
    }

    private static UUID uuid(String s) {
        return UUID.nameUUIDFromBytes(s.getBytes(StandardCharsets.UTF_8));
    }
}
