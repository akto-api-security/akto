package com.akto.behaviour_modelling.model;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Mutable per-user state tracked within a session window.
 * Holds the last (sequenceLength - 1) API IDs seen for this user,
 * used to emit transition keys on each new API call.
 */
public class UserSessionState {

    private final Deque<Integer> recentApis;
    private long sessionStart;

    public UserSessionState(long sessionStart) {
        this.recentApis = new ArrayDeque<>();
        this.sessionStart = sessionStart;
    }

    public Deque<Integer> getRecentApis() {
        return recentApis;
    }

    public long getSessionStart() {
        return sessionStart;
    }

    /**
     * Called when a new window starts. Clears transition context so no
     * cross-window transitions are emitted.
     */
    public void reset(long newSessionStart) {
        recentApis.clear();
        this.sessionStart = newSessionStart;
    }
}
