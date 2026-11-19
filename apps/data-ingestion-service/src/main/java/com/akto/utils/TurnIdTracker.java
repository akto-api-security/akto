package com.akto.utils;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class TurnIdTracker {

    private static final int MAX_SESSIONS = 50_000;
    private static final String ID_PREFIX = "turn_";

    private static final Map<String, String> LATEST_TURN_BY_SESSION =
            new LinkedHashMap<String, String>(1024, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_SESSIONS;
                }
            };

    private TurnIdTracker() {}

    public static String record(String sessionId, String turnKey) {
        String id = ID_PREFIX + UUID.nameUUIDFromBytes(turnKey.getBytes(StandardCharsets.UTF_8));
        synchronized (LATEST_TURN_BY_SESSION) {
            LATEST_TURN_BY_SESSION.put(sessionId, id);
        }
        return id;
    }

    public static String latest(String sessionId) {
        synchronized (LATEST_TURN_BY_SESSION) {
            return LATEST_TURN_BY_SESSION.get(sessionId);
        }
    }
}
