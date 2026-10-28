package com.akto.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;
import com.google.gson.Gson;

/**
 * Drops sensitive sample writes that repeat the exact same sample for the same param within a window.
 * Older mini-runtime versions re-push the same sample on every sync, which only rewrites an identical array.
 * TODO: remove once all mini-runtimes run with pushSensitiveSampleOnce.
 */
public class SensitiveSampleWriteDedupe {

    public static final String ENV_FLAG = "DEDUPE_SENSITIVE_SAMPLE_WRITES";

    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final int MAX_ENTRIES = 200_000;
    private static final Gson gson = new Gson();

    // key (account + param filters + payload hash) -> last time it was seen
    private static final ConcurrentHashMap<String, Long> lastSeenMs = new ConcurrentHashMap<>();

    public static boolean isEnabled() {
        return "true".equalsIgnoreCase(System.getenv(ENV_FLAG));
    }

    public static List<BulkUpdates> filter(int accountId, List<BulkUpdates> writes) {
        return filter(accountId, writes, System.currentTimeMillis());
    }

    static List<BulkUpdates> filter(int accountId, List<BulkUpdates> writes, long nowMs) {
        if (writes == null || writes.isEmpty()) return writes;

        evictIfFull(nowMs);

        List<BulkUpdates> kept = new ArrayList<>();
        for (BulkUpdates write : writes) {
            String key = keyOf(accountId, write);
            if (key == null) {
                kept.add(write);
                continue;
            }
            // sliding window: every sighting, including a skipped one, restarts the window
            Long prev = lastSeenMs.put(key, nowMs);
            if (prev != null && nowMs - prev < WINDOW_MS) continue;
            kept.add(write);
        }
        return kept;
    }

    /** null means "do not dedupe this write": deletes, or anything that is not a plain sample push. */
    private static String keyOf(int accountId, BulkUpdates write) {
        try {
            if (write == null || write.getFilters() == null || write.getUpdates() == null) return null;

            boolean hasPush = false;
            StringBuilder sb = new StringBuilder();
            sb.append(accountId);
            for (Map.Entry<String, Object> e : new TreeMap<>(write.getFilters()).entrySet()) {
                sb.append('|').append(e.getKey()).append('=').append(e.getValue());
            }
            for (String update : write.getUpdates()) {
                UpdatePayload payload = gson.fromJson(update, UpdatePayload.class);
                String op = payload == null ? null : payload.getOp();
                if ("pushEach".equalsIgnoreCase(op)) {
                    hasPush = true;
                } else if (!"setOnInsert".equalsIgnoreCase(op)) {
                    return null;
                }
                sb.append('|').append(update);
            }
            if (!hasPush) return null;

            return sha256(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256(String s) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static void evictIfFull(long nowMs) {
        if (lastSeenMs.size() < MAX_ENTRIES) return;
        Iterator<Map.Entry<String, Long>> it = lastSeenMs.entrySet().iterator();
        while (it.hasNext()) {
            if (nowMs - it.next().getValue() >= WINDOW_MS) it.remove();
        }
        if (lastSeenMs.size() >= MAX_ENTRIES) lastSeenMs.clear();
    }

    static void resetForTests() {
        lastSeenMs.clear();
    }
}
