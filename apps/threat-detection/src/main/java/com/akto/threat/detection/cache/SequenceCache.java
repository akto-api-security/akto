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
    private volatile Set<String> knownApis = new HashSet<>();
    private int stepsUpdatedAt = 0;

    private final Cache<String, String> lastApi = Caffeine.newBuilder()
            .maximumSize(MAX_SESSIONS)
            .expireAfterAccess(HISTORY_WINDOW_MILLIS, TimeUnit.MILLISECONDS)
            .build();
    private final Cache<String, Integer> unknownRun = Caffeine.newBuilder()
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

    // True once a session has taken UNKNOWN_STEPS_TO_ALERT unknown steps in a row; a known step resets the run
    public boolean checkUnknownPath(HttpResponseParams responseParam, int apiCollectionId, String url, Method method) {
        refreshKnownSteps();
        if (knownApis.isEmpty()) return false;
        String sessionId = sessionIdentifier.extractUserId(responseParam);
        if (sessionId == null) return false;
        String apiKey = apiCollectionId + " " + normalizeUrl(url) + " " + method;
        if (!knownApis.contains(apiKey)) return false;

        String previous = lastApi.getIfPresent(sessionId);
        lastApi.put(sessionId, apiKey);
        if (previous == null || previous.equals(apiKey)) return false;

        if (knownSteps.contains(previous + "|" + apiKey)) {
            unknownRun.invalidate(sessionId);
            return false;
        }
        int run = unknownRun.asMap().merge(sessionId, 1, Integer::sum);
        if (run < UNKNOWN_STEPS_TO_ALERT) return false;
        unknownRun.invalidate(sessionId);
        return true;
    }

    private void refreshKnownSteps() {
        int now = (int) (System.currentTimeMillis() / 1000);
        if (stepsUpdatedAt != 0 && now - stepsUpdatedAt < REFRESH_INTERVAL_SEC) {
            return;
        }
        stepsUpdatedAt = now;
        try {
            Set<String> steps = new HashSet<>();
            Set<String> apis = new HashSet<>();
            for (ApiSequences seq : dataActor.fetchKnownSequenceSteps()) {
                String seqKey = seq.getSeqKey();
                if (seqKey == null) continue;
                if (seq.getOrder() == 0) {
                    apis.add(normalizeKey(seqKey));
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
