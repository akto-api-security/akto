package com.akto.util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import org.bson.BsonArray;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.bson.RawBsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoClientSettings;
import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;

/**
 * Counts the documents and bytes the reads (find, getMore, aggregate) of this process return, by job and collection,
 * so that the load of a container can be traced to the job that causes it. A job is named by the thread that runs it:
 * {@link #startTask} and {@link #endTask} (AccountTask and JobsCron do this); reads of a thread without a job are
 * counted under the name of that thread.
 *
 * Off unless the environment variable AKTO_MONGO_READ_STATS is "true", because the driver then decodes every reply
 * so that its size can be counted. Every REPORT_SECONDS it logs the biggest (job, collection) pairs with
 * the CRON_STATS_LOG_TAG, then starts counting again.
 */
public class MongoReadStats implements CommandListener {

    private static final Logger logger = LoggerFactory.getLogger(MongoReadStats.class);

    private static final String ENABLED_ENV = "AKTO_MONGO_READ_STATS";
    private static final int REPORT_SECONDS = 300;
    private static final int TOP_JOB_COLLECTIONS = 25;
    private static final int TOP_DATABASES = 10;
    private static final double MB = 1024.0 * 1024.0;
    private static final Set<String> READ_COMMANDS = new HashSet<>(Arrays.asList("find", "getMore", "aggregate"));
    private static final BsonDocumentCodec REPLY_CODEC = new BsonDocumentCodec();

    private static final MongoReadStats instance = new MongoReadStats();
    private static final AtomicBoolean reporterStarted = new AtomicBoolean();

    private static final ThreadLocal<String> task = new ThreadLocal<>();
    // documents and bytes read by the thread since startTask
    private static final ThreadLocal<long[]> taskTotals = new ThreadLocal<>();
    // database and collection of the command being run by the thread
    private static final ThreadLocal<String[]> inFlight = new ThreadLocal<>();

    private static volatile Map<String, Totals> byJobCollection = new ConcurrentHashMap<>();
    private static volatile Map<String, Totals> byDatabase = new ConcurrentHashMap<>();

    private static class Totals {
        final LongAdder commands = new LongAdder();
        final LongAdder docs = new LongAdder();
        final LongAdder bytes = new LongAdder();

        void add(long docCount, long byteCount) {
            commands.increment();
            docs.add(docCount);
            bytes.add(byteCount);
        }
    }

    private MongoReadStats() {}

    public static boolean isEnabled() {
        return "true".equalsIgnoreCase(System.getenv(ENABLED_ENV));
    }

    /** Counts the reads of the client being built, when AKTO_MONGO_READ_STATS is set. */
    public static void addTo(MongoClientSettings.Builder builder) {
        if (!isEnabled()) {
            return;
        }
        builder.addCommandListener(instance);
        if (reporterStarted.compareAndSet(false, true)) {
            ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "mongo-read-stats-reporter");
                thread.setDaemon(true);
                return thread;
            });
            reporter.scheduleAtFixedRate(MongoReadStats::report, REPORT_SECONDS, REPORT_SECONDS, TimeUnit.SECONDS);
        }
        logger.info("{} mongo read stats on, reported every {} seconds", Constants.CRON_STATS_LOG_TAG, REPORT_SECONDS);
    }

    /** From now on the reads of this thread are counted under this job name. */
    public static void startTask(String taskName) {
        task.set(taskName);
        taskTotals.set(new long[2]);
    }

    /** Ends the job of this thread. Returns what it read, as " docs=... read=...MB", or "" when the stats are off. */
    public static String endTask() {
        long[] totals = taskTotals.get();
        task.remove();
        taskTotals.remove();
        if (totals == null || !isEnabled()) {
            return "";
        }
        return String.format(" docs=%d read=%.1fMB", totals[0], totals[1] / MB);
    }

    @Override
    public void commandStarted(CommandStartedEvent event) {
        String command = event.getCommandName();
        if (!READ_COMMANDS.contains(command)) {
            inFlight.remove();
            return;
        }
        BsonValue collection = event.getCommand().get("getMore".equals(command) ? "collection" : command);
        inFlight.set(new String[]{event.getDatabaseName(), collection != null && collection.isString() ? collection.asString().getValue() : "?"});
    }

    @Override
    public void commandSucceeded(CommandSucceededEvent event) {
        String[] target = inFlight.get();
        inFlight.remove();
        if (target == null) {
            return;
        }
        try {
            BsonDocument cursor = event.getResponse().getDocument("cursor", null);
            if (cursor == null) {
                return;
            }
            BsonArray batch = cursor.getArray("firstBatch", cursor.getArray("nextBatch", null));
            long docs = batch == null ? 0 : batch.size();
            long bytes = new RawBsonDocument(event.getResponse(), REPLY_CODEC).getByteBuffer().remaining();

            String job = task.get() != null ? task.get() : "thread:" + Thread.currentThread().getName();
            byJobCollection.computeIfAbsent(job + " | " + target[1], k -> new Totals()).add(docs, bytes);
            byDatabase.computeIfAbsent(target[0], k -> new Totals()).add(docs, bytes);
            long[] totals = taskTotals.get();
            if (totals != null) {
                totals[0] += docs;
                totals[1] += bytes;
            }
        } catch (Exception e) {
            logger.error("Error counting the reply of a mongo read", e);
        }
    }

    @Override
    public void commandFailed(CommandFailedEvent event) {
        inFlight.remove();
    }

    private static void report() {
        try {
            Map<String, Totals> jobCollections = byJobCollection;
            Map<String, Totals> databases = byDatabase;
            byJobCollection = new ConcurrentHashMap<>();
            byDatabase = new ConcurrentHashMap<>();

            long totalBytes = jobCollections.values().stream().mapToLong(t -> t.bytes.sum()).sum();
            logger.info("{} mongo-reads window={}s total={}MB ({}MB/s)", Constants.CRON_STATS_LOG_TAG, REPORT_SECONDS,
                    String.format("%.1f", totalBytes / MB), String.format("%.2f", totalBytes / MB / REPORT_SECONDS));
            log("job | collection", jobCollections, TOP_JOB_COLLECTIONS);
            log("database", databases, TOP_DATABASES);
        } catch (Exception e) {
            logger.error("Error reporting the mongo read stats", e);
        }
    }

    private static void log(String what, Map<String, Totals> totals, int top) {
        totals.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().bytes.sum(), a.getValue().bytes.sum()))
                .limit(top)
                .collect(Collectors.toList())
                .forEach(e -> logger.info("{} mongo-reads {}={} commands={} docs={} read={}MB", Constants.CRON_STATS_LOG_TAG, what,
                        e.getKey(), e.getValue().commands.sum(), e.getValue().docs.sum(),
                        String.format("%.1f", e.getValue().bytes.sum() / MB)));
    }
}
