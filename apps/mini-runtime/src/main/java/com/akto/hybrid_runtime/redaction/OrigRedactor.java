package com.akto.hybrid_runtime.redaction;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Masks the request and response payloads inside stored sample messages (the raw "orig" JSON that
 * becomes sample data and sensitive sample data) through guardrails-service.
 *
 * One instance covers one sync. {@link #prepare} sends every distinct sample it has not seen to the
 * service in batches and remembers the outcome; {@link #lookup} then answers per sample, so a
 * payload that is both a sample and a sensitive example is sent once.
 *
 * Only the requestPayload and responsePayload fields are rewritten; every other field of the
 * message is left exactly as it was. A sample whose payload could not be redacted is returned
 * unchanged and flagged, and the caller stores it with {@link #withMarker}.
 */
public class OrigRedactor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(OrigRedactor.class, LogDb.RUNTIME);
    private static final ObjectMapper mapper = new ObjectMapper();

    /** Marker key written into a stored sample whose payloads were not fully redacted. */
    public static final String MARKER_KEY = "aktoPayloadRedaction";
    public static final String MARKER_UNAVAILABLE = RedactionResult.MARKER_UNAVAILABLE;
    public static final String MARKER_BASIC = RedactionResult.MARKER_BASIC;

    /**
     * The message to store, and the marker it must carry (null when it was fully redacted or clean).
     * When {@link #skip} is true the message must not be stored at all.
     */
    public static final class Result {
        public final String orig;
        public final String marker;
        public final boolean skip;

        Result(String orig, String marker, boolean skip) {
            this.orig = orig;
            this.marker = marker;
            this.skip = skip;
        }
    }

    private static final class Pending {
        final String key;
        final String orig;
        final ObjectNode root;
        final RedactionItem item;

        Pending(String key, String orig, ObjectNode root, RedactionItem item) {
            this.key = key;
            this.orig = orig;
            this.root = root;
            this.item = item;
        }
    }

    private final GuardrailRedactionClient client;
    private final long deadlineAtMs;
    private final boolean skipWhenUnavailable;
    // Concurrent: processSampleBatch reads on a worker thread that can outlive its batch timeout
    // while the next prepare is already writing.
    private final Map<String, Result> memo = new ConcurrentHashMap<>();

    public OrigRedactor(GuardrailRedactionClient client, int budgetMs) {
        this(client, budgetMs, false);
    }

    public OrigRedactor(GuardrailRedactionClient client, int budgetMs, boolean skipWhenUnavailable) {
        this.client = client;
        this.deadlineAtMs = System.currentTimeMillis() + budgetMs;
        this.skipWhenUnavailable = skipWhenUnavailable;
    }

    /** Skipping applies to UNAVAILABLE only; BASIC (regex scrub applied) and clean results are stored. */
    private Result result(String orig, String marker) {
        return new Result(orig, marker, skipWhenUnavailable && MARKER_UNAVAILABLE.equals(marker));
    }

    /** Redacts every message not already handled in this sync. Never throws. */
    public void prepare(Collection<String> origs) {
        try {
            doPrepare(origs);
        } catch (Exception e) {
            // Anything not memoised is answered UNAVAILABLE by lookup, so nothing reads as redacted.
            // Only the exception type is logged: parser messages quote the raw payload.
            loggerMaker.errorAndAddToDb("Payload redaction pre-pass failed: " + e.getClass().getSimpleName());
        }
    }

    private void doPrepare(Collection<String> origs) {
        List<Pending> pending = new ArrayList<>();
        Set<String> queued = new HashSet<>();
        for (String orig : origs) {
            if (orig == null) {
                continue;
            }
            String key = sha256(orig);
            if (memo.containsKey(key) || !queued.add(key)) {
                continue;
            }
            Pending p;
            try {
                p = toPending(key, orig, pending.size());
            } catch (Exception e) {
                // A message that cannot be read cannot be shown to be clean: it is not redacted.
                loggerMaker.error("Could not read a sample message for payload redaction: " + e.getClass().getSimpleName());
                memo.put(key, result(orig, MARKER_UNAVAILABLE));
                continue;
            }
            if (p == null) {
                memo.put(key, result(orig, null)); // no payload in it, nothing to redact
            } else {
                pending.add(p);
            }
        }

        if (pending.isEmpty()) {
            return;
        }
        List<RedactionItem> items = new ArrayList<>(pending.size());
        for (Pending p : pending) {
            items.add(p.item);
        }
        Map<String, RedactionResult> results = client.redact(items, deadlineAtMs);
        for (Pending p : pending) {
            memo.put(p.key, apply(p, results.get(p.item.getId())));
        }
    }

    /** Null when the message has no payload to redact. Throws when it cannot be read. */
    private Pending toPending(String key, String orig, int index) throws Exception {
        JsonNode node = mapper.readTree(orig);
        if (node == null || !node.isObject()) {
            return null;
        }
        ObjectNode root = (ObjectNode) node;
        String requestPayload = text(root, "requestPayload");
        String responsePayload = text(root, "responsePayload");
        if (isEmpty(requestPayload) && isEmpty(responsePayload)) {
            return null;
        }
        RedactionItem item = new RedactionItem(String.valueOf(index), text(root, "method"), hostOf(root),
                text(root, "path"), requestPayload, responsePayload);
        return new Pending(key, orig, root, item);
    }

    private Result apply(Pending p, RedactionResult answer) {
        RedactionResult.Side request = answer == null ? null : answer.getRequest();
        RedactionResult.Side response = answer == null ? null : answer.getResponse();
        boolean changed = applySide(p, "requestPayload", p.item.getRequestPayload(), request);
        changed |= applySide(p, "responsePayload", p.item.getResponsePayload(), response);
        String marker = RedactionResult.markerFor(p.item, answer);
        if (!changed) {
            return result(p.orig, marker);
        }
        String masked = serialize(p.root);
        // If the masked message cannot be written, the original is kept and must say so.
        return masked != null ? result(masked, marker) : result(p.orig, MARKER_UNAVAILABLE);
    }

    /** True when the field was replaced with its masked form. */
    private static boolean applySide(Pending p, String field, String sentPayload, RedactionResult.Side side) {
        if (sentPayload == null || side == null || side.getPayload() == null) {
            return false;
        }
        p.root.put(field, side.getPayload());
        return true;
    }

    /** The message as JSON, or null if it cannot be written. */
    private static String serialize(ObjectNode root) {
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Could not serialise a redacted sample message: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * The message to store for this sample. A message that was never prepared (the pre-pass
     * failed, or was skipped) gets the UNAVAILABLE marker: it is explicitly unredacted, not assumed clean.
     */
    public Result lookup(String orig) {
        Result known = orig == null ? null : memo.get(sha256(orig));
        return known != null ? known : result(orig, MARKER_UNAVAILABLE);
    }

    /** Adds a redaction marker to a message about to be stored. Returns it unchanged if it cannot be parsed. */
    public static String withMarker(String stored, String marker) {
        try {
            JsonNode node = mapper.readTree(stored);
            if (node == null || !node.isObject()) {
                return stored;
            }
            ((ObjectNode) node).put(MARKER_KEY, marker);
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            loggerMaker.error("Could not mark a sample's redaction state: " + e.getClass().getSimpleName());
            return stored;
        }
    }

    private static String text(ObjectNode root, String field) {
        JsonNode n = root.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static boolean isEmpty(String s) {
        return s == null || s.isEmpty();
    }

    /** Host from the request headers, which a sample stores as a JSON string (or object) of header values. */
    private static String hostOf(ObjectNode root) {
        try {
            JsonNode headers = root.get("requestHeaders");
            if (headers == null || headers.isNull()) {
                return "";
            }
            if (headers.isTextual()) {
                headers = mapper.readTree(headers.asText());
            }
            if (headers != null && headers.isObject()) {
                java.util.Iterator<Map.Entry<String, JsonNode>> fields = headers.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> e = fields.next();
                    if ("host".equalsIgnoreCase(e.getKey())) {
                        JsonNode v = e.getValue();
                        return v.isArray() && v.size() > 0 ? v.get(0).asText() : v.asText();
                    }
                }
            }
        } catch (Exception ignored) {
            // an unreadable header block means no host, which only narrows policy scoping
        }
        return "";
    }

    static String sha256(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
