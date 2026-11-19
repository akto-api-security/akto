package com.akto.threat.detection.cache;

import com.akto.data_actor.DataActor;
import com.akto.dto.ApiSequences;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLMethods.Method;
import com.akto.dto.type.URLTemplate;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.RuntimeUtil;
import com.akto.threat.detection.utils.DataReuse;
import com.akto.threat.detection.utils.SessionIdentifier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

// Flags a session that calls an API without first calling the APIs that almost always precede it,
// or that takes several steps in a row that users almost never take.
// Rules ("B requires A", "C requires A, B") and known steps come from cyborg's api_sequences.
public class SequenceCache {

    private static final LoggerMaker logger = new LoggerMaker(SequenceCache.class, LogDb.THREAT_DETECTION);

    private static final int REFRESH_INTERVAL_SEC = 15 * 60;
    private static final long HISTORY_WINDOW_MILLIS = TimeUnit.MINUTES.toMillis(15);
    private static final int MAX_SESSIONS = 100_000;
    // Alert when this many of a session's last RECENT_STEPS steps are ones users almost never take
    private static final int UNKNOWN_STEPS_TO_ALERT = 5;
    private static final int RECENT_STEPS = 10;
    // Check whether unusual steps reuse values from the previous response (reads response bodies of sessions with unusual steps)
    private static final boolean DATA_REUSE_ENABLED = Boolean.parseBoolean(System.getenv("THREAT_DATA_REUSE_ENABLED"));

    private final DataActor dataActor;
    private final SessionIdentifier sessionIdentifier = new SessionIdentifier();

    // API key -> alternative sets of APIs, any one of which must have been called before it
    private volatile Map<String, List<List<String>>> requiredBefore = new HashMap<>();
    // APIs that appear as a requirement; only these are remembered per session
    private volatile Set<String> watched = new HashSet<>();
    private int lastUpdatedAt = 0;

    // Steps "A|B" users take (cyborg leaves out rare ones), and every API with a call count; steps between other APIs are not judged
    private volatile Set<String> knownSteps = new HashSet<>();
    // API key -> the same key; paths keep this shared instance instead of a copy per call
    private volatile Map<String, String> knownApis = new HashMap<>();
    private int stepsUpdatedAt = 0;

    // Session -> its recent calls and which of the steps between them were unknown
    private final Cache<String, RecentCalls> sessionPath = Caffeine.newBuilder()
            .maximumSize(MAX_SESSIONS)
            .expireAfterAccess(HISTORY_WINDOW_MILLIS, TimeUnit.MILLISECONDS)
            .build();

    private final Cache<String, Map<String, Long>> sessionSeen = Caffeine.newBuilder()
            .maximumSize(MAX_SESSIONS)
            .expireAfterAccess(HISTORY_WINDOW_MILLIS, TimeUnit.MILLISECONDS)
            .build();

    // One alert per session and API per window
    private final Cache<String, Boolean> flagged = Caffeine.newBuilder()
            .maximumSize(MAX_SESSIONS)
            .expireAfterWrite(HISTORY_WINDOW_MILLIS, TimeUnit.MILLISECONDS)
            .build();

    public SequenceCache(DataActor dataActor) {
        this.dataActor = dataActor;
    }

    // Reason text when this call is a sequence anomaly, otherwise null. url is the raw request URL; it is matched to the
    // catalog the same way the catalog does (exact URL, then template), falling back to fallbackUrl.
    public String evaluate(HttpResponseParams responseParam, int apiCollectionId, String url, Method method,
                           AccountConfig accountConfig, String fallbackUrl) {
        if (apiCollectionId == 0) return null;
        Map<String, Set<Method>> catalogUrls = accountConfig != null ? accountConfig.getApiInfoUrlToMethods() : null;
        List<URLTemplate> catalogTemplates = accountConfig != null && accountConfig.getApiCollectionUrlTemplates() != null
                ? accountConfig.getApiCollectionUrlTemplates().get(apiCollectionId) : null;
        String sequenceUrl = RuntimeUtil.findCatalogUrl(url, method, catalogUrl -> {
            Set<Method> methods = catalogUrls == null ? null : catalogUrls.get(apiCollectionId + ":" + catalogUrl);
            return methods != null && methods.contains(method);
        }, catalogTemplates);
        if (sequenceUrl == null) sequenceUrl = fallbackUrl;

        boolean skippedRequiredStep = checkSequenceAnomaly(responseParam, apiCollectionId, sequenceUrl, method);
        String unknownPath = checkUnknownPath(responseParam, apiCollectionId, sequenceUrl, method);
        if (unknownPath != null) return unknownPath;
        return skippedRequiredStep ? skippedStepReason(resolve(apiCollectionId, sequenceUrl, method)) : null;
    }

