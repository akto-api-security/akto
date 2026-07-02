package com.akto.threat.backend.utils;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs independent Mongo queries in parallel and collects results.
 * Reusable by any service that has multiple independent queries per request.
 *
 * Usage:
 *   List<Future<Set<String>>> futures = ParallelQueryExecutor.submit(executor, tasks);
 *   List<Set<String>> results = ParallelQueryExecutor.collect(futures, 10_000);
 */
public class ParallelQueryExecutor {

    private static final LoggerMaker logger = new LoggerMaker(ParallelQueryExecutor.class, LogDb.THREAT_DETECTION);

    // Shared pool sized to typical number of parallel queries per request.
    // Using a cached pool so it scales up under burst and releases threads when idle.
    private static final ExecutorService POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "parallel-mongo-query");
        t.setDaemon(true);
        return t;
    });

    /**
     * Submit a list of independent tasks to the shared pool.
     * Returns futures in the same order as the input tasks.
     */
    public static <T> List<Future<T>> submit(List<Callable<T>> tasks) {
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        for (Callable<T> task : tasks) {
            futures.add(POOL.submit(task));
        }
        return futures;
    }

    /**
     * Collect results from futures with a per-future timeout.
     * On timeout or error for any task, logs the error and returns null for that slot.
     * Callers should handle null results defensively.
     */
    public static <T> List<T> collect(List<Future<T>> futures, long timeoutMs) {
        List<T> results = new ArrayList<>(futures.size());
        for (Future<T> future : futures) {
            try {
                results.add(future.get(timeoutMs, TimeUnit.MILLISECONDS));
            } catch (Exception e) {
                logger.error("Parallel query task failed or timed out: " + e.getMessage(), e);
                future.cancel(true);
                results.add(null);
            }
        }
        return results;
    }

    /**
     * Submit named tasks in parallel and collect results into a Map keyed by the same names.
     * Failed/timed-out tasks return an empty collection for their key (never null).
     * Preserves insertion order of the input map.
     */
    public static <T> Map<String, T> submitAndCollect(Map<String, Callable<T>> namedTasks, long timeoutMs) {
        List<String> keys = new ArrayList<>(namedTasks.keySet());
        List<Callable<T>> tasks = new ArrayList<>(namedTasks.values());

        List<Future<T>> futures = submit(tasks);
        List<T> results = collect(futures, timeoutMs);

        Map<String, T> resultMap = new LinkedHashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            resultMap.put(keys.get(i), results.get(i));
        }
        return Collections.unmodifiableMap(resultMap);
    }
}
