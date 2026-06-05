package com.akto.threat.detection.scripts;

import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.threat.detection.scripts.TrafficCatalog.ApiProfile;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * E2E traffic simulator for verifying distribution and API hit count graphs.
 *
 * Uses explicit SimTier configs — each tier targets a specific distribution bucket range
 * by controlling callsPerMin and activeMinutes. Higher-bucket tiers are only active for
 * a short window at the end of the simulation so their per-window accumulation lands in
 * the right bucket without blowing the total message budget.
 *
 * Target: ~1M messages, covers b1–b12, 5–50 IPs per bucket.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="com.akto.threat.detection.scripts.EndToEndTrafficSimulator" \
 *     -Dsim.duration.mins=30 -Dsim.total.messages=1000000 \
 *     -Dkafka.url=localhost:9092 -Dmode=threat
 *
 *   Filter to one API: -Dsim.api=/api/v1/feed
 *   Modes: threat (default), mini-runtime, both
 */
public class EndToEndTrafficSimulator {

    private static final int    SIM_DURATION_MINS = RealisticTrafficBenchmark.readIntConfig("sim.duration.mins", "SIM_DURATION_MINS", 30);
    private static final int    NUM_THREADS       = RealisticTrafficBenchmark.readIntConfig("sim.threads",        "SIM_THREADS",       Runtime.getRuntime().availableProcessors());
    private static final String KAFKA_URL          = RealisticTrafficBenchmark.readStringConfig("kafka.url",        "KAFKA_URL",           "localhost:9092");
    private static final String MODE               = RealisticTrafficBenchmark.readStringConfig("mode",             "MODE",                "threat");
    // Optional: filter to a single API path, e.g. -Dsim.api=/api/v1/feed
    // If not set, all APIs in the catalog are simulated.
    private static final String SIM_API            = System.getProperty("sim.api", "/api/v1/cart");

    // -------------------------------------------------------------------------
    // SimTier: explicit bucket-targeting tier config
    //
    // callsPerMin  — calls per IP per active minute
    // ipCount      — number of unique IPs in this tier
    // startMin     — minute offset (within sim) at which this tier becomes active
    // activeMinutes — how many consecutive minutes this tier fires
    // subnet       — IP prefix for this tier (e.g. "192.168.1.")
    //
    // Window total = callsPerMin × min(activeMinutes, windowSize)
    // With windowSize=5, to land in bucket bN, window total must fall in bN's range.
    //
    // b1–b5: active full 30 min → smooth baseline across the whole graph
    // b6–b12: short bursts spread evenly so spikes appear across the timeline, not all at the end
    //
    // Budget math (30-min sim, window=5):
    //   b1  (1-10):       1/min ×  50 IPs × 30 min =     1,500
    //   b2  (11-50):      5/min ×  30 IPs × 30 min =     4,500
    //   b3  (51-250):    20/min ×  20 IPs × 30 min =    12,000
    //   b4  (251-1000): 100/min ×  15 IPs × 30 min =    45,000
    //   b5  (1001-2500):300/min ×  10 IPs × 30 min =    90,000
    //   b6  (2501-5000):700/min ×   8 IPs ×  5 min =    28,000  starts min 0
    //   b7  (5001-10k):1400/min ×   6 IPs ×  5 min =    42,000  starts min 5
    //   b8  (10k-25k): 3000/min ×   5 IPs ×  2 min =    30,000  starts min 10
    //   b9  (25k-50k): 6000/min ×   5 IPs ×  1 min =    30,000  starts min 14
    //   b10 (50k-100k):12000/min×   5 IPs ×  1 min =    60,000  starts min 18
    //   b11 (100k-250k):25000/min×  5 IPs ×  1 min =   125,000  starts min 22
    //   b12 (250k-500k):60000/min×  5 IPs ×  1 min =   300,000  starts min 26
    //   Total ≈ 768,000 < 1M ✓
    // -------------------------------------------------------------------------

    private static class SimTier {
        final String label;
        final int    callsPerMin;
        final int    ipCount;
        final int    startMin;      // minute offset when this tier becomes active
        final int    activeMinutes; // how many consecutive minutes it fires
        final String subnet;
        final String targetBucket;

        SimTier(String label, int callsPerMin, int ipCount, int startMin, int activeMinutes,
                String subnet, String targetBucket) {
            this.label         = label;
            this.callsPerMin   = callsPerMin;
            this.ipCount       = ipCount;
            this.startMin      = startMin;
            this.activeMinutes = activeMinutes;
            this.subnet        = subnet;
            this.targetBucket  = targetBucket;
        }

        boolean isActiveAt(int min) {
            return min >= startMin && min < startMin + activeMinutes;
        }

        long estimatedMessages() {
            return (long) callsPerMin * ipCount * activeMinutes;
        }
    }

