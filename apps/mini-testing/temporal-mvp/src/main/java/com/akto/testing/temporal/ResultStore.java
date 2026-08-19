package com.akto.testing.temporal;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Idempotent, crash-durable result store (file-based; stands in for cyborg/Mongo).
 * One record per (test run, API, test). In production this becomes an idempotent
 * bulk upsert via ClientActor keyed by (summaryId, apiInfoKey, subcategory).
 */
public final class ResultStore {
    private static final Path ROOT = Paths.get(System.getenv().getOrDefault("MVP_DATA_DIR", "data"));
    private static final Object LOCK = new Object();

    private ResultStore() {}

    /** Identifies one test on one API within a run. */
    public static String resultKey(String testRunId, int apiId, int testId) {
        return testRunId + ":" + apiId + ":" + testId;
    }

    public static void recordAttempt(String testRunId, String key) {
        append(dir(testRunId).resolve("attempts.log"), key + "\n");
    }

    public static void saveOutcome(String testRunId, String key, String outcome) {
        append(dir(testRunId).resolve("results.jsonl"), key + "\t" + outcome + "\n");
    }

    /** Resume-skip source: results already durably saved for this run. */
    public static Map<String, String> loadSavedOutcomes(String testRunId) {
        Path f = dir(testRunId).resolve("results.jsonl");
        Map<String, String> m = new HashMap<>();
        if (Files.exists(f)) {
            try {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    if (line.isEmpty()) continue;
                    int tab = line.indexOf('\t');
                    m.put(line.substring(0, tab), line.substring(tab + 1)); // last write wins → idempotent
                }
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }
        return m;
    }

    public static int countAttempts(String testRunId) {
        Path f = dir(testRunId).resolve("attempts.log");
        if (!Files.exists(f)) return 0;
        try { return (int) Files.readAllLines(f).stream().filter(s -> !s.isEmpty()).count(); }
        catch (IOException e) { throw new UncheckedIOException(e); }
    }

    /** Authoritative accounting: distinct tests and per-outcome tally, deduped by key. */
    public static TestOutcomes finalOutcomes(String testRunId) {
        TestOutcomes o = new TestOutcomes();
        for (String v : loadSavedOutcomes(testRunId).values()) o.record(v);
        return o;
    }

    public static int distinctTests(String testRunId) { return loadSavedOutcomes(testRunId).size(); }

    private static Path dir(String testRunId) {
        Path d = ROOT.resolve(testRunId);
        try { Files.createDirectories(d); } catch (IOException e) { throw new UncheckedIOException(e); }
        return d;
    }

    private static void append(Path p, String s) {
        synchronized (LOCK) {
            try { Files.write(p, s.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND); }
            catch (IOException e) { throw new UncheckedIOException(e); }
        }
    }
}