    // "<B> was called without <A> first", from the first rule for B
    private String skippedStepReason(String apiKey) {
        List<List<String>> contexts = apiKey == null ? null : requiredBefore.get(apiKey);
        if (contexts == null || contexts.isEmpty()) return "Called without the step users always take before it";
        List<String> required = new ArrayList<>();
        for (String api : contexts.get(0)) required.add(describe(api));
        return describe(apiKey) + " was called without " + String.join(" → ", required) + " first";
    }

    public boolean checkSequenceAnomaly(HttpResponseParams responseParam, int apiCollectionId, String url, Method method) {
        refresh();
        if (requiredBefore.isEmpty()) return false;
        String sessionId = sessionIdentifier.extractUserId(responseParam);
        if (sessionId == null) return false;
        String apiKey = resolve(apiCollectionId, url, method);
        if (apiKey == null) return false;

        long now = System.currentTimeMillis();
        Map<String, Long> seen = sessionSeen.get(sessionId, k -> new ConcurrentHashMap<>());
        boolean anomalous = false;
        List<List<String>> contexts = requiredBefore.get(apiKey);
        if (contexts != null && !anySatisfied(contexts, seen, now)) {
            anomalous = flagged.asMap().putIfAbsent(sessionId + " " + apiKey, Boolean.TRUE) == null;
        }
        if (watched.contains(apiKey)) seen.put(apiKey, now);
        return anomalous;
    }

    // Last RECENT_STEPS + 1 calls of a session (so every recent step has both ends) and whether each step was unknown
    private static final class RecentCalls {
        private final ArrayDeque<String> calls = new ArrayDeque<>(RECENT_STEPS + 1);
        private final ArrayDeque<Boolean> unknown = new ArrayDeque<>(RECENT_STEPS);
        // Per step: request parameters that reused the previous response's values, "" when none (ArrayDeque rejects null)
        private final ArrayDeque<String> reused = new ArrayDeque<>(RECENT_STEPS);
        private int unknownCount = 0;
        // Hashes of the last response's values; kept only while the window has an unknown step
        private long[] lastResponse;

        RecentCalls(String firstCall) {
            calls.addLast(firstCall);
        }

        void add(String call, boolean isUnknown, String reusedParams) {
            calls.addLast(call);
            if (calls.size() > RECENT_STEPS + 1) calls.removeFirst();
            unknown.addLast(isUnknown);
            reused.addLast(reusedParams);
            if (isUnknown) unknownCount++;
            if (unknown.size() > RECENT_STEPS) {
                reused.removeFirst();
                if (unknown.removeFirst()) unknownCount--;
            }
        }

        // "METHOD /a → METHOD /b (reused: id); ..." for each unknown step, oldest first
        String describeUnknownSteps() {
            List<String> callList = new ArrayList<>(calls);
            List<String> reusedList = new ArrayList<>(reused);
            int offset = callList.size() - 1 - unknown.size();
            StringBuilder text = new StringBuilder();
            int index = 0;
            for (Boolean isUnknown : unknown) {
                if (isUnknown) {
                    if (text.length() > 0) text.append("; ");
                    text.append(describe(callList.get(offset + index))).append(" → ").append(describe(callList.get(offset + index + 1)));
                    if (!reusedList.get(index).isEmpty()) text.append(" (reused: ").append(reusedList.get(index)).append(")");
                }
                index++;
            }
            return text.toString();
        }
    }

    // Reason text once UNKNOWN_STEPS_TO_ALERT of a session's last RECENT_STEPS steps are unknown, otherwise null.
    // Only an unknown step can raise it; the window starts over after an alert.
    public String checkUnknownPath(HttpResponseParams responseParam, int apiCollectionId, String url, Method method) {
        refreshKnownSteps();
        if (knownApis.isEmpty()) return null;
        String sessionId = sessionIdentifier.extractUserId(responseParam);
        if (sessionId == null) return null;
        String apiKey = knownApis.get(apiCollectionId + " " + normalizeUrl(url) + " " + method);
        if (apiKey == null) return null;

        RecentCalls recent = sessionPath.getIfPresent(sessionId);
        if (recent == null) {
            sessionPath.put(sessionId, new RecentCalls(apiKey));
            return null;
        }
        String previous = recent.calls.peekLast();
        if (previous.equals(apiKey)) return null;
        boolean isUnknown = !knownSteps.contains(previous + "|" + apiKey);
        List<String> reusedParams = DATA_REUSE_ENABLED && isUnknown ? DataReuse.findReuse(recent.lastResponse, responseParam) : null;
        recent.add(apiKey, isUnknown, reusedParams == null ? "" : String.join(", ", reusedParams));
        recent.lastResponse = DATA_REUSE_ENABLED && recent.unknownCount > 0 ? DataReuse.fingerprint(responseParam) : null;
        if (!isUnknown || recent.unknownCount < UNKNOWN_STEPS_TO_ALERT) return null;
        String reason = recent.unknownCount + " of the last " + recent.unknown.size() + " steps were ones users almost never take: " + recent.describeUnknownSteps();
        sessionPath.put(sessionId, new RecentCalls(apiKey));
        return reason;
    }

