package com.akto.testing.kafka_utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class WebSocketTestSlotsTest {

    // how many tests ran at the same time, per group
    private static int maxRunningAtOnce(boolean webSocket, int[] collectionIds) throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(collectionIds.length);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int collectionId : collectionIds) {
                results.add(pool.submit(() -> WebSocketTestSlots.run(webSocket, collectionId, () -> {
                    maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                    Thread.sleep(100);
                    running.decrementAndGet();
                    return collectionId;
                })));
            }
            for (int i = 0; i < collectionIds.length; i++) {
                assertEquals(collectionIds[i], (int) results.get(i).get());
            }
        } finally {
            pool.shutdownNow();
        }
        return maxRunning.get();
    }

    @Test
    public void webSocketTestsOfOneCollectionRunOneAtATime() throws Exception {
        assertEquals(WebSocketTestSlots.TESTS_PER_COLLECTION,
                maxRunningAtOnce(true, new int[]{101, 101, 101, 101, 101, 101}));
    }

    @Test
    public void otherCollectionsAndHttpTestsAreNotHeldBack() throws Exception {
        assertTrue(maxRunningAtOnce(true, new int[]{201, 202, 203, 204}) > 1);
        assertTrue(maxRunningAtOnce(false, new int[]{301, 301, 301, 301}) > 1);
    }

    @Test
    public void limitFallsBackToOneOnBadValues() {
        assertEquals(1, WebSocketTestSlots.testsPerCollection(null));
        assertEquals(1, WebSocketTestSlots.testsPerCollection("abc"));
        assertEquals(1, WebSocketTestSlots.testsPerCollection("0"));
        assertEquals(3, WebSocketTestSlots.testsPerCollection(" 3 "));
    }
}
