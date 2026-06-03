package com.akto.threat.detection.scripts;

import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.threat.detection.scripts.TrafficCatalog.ApiProfile;
import com.akto.threat.detection.scripts.TrafficCatalog.IpEntry;
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
 * Pushes realistic Kafka traffic for collection 1000 with 11 APIs (HIGH/MEDIUM/LOW usage).
 * Each API has a Zipf-split IP pool (CASUAL/REGULAR/POWER/ANOMALOUS tiers) so distribution
 * buckets b1-b14 populate with a realistic long-tail shape.
 *
 * Total messages are capped by sim.total.messages; the simulator derives a per-minute budget
 * and applies a scale factor to tier call counts so the cap is respected.
 *
 * Supports both threat-detection (proto) and mini-runtime (JSON) Kafka topics.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="com.akto.threat.detection.scripts.EndToEndTrafficSimulator" \
 *     -Dsim.duration.mins=1440 -Dsim.total.messages=1000000 -Dsim.threads=8 \
 *     -Dkafka.url=localhost:9092 -Dmode=mini-runtime
 *
 *   Modes: threat (default), mini-runtime, both
 */
public class EndToEndTrafficSimulator {

    private static final int    SIM_DURATION_MINS  = RealisticTrafficBenchmark.readIntConfig("sim.duration.mins",    "SIM_DURATION_MINS",   1440);
    private static final long   TOTAL_MESSAGES      = RealisticTrafficBenchmark.readLongConfig("sim.total.messages", "SIM_TOTAL_MESSAGES",  1_000_000L);
    private static final int    NUM_THREADS         = RealisticTrafficBenchmark.readIntConfig("sim.threads",         "SIM_THREADS",         Runtime.getRuntime().availableProcessors());
    private static final String KAFKA_URL           = RealisticTrafficBenchmark.readStringConfig("kafka.url",        "KAFKA_URL",           "localhost:9092");
    private static final String MODE                = RealisticTrafficBenchmark.readStringConfig("mode",             "MODE",                "threat");

    // Natural catalog throughput per minute (computed from tier averages and pool sizes):
    //   HIGH  (200 IPs): casual(120)×4.5 + regular(50)×25 + power(24)×290 + anomalous(6)×5000 ≈ 38,750/min
    //   MEDIUM(100 IPs): ≈ 19,375/min   LOW(40 IPs): ≈ 7,750/min
    //   Total: 3×38750 + 4×19375 + 4×7750 ≈ 225,000/min
    private static final double NATURAL_MSG_PER_MIN = 225_000.0;