    // "METHOD /url" from a "<collectionId> <url> <method>" key
    private static String describe(String apiKey) {
        int first = apiKey.indexOf(' ');
        int last = apiKey.lastIndexOf(' ');
        return apiKey.substring(last + 1) + " " + apiKey.substring(first + 1, last);
    }

    private void refreshKnownSteps() {
        int now = (int) (System.currentTimeMillis() / 1000);
        if (stepsUpdatedAt != 0 && now - stepsUpdatedAt < REFRESH_INTERVAL_SEC) {
            return;
        }
        stepsUpdatedAt = now;
        try {
            Set<String> steps = new HashSet<>();
            Map<String, String> apis = new HashMap<>();
            for (ApiSequences seq : dataActor.fetchKnownSequenceSteps()) {
                String seqKey = seq.getSeqKey();
                if (seqKey == null) continue;
                if (seq.getOrder() == 0) {
                    String apiKey = normalizeKey(seqKey);
                    apis.put(apiKey, apiKey);
                } else if (seq.getOrder() == 1) {
                    int split = seqKey.indexOf('|');
                    if (split > 0) steps.add(normalizeKey(seqKey.substring(0, split)) + "|" + normalizeKey(seqKey.substring(split + 1)));
                }
            }
            knownSteps = steps;
            knownApis = apis;
            logger.infoAndAddToDb("SequenceCache known steps refreshed: " + steps.size() + " steps, " + apis.size() + " APIs");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error refreshing known steps: " + e.getMessage());
        }
    }

    private String resolve(int apiCollectionId, String url, Method method) {
        String key = apiCollectionId + " " + normalizeUrl(url) + " " + method;
        return requiredBefore.containsKey(key) || watched.contains(key) ? key : null;
    }

    // Query and trailing slash are dropped on both sides: the catalog keeps "/x/", cleanUrl turns it into "/x"
    static String normalizeUrl(String url) {
        if (url == null) return "";
        int query = url.indexOf('?');
        if (query >= 0) url = url.substring(0, query);
        while (url.length() > 1 && url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    // Api key format: "<collectionId> <url> <method>"
    static String normalizeKey(String apiKey) {
        int first = apiKey.indexOf(' ');
        int last = apiKey.lastIndexOf(' ');
        if (first <= 0 || last <= first) return apiKey;
        return apiKey.substring(0, first) + " " + normalizeUrl(apiKey.substring(first + 1, last)) + apiKey.substring(last);
    }

    private static boolean anySatisfied(List<List<String>> contexts, Map<String, Long> seen, long now) {
        for (List<String> context : contexts) {
            boolean all = true;
            for (String api : context) {
                Long at = seen.get(api);
                if (at == null || now - at > HISTORY_WINDOW_MILLIS) {
                    all = false;
                    break;
                }
            }
            if (all) return true;
        }
        return false;
    }

    public int size() {
        return requiredBefore.size();
    }

    public void refresh() {
        int now = (int) (System.currentTimeMillis() / 1000);
        if (lastUpdatedAt != 0 && now - lastUpdatedAt < REFRESH_INTERVAL_SEC) {
            return;
        }
        lastUpdatedAt = now;
        try {
            Map<String, List<List<String>>> rules = new HashMap<>();
            Set<String> required = new HashSet<>();
            for (ApiSequences seq : dataActor.fetchSequenceRules()) {
                List<String> paths = seq.getPaths();
                if (paths == null || paths.size() < 2) continue;
                String next = normalizeKey(paths.get(paths.size() - 1));
                List<String> context = new ArrayList<>();
                for (String api : paths.subList(0, paths.size() - 1)) context.add(normalizeKey(api));
                rules.computeIfAbsent(next, k -> new ArrayList<>()).add(context);
                required.addAll(context);
            }
            requiredBefore = rules;
            watched = required;
            logger.infoAndAddToDb("SequenceCache refreshed: " + rules.size() + " APIs with required predecessors");
        } catch (Exception e) {
            logger.errorAndAddToDb(e, "Error refreshing SequenceCache: " + e.getMessage());
        }
    }
}