    // label, callsPerMin, ipCount, startMin, activeMinutes, subnet, targetBucket
    private static final SimTier[] SIM_TIERS = {
        new SimTier("b1  (1-10)",          1,  50,  0, 30, "192.168.1.",  "b1"),
        new SimTier("b2  (11-50)",          5,  30,  0, 30, "192.168.2.",  "b2"),
        new SimTier("b3  (51-250)",        20,  20,  0, 30, "192.168.3.",  "b3"),
        new SimTier("b4  (251-1000)",     100,  15,  0, 30, "192.168.4.",  "b4"),
        new SimTier("b5  (1001-2500)",    300,  10,  0, 30, "192.168.5.",  "b5"),
        new SimTier("b6  (2501-5000)",    700,   8,  0,  5, "192.168.6.",  "b6"),
        new SimTier("b7  (5001-10k)",    1400,   6,  5,  5, "192.168.7.",  "b7"),
        new SimTier("b8  (10k-25k)",     3000,   5, 10,  2, "192.168.8.",  "b8"),
        new SimTier("b9  (25k-50k)",     6000,   5, 14,  1, "192.168.9.",  "b9"),
        new SimTier("b10 (50k-100k)",   12000,   5, 18,  1, "192.168.10.", "b10"),
        new SimTier("b11 (100k-250k)",  25000,   5, 22,  1, "192.168.11.", "b11"),
        new SimTier("b12 (250k-500k)",  60000,   5, 26,  1, "192.168.12.", "b12"),
    };

