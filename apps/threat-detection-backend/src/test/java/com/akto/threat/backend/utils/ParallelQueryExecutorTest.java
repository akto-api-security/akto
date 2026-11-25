package com.akto.threat.backend.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

// The fetch_filters caller reads each key with getOrDefault(key, empty), which only works if a failed key is absent.
public class ParallelQueryExecutorTest {

    private static final Set<String> EMPTY = Collections.emptySet();

    @Test
    public void timedOutTaskIsLeftOutSoCallerGetsItsDefault() {
        Map<String, Callable<Set<String>>> tasks = new LinkedHashMap<>();
        tasks.put("fast", () -> Collections.singleton("a"));
        tasks.put("slow", () -> { Thread.sleep(3000); return Collections.singleton("b"); });

        Map<String, Set<String>> results = ParallelQueryExecutor.submitAndCollect(tasks, 300);

        assertEquals(Collections.singleton("a"), results.getOrDefault("fast", EMPTY));
        assertFalse(results.containsKey("slow"));
        assertEquals(EMPTY, results.getOrDefault("slow", EMPTY));
    }

    @Test
    public void failedTaskIsLeftOutToo() {
        Map<String, Callable<Set<String>>> tasks = new LinkedHashMap<>();
        tasks.put("ok", () -> Collections.singleton("a"));
        tasks.put("broken", () -> { throw new IllegalStateException("mongo down"); });

        Map<String, Set<String>> results = ParallelQueryExecutor.submitAndCollect(tasks, 5000);

        assertEquals(Collections.singleton("a"), results.getOrDefault("ok", EMPTY));
        assertEquals(EMPTY, results.getOrDefault("broken", EMPTY));
    }

    @Test
    public void timedOutTaskIsCancelledSoItDoesNotWaitForever() throws Exception {
        Map<String, Callable<Set<String>>> tasks = new LinkedHashMap<>();
        CountDownLatch interrupted = new CountDownLatch(1);
        tasks.put("slow", () -> {
            try { Thread.sleep(60_000); } catch (InterruptedException e) { interrupted.countDown(); }
            return Collections.singleton("b");
        });

        Map<String, Set<String>> results = ParallelQueryExecutor.submitAndCollect(tasks, 100);

        assertFalse(results.containsKey("slow"));
        assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    }
}
