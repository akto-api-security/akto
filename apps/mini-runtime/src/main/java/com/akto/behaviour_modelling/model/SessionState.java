package com.akto.behaviour_modelling.model;

/**
 * Last few endpoint ids of one session, as a ring buffer. Callers synchronize on the instance.
 */
public final class SessionState {

    private final int[] history;
    private int head;
    private int size;
    private volatile int lastSeen;

    public SessionState(int maxHistory) {
        this.history = new int[maxHistory];
    }

    public int size() {
        return size;
    }

    /** k = 1 is the most recent endpoint. */
    public int recent(int k) {
        int idx = head - k;
        if (idx < 0) idx += history.length;
        return history[idx];
    }

    public void add(int endpointId) {
        history[head] = endpointId;
        head = (head + 1) % history.length;
        if (size < history.length) size++;
    }

    public void clear() {
        head = 0;
        size = 0;
    }

    public int getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(int lastSeen) {
        this.lastSeen = lastSeen;
    }
}
