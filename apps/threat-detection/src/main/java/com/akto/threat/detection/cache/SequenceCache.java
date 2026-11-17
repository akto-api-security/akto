package com.akto.threat.detection.cache;

import com.akto.data_actor.DataActor;
import com.akto.dto.ApiSequences;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.URLMethods.Method;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.threat.detection.utils.SessionIdentifier;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

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
    private static final int UNKNOWN_STEPS_TO_ALERT = 5;

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

    // Session -> its last API, then the APIs of its current run of unknown steps (at most UNKNOWN_STEPS_TO_ALERT + 1)
    private final Cache<String, List<String>> sessionPath = Caffeine.newBuilder()
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

    // Reason text once a session has taken UNKNOWN_STEPS_TO_ALERT unknown steps in a row, otherwise null.
    // A known step resets the run, so a session's path is just its last API whenever it behaves normally.
    public String checkUnknownPath(HttpResponseParams responseParam, int apiCollectionId, String url, Method method) {
        refreshKnownSteps();
        if (knownApis.isEmpty()) return null;
        String sessionId = sessionIdentifier.extractUserId(responseParam);
        if (sessionId == null) return null;
        String apiKey = knownApis.get(apiCollectionId + " " + normalizeUrl(url) + " " + method);
        if (apiKey == null) return null;

        List<String> path = sessionPath.getIfPresent(sessionId);
        if (path == null) {
            sessionPath.put(sessionId, newPath(apiKey));
            return null;
        }
        String previous = path.get(path.size() - 1);
        if (previous.equals(apiKey)) return null;
        if (knownSteps.contains(previous + "|" + apiKey)) {
            sessionPath.put(sessionId, newPath(apiKey));
            return null;
        }
        path.add(apiKey);
        if (path.size() <= UNKNOWN_STEPS_TO_ALERT) return null;
        sessionPath.put(sessionId, newPath(apiKey));
        return UNKNOWN_STEPS_TO_ALERT + " steps in a row that users almost never take: " + describe(path);
    }

    private static List<String> newPath(String apiKey) {
        List<String> path = new ArrayList<>(UNKNOWN_STEPS_TO_ALERT + 1);
        path.add(apiKey);
        return path;
    }

    // "METHOD /url → METHOD /url ..." from "<collectionId> <url> <method>" keys
    private static String describe(List<String> path) {
        StringBuilder text = new StringBuilder();
        for (String apiKey : path) {
            int first = apiKey.indexOf(' ');
            int last = apiKey.lastIndexOf(' ');
            if (text.length() > 0) text.append(" → ");
            text.append(apiKey.substring(last + 1)).append(' ').append(apiKey.substring(first + 1, last));
        }
        return text.toString();
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
