package com.akto.testing.kafka_utils;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * WebSocket tests of one collection (one server) run one at a time by default, as the legacy path runs them,
 * so a broadcast server's frames for one test's connection never land in another test's reply. HTTP tests
 * are not limited. AKTO_WS_PARALLEL_TESTS_PER_COLLECTION raises the limit for servers that keep each
 * connection private.
 */
final class WebSocketTestSlots {

    static final int TESTS_PER_COLLECTION = testsPerCollection(System.getenv("AKTO_WS_PARALLEL_TESTS_PER_COLLECTION"));

    private static final Map<Integer, Semaphore> slots = new ConcurrentHashMap<>();

    private WebSocketTestSlots() {
    }

    static int testsPerCollection(String value) {
        try {
            int tests = Integer.parseInt(value.trim());
            return tests > 0 ? tests : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    /** Runs the test; a WebSocket one first waits for a free slot of its collection (the wait counts toward the test's timeout). */
    static <T> T run(boolean webSocket, int apiCollectionId, Callable<T> test) throws Exception {
        if (!webSocket) {
            return test.call();
        }
        Semaphore collectionSlots = slots.computeIfAbsent(apiCollectionId, k -> new Semaphore(TESTS_PER_COLLECTION, true));
        collectionSlots.acquire();
        try {
            return test.call();
        } finally {
            collectionSlots.release();
        }
    }
}