    public static void main(String[] args) throws Exception {
        if (!MODE.equals("threat") && !MODE.equals("mini-runtime") && !MODE.equals("both")) {
            System.err.println("Invalid mode: " + MODE + ". Valid modes: threat, mini-runtime, both");
            System.exit(1);
        }

        // Resolve target API path + method
        List<ApiProfile> fullCatalog = TrafficCatalog.buildCatalog();
        List<ApiProfile> catalog = new ArrayList<>();
        for (ApiProfile p : fullCatalog) {
            if (SIM_API.isEmpty() || SIM_API.equals(p.path)) {
                catalog.add(p);
            }
        }
        if (catalog.isEmpty()) {
            System.err.println("No API matched sim.api=" + SIM_API
                    + ". Available: " + fullCatalog.stream()
                        .map(p -> p.path).reduce((a, b) -> a + ", " + b).orElse("none"));
            System.exit(1);
        }
        // When multiple APIs match, round-robin assign SimTier IPs across APIs
        final int apiCount = catalog.size();

        KafkaProtoProducer protoProducer = buildProtoProducer();
        if (MODE.equals("mini-runtime") || MODE.equals("both")) {
            RealisticTrafficBenchmark.initJsonProducer(KAFKA_URL);
        }

        long baseEpochMin = System.currentTimeMillis() / 60000 - SIM_DURATION_MINS;
        AtomicLong totalSent   = new AtomicLong(0);
        AtomicLong totalErrors = new AtomicLong(0);
        AtomicBoolean stopReporter = new AtomicBoolean(false);

        long estimatedTotal = 0;
        for (SimTier t : SIM_TIERS) estimatedTotal += t.estimatedMessages() * apiCount;

        System.out.printf("\n====== E2E Traffic Simulator ======\n");
        System.out.printf("Mode: %s | Duration: %d min | Est. messages: %,d | Threads: %d\n",
                MODE.toUpperCase(), SIM_DURATION_MINS, estimatedTotal, NUM_THREADS);
        System.out.printf("APIs: %d%s | Kafka: %s\n\n",
                apiCount, SIM_API.isEmpty() ? "" : " (filtered: " + SIM_API + ")", KAFKA_URL);
        printTierSummary(estimatedTotal);

        long startTime = System.nanoTime();

        // Progress reporter
        final long finalEstimate = estimatedTotal;
        Thread reporter = new Thread(() -> {
            while (!stopReporter.get()) {
                try {
                    long sent = totalSent.get();
                    long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                    double rate = sent * 1000.0 / Math.max(elapsedMs, 1);
                    long eta = rate > 0 ? (long)((finalEstimate - sent) / rate * 1000) : 0;
                    System.out.printf("\r[%02d:%02d] sent=%,d (%.0f msg/s) errors=%,d ETA=%02d:%02d",
                            elapsedMs / 60000, (elapsedMs % 60000) / 1000,
                            sent, rate, totalErrors.get(),
                            eta / 60000, (eta % 60000) / 1000);
                    System.out.flush();
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        reporter.setDaemon(true);
        reporter.start();

        // Split minute range across threads
        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        List<Future<?>> futures = new ArrayList<>();
        int minsPerThread = Math.max(1, SIM_DURATION_MINS / NUM_THREADS);

        for (int t = 0; t < NUM_THREADS; t++) {
            final int threadIdx = t;
            final int minStart  = t * minsPerThread;
            final int minEnd    = (t == NUM_THREADS - 1) ? SIM_DURATION_MINS : (t + 1) * minsPerThread;

            futures.add(executor.submit(() -> {
                Random rng = new Random(42 + threadIdx);

                for (int min = minStart; min < minEnd; min++) {
                    long epochMinute = baseEpochMin + min;

                    for (SimTier tier : SIM_TIERS) {
                        if (!tier.isActiveAt(min)) continue;

                        for (int ipIdx = 0; ipIdx < tier.ipCount; ipIdx++) {
                            String ip = tier.subnet + (ipIdx + 1);
                            // Round-robin IPs across APIs so each API gets its own IP set
                            ApiProfile profile = catalog.get(ipIdx % apiCount);

                            for (int c = 0; c < tier.callsPerMin; c++) {
                                try {
                                    HttpResponseParam msg = TrafficCatalog.buildMessage(
                                            profile.path, profile.method, ip, epochMinute, rng);

                                    if (MODE.equals("threat") || MODE.equals("both")) {
                                        protoProducer.send(RealisticTrafficBenchmark.THREAT_TOPIC, msg);
                                    }
                                    if (MODE.equals("mini-runtime") || MODE.equals("both")) {
                                        RealisticTrafficBenchmark.IngestDataBatch batch =
                                                RealisticTrafficBenchmark.convertProtoToJson(msg);
                                        String json = RealisticTrafficBenchmark.ingestDataBatchToJson(batch);
                                        RealisticTrafficBenchmark.jsonProducer.send(
                                                new ProducerRecord<>(RealisticTrafficBenchmark.INGEST_TOPIC, json));
                                    }

                                    totalSent.incrementAndGet();
                                } catch (Exception e) {
                                    totalErrors.incrementAndGet();
                                }
                            }
                        }
                    }
                }
            }));
        }

        executor.shutdown();
        executor.awaitTermination(60, TimeUnit.MINUTES);
        stopReporter.set(true);

        System.out.println("\n\nFlushing...");
        if (MODE.equals("threat") || MODE.equals("both")) {
            protoProducer.flush();
        }
        if (MODE.equals("mini-runtime") || MODE.equals("both")) {
            RealisticTrafficBenchmark.jsonProducer.flush();
            RealisticTrafficBenchmark.jsonProducer.close();
        }

        long totalMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.printf("\n====== Done ======\n");
        System.out.printf("Sent: %,d | Errors: %,d | Time: %.1fs | Throughput: %.0f msg/s\n",
                totalSent.get(), totalErrors.get(), totalMs / 1000.0,
                totalSent.get() * 1000.0 / Math.max(totalMs, 1));
        System.out.printf("Simulated epoch minutes: [%d .. %d]\n",
                baseEpochMin, baseEpochMin + SIM_DURATION_MINS - 1);
        System.out.println("Check distribution graph for collection " + TrafficCatalog.COLLECTION_ID);
    }

    private static void printTierSummary(long estimatedTotal) {
        System.out.printf("%-20s %-6s %10s %6s %9s %12s %12s %14s\n",
                "Tier", "bucket", "calls/min", "IPs", "startMin", "activeMins", "est.msgs", "windowTotal");
        System.out.println("--------------------------------------------------------------------------------------------");
        for (SimTier t : SIM_TIERS) {
            long est = t.estimatedMessages();
            int windowTotal = t.callsPerMin * Math.min(t.activeMinutes, 5);
            System.out.printf("%-20s %-6s %10d %6d %9d %12d %,12d %14d\n",
                    t.label, t.targetBucket, t.callsPerMin, t.ipCount, t.startMin, t.activeMinutes, est, windowTotal);
        }
        System.out.printf("%-20s %-6s %10s %6s %9s %12s %,12d\n",
                "TOTAL", "", "", "", "", "", estimatedTotal);
        System.out.println();
    }

    private static KafkaProtoProducer buildProtoProducer() {
        KafkaConfig config = KafkaConfig.newBuilder()
                .setGroupId("e2e-simulator")
                .setBootstrapServers(KAFKA_URL)
                .setConsumerConfig(KafkaConsumerConfig.newBuilder()
                        .setMaxPollRecords(500)
                        .setPollDurationMilli(100)
                        .build())
                .setProducerConfig(KafkaProducerConfig.newBuilder()
                        .setBatchSize(65536)
                        .setLingerMs(50)
                        .build())
                .setKeySerializer(Serializer.STRING)
                .setValueSerializer(Serializer.BYTE_ARRAY)
                .build();
        return new KafkaProtoProducer(config);
    }
}