    public static void main(String[] args) throws Exception {
        if (!MODE.equals("threat") && !MODE.equals("mini-runtime") && !MODE.equals("both")) {
            System.err.println("Invalid mode: " + MODE + ". Valid modes: threat, mini-runtime, both");
            System.exit(1);
        }

        List<ApiProfile> catalog = TrafficCatalog.buildCatalog();

        // Derive scale factor so total messages ≈ TOTAL_MESSAGES
        double scale = TOTAL_MESSAGES / (NATURAL_MSG_PER_MIN * SIM_DURATION_MINS);
        // Floor at a value that lets ANOMALOUS still fire at least 1 call/min
        scale = Math.max(scale, 1.0 / 8000.0);

        KafkaProtoProducer protoProducer = buildProtoProducer();
        if (MODE.equals("mini-runtime") || MODE.equals("both")) {
            RealisticTrafficBenchmark.initJsonProducer(KAFKA_URL);
        }

        long baseEpochMin = System.currentTimeMillis() / 60000 - SIM_DURATION_MINS;

        AtomicLong totalSent   = new AtomicLong(0);
        AtomicLong totalErrors = new AtomicLong(0);
        AtomicBoolean stopReporter = new AtomicBoolean(false);

        System.out.printf("\n====== E2E Traffic Simulator ======\n");
        System.out.printf("Mode: %s | Duration: %d min (~%.1f hrs) | Target: %,d messages\n",
                MODE.toUpperCase(), SIM_DURATION_MINS, SIM_DURATION_MINS / 60.0, TOTAL_MESSAGES);
        System.out.printf("Scale: %.5f | Est. msg/min: %.0f | Threads: %d\n",
                scale, NATURAL_MSG_PER_MIN * scale, NUM_THREADS);
        System.out.printf("Collection: %d | APIs: %d | Kafka: %s\n\n",
                TrafficCatalog.COLLECTION_ID, catalog.size(), KAFKA_URL);
        printCatalogSummary(catalog);

        long startTime = System.nanoTime();

        // Progress reporter
        final double finalScale = scale;
        Thread reporter = new Thread(() -> {
            while (!stopReporter.get()) {
                try {
                    long sent = totalSent.get();
                    long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                    double rate = sent * 1000.0 / Math.max(elapsedMs, 1);
                    long eta = (long)((TOTAL_MESSAGES - sent) / Math.max(rate, 1) * 1000);
                    System.out.printf("\r[%02d:%02d] sent=%,d / %,d (%.0f msg/s) errors=%,d ETA=%02d:%02d",
                            elapsedMs / 60000, (elapsedMs % 60000) / 1000,
                            sent, TOTAL_MESSAGES, rate, totalErrors.get(),
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
        int minsPerThread = SIM_DURATION_MINS / NUM_THREADS;

        for (int t = 0; t < NUM_THREADS; t++) {
            final int threadIdx = t;
            final int minStart = t * minsPerThread;
            final int minEnd   = (t == NUM_THREADS - 1) ? SIM_DURATION_MINS : (t + 1) * minsPerThread;

            futures.add(executor.submit(() -> {
                Random rng = new Random(42 + threadIdx);

                for (int min = minStart; min < minEnd; min++) {
                    long epochMinute = baseEpochMin + min;
                    double todMultiplier = timeOfDayMultiplier(min, SIM_DURATION_MINS);

                    for (ApiProfile profile : catalog) {
                        for (IpEntry ipEntry : profile.ipPool) {
                            double effectiveScale = finalScale * todMultiplier;
                            int callCount = scaledCallCount(ipEntry.tier, effectiveScale, rng);
                            if (callCount == 0) continue;

                            for (int c = 0; c < callCount; c++) {
                                try {
                                    HttpResponseParam msg = TrafficCatalog.buildMessage(
                                            profile, ipEntry, epochMinute, rng);

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
        System.out.println("Now check the distribution and API hit count graphs for collection "
                + TrafficCatalog.COLLECTION_ID);
    }

    /**
     * Scale a tier's call count by the given factor.
     * For fractional expected counts (e.g. scale=0.003 × min=1 → expected=0.003),
     * we use probabilistic rounding so the average is correct over many minutes.
     */
    private static int scaledCallCount(TrafficCatalog.Tier tier, double scale, Random rng) {
        int naturalMin = tier.minCallsPerMin;
        int naturalMax = tier.maxCallsPerMin;
        double naturalCount = naturalMin + rng.nextInt(naturalMax - naturalMin + 1);
        double scaled = naturalCount * scale;

        int floor = (int) scaled;
        double remainder = scaled - floor;
        // Probabilistic rounding: add 1 with probability = fractional part
        return floor + (rng.nextDouble() < remainder ? 1 : 0);
    }

    /**
     * Time-of-day multiplier based on position in the simulation window.
     * Maps minute offset to a sine-curve peaking at 60% of duration (afternoon peak).
     * Range: 0.2 (overnight trough) to 1.5 (peak hours).
     */
    private static double timeOfDayMultiplier(int minOffset, int totalMins) {
        // Treat minOffset as position in a 24-hour cycle
        double hourOfDay = (minOffset % 1440) / 60.0;
        // Sine curve: peak at 14:00, trough at 02:00
        double angle = (hourOfDay - 2.0) / 24.0 * 2 * Math.PI;
        double raw = Math.sin(angle); // -1 to 1
        // Map to [0.2, 1.5]
        return 0.2 + (raw + 1.0) / 2.0 * 1.3;
    }

    private static void printCatalogSummary(List<ApiProfile> catalog) {
        System.out.printf("%-40s %-6s %-7s %6s IPs\n", "Path", "Method", "Usage", "Pool");
        System.out.println("----------------------------------------------------------------");
        for (ApiProfile p : catalog) {
            System.out.printf("%-40s %-6s %-7s %6d\n", p.path, p.method, p.usageLevel, p.ipPool.size());
        }
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
