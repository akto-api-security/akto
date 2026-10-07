package com.akto.testing.kafka_utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.time.Duration;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import org.apache.kafka.clients.consumer.*;
import org.bson.types.ObjectId;

import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.execution.TestPhaseTimer;
import com.akto.testing.TestExecutor;
import com.akto.testing.Utils;
import com.akto.testing.kafka_utils.TestRunMetrics.Stage;
import com.akto.testing.kafka_utils.TestRunMetrics.StopReason;
import com.akto.util.Constants;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import bz.stub.parallelconsumer.ParallelConsumerOptions;
import bz.stub.parallelconsumer.ParallelStreamProcessor;

public class ConsumerUtil {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ConsumerUtil.class, LogDb.TESTING);
    static Properties properties = com.akto.runtime.utils.Utils.configProperties(Constants.LOCAL_KAFKA_BROKER_URL, Constants.AKTO_KAFKA_GROUP_ID_CONFIG, Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
    static{
        properties.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, Constants.MAX_POLL_INTERVAL_MS);
        if (!KafkaConfig.applyAuthenticationPropertiesFromEnv(properties)) {
            loggerMaker.errorAndAddToDb("Kafka authentication is enabled but credentials are missing for testing consumer");
        }
        loggerMaker.warnAndAddToDb("Kafka consumer config broker=" + Constants.LOCAL_KAFKA_BROKER_URL
                + " groupId=" + Constants.AKTO_KAFKA_GROUP_ID_CONFIG
                + " maxPollIntervalMs=" + Constants.MAX_POLL_INTERVAL_MS
                + " maxPollRecords=" + Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG
                + " perTestTimeoutSec=" + Constants.MINI_TESTING_TASK_TIMEOUT_SECONDS);
    }
    private static Consumer<String, String> consumer = Constants.IS_NEW_TESTING_ENABLED ? new KafkaConsumer<>(properties) : null;

    // Named so diagnose.sh's jstack-based worker classifier (which keys off "mini-test-worker") can
    // actually find these threads - the default Executors thread factory names them "pool-N-thread-M".
    private static final AtomicInteger workerThreadCounter = new AtomicInteger();
    private static final ThreadFactory workerThreadFactory =
            r -> new Thread(r, "mini-test-worker-" + workerThreadCounter.incrementAndGet());

    public static ExecutorService executor = Executors.newFixedThreadPool(150, workerThreadFactory);
    private static final int maxRunTimeForTests = Constants.MINI_TESTING_TASK_TIMEOUT_SECONDS;
    private static final int STALL_TIMEOUT_SECONDS = 420; // > maxRunTimeForTests with margin
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final ConcurrentHashMap<ApiInfoKey, Integer> testedApisMap = new ConcurrentHashMap<>();

    /** All observability for the current run. Recreated per init(); set before any task is submitted. */
    private TestRunMetrics metrics;

    /** For per-test CPU accounting (compute vs I/O). Unsupported -> CPU reported as 0. */
    private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();
    private static final boolean CPU_TIME_SUPPORTED = THREAD_MX.isThreadCpuTimeSupported();


    // ----- ports: the only places init() touches the outside world, injectable for tests -----

    /** Kafka consumer-group lag for a (topic, group); -1 when unknown. */
    @FunctionalInterface
    interface LagSource {
        long lag(String topic, String groupId);
    }

    private final IntSupplier clock;
    private final Predicate<ObjectId> runStatus;
    private final LagSource lagSource;
    private final RunCleanup.ExitHandler exitHandler;

    public ConsumerUtil() {
        this(Context::now,
             summaryId -> GetRunningTestsStatus.getRunningTests().isTestRunning(summaryId),
             KafkaAdminClient::getConsumerLag,
             System::exit);
    }

    ConsumerUtil(IntSupplier clock, Predicate<ObjectId> runStatus, LagSource lagSource,
                 RunCleanup.ExitHandler exitHandler) {
        this.clock = clock;
        this.runStatus = runStatus;
        this.lagSource = lagSource;
        this.exitHandler = exitHandler;
    }

    public static SingleTestPayload parseTestMessage(String message) {
        JSONObject jsonObject = JSON.parseObject(message);
        ObjectId testingRunId = new ObjectId(jsonObject.getString("testingRunId"));
        ObjectId testingRunResultSummaryId = new ObjectId(jsonObject.getString("testingRunResultSummaryId"));
        ApiInfo.ApiInfoKey apiInfoKey = ApiInfo.getApiInfoKeyFromString(jsonObject.getString("apiInfoKey"));
        String subcategory = jsonObject.getString("subcategory");
        List<TestingRunResult.TestLog> testLogs = JSON.parseArray(jsonObject.getString("testLogs"), TestingRunResult.TestLog.class);
        int accountId = jsonObject.getInteger("accountId");
        return new SingleTestPayload(testingRunId, testingRunResultSummaryId, apiInfoKey, subcategory, testLogs, accountId);
    }

    public void runTestFromMessage(String message, String recordId){
        // Record the REAL worker thread here, on the worker thread itself - onSubmit only ever sees
        // the pc-pool caller (always future.get(), never the actual work), so a stall dump jstack'd
        // on that name is a dead end. This is the fix for that gap.
        metrics.onWorkerStart(recordId, Thread.currentThread().getName());
        SingleTestPayload singleTestPayload = parseTestMessage(message);
        Context.accountId.set(singleTestPayload.getAccountId());
        ObjectId summaryId = singleTestPayload.getTestingRunResultSummaryId();
        TestExecutor.setTestRunActivityContext(summaryId);
        ApiInfoKey apiInfoKey = singleTestPayload.getApiInfoKey();
        String subCategory = singleTestPayload.getSubcategory();
        try {
            TestExecutor executor = new TestExecutor();

            TestingConfigurations instance = TestingConfigurations.getInstance();

            // LOOKUP: in-memory config + sample-message resolution.
            long lookupStart = System.nanoTime();
            TestConfig testConfig = instance.getTestConfigMap().get(subCategory);
            List<String> messagesList = instance.getTestingUtil().getSampleMessages().get(apiInfoKey);
            metrics.recordStage(Stage.LOOKUP, System.nanoTime() - lookupStart);

            int timeNow = Context.now();
            if (messagesList == null || messagesList.isEmpty()) {
                metrics.markSkipped();
                String skipMsg = "Skipping test: no sample messages for apiInfoKey=" + apiInfoKey
                        + " subcategory=" + subCategory + " summaryId=" + summaryId;
                loggerMaker.errorAndAddToDb(skipMsg);
                debugLogToDb(singleTestPayload.getAccountId(), skipMsg);
            } else {
                String sample = messagesList.get(messagesList.size() - 1);
                metrics.recordPayloadSize(sample == null ? 0 : sample.length());
                loggerMaker.infoAndAddToDb("Running test for: " + apiInfoKey + " with subcategory: " + subCategory);

                // RUN_TEST wall + CPU. Recorded in a finally so a test that times out / throws still
                // gets its timing counted once it unwinds (else COST only ever sees fast completers).
                TestPhaseTimer.reset();
                long runCpuStart = CPU_TIME_SUPPORTED ? THREAD_MX.getCurrentThreadCpuTime() : -1L;
                long runWallStart = System.nanoTime();
                TestingRunResult runResult;
                try {
                    runResult = executor.runTestNew(apiInfoKey, singleTestPayload.getTestingRunId(), instance.getTestingUtil(), singleTestPayload.getTestingRunResultSummaryId(),testConfig , instance.getTestingRunConfig(), instance.isDebug(), singleTestPayload.getTestLogs(), sample);
                } finally {
                    metrics.recordStage(Stage.RUN_TEST, System.nanoTime() - runWallStart);
                    metrics.recordStage(Stage.SEND_REQUEST, TestPhaseTimer.sendReqNanos());
                    metrics.recordStage(Stage.FILTER, TestPhaseTimer.filterNanos());
                    metrics.recordStage(Stage.WORDLIST, TestPhaseTimer.wordlistNanos());
                    metrics.recordStage(Stage.VALIDATE, TestPhaseTimer.validateNanos());
                    if (runCpuStart >= 0) metrics.recordRunTestCpu(THREAD_MX.getCurrentThreadCpuTime() - runCpuStart);
                }

                executor.persistTestLogsToDb(runResult != null ? runResult.getTestLogs() : null);
                long insertStart = System.nanoTime();
                if (!Constants.SKIP_INSERT_TEST_RESULTS) {
                    executor.insertResultsAndMakeIssues(Collections.singletonList(runResult), singleTestPayload.getTestingRunResultSummaryId());
                }
                metrics.recordStage(Stage.INSERT_RESULTS, System.nanoTime() - insertStart);

                if (runResult != null && runResult.isVulnerable()) {
                    metrics.markVulnerable();
                } else {
                    metrics.markPassed();
                }

                testedApisMap.put(apiInfoKey, Context.now());

                // loggerMaker.insertImportantTestingLog("Test completed for: " + apiInfoKey + " with subcategory: " + subCategory + " in " + (Context.now() - timeNow) + " seconds");
            }
        } catch (Exception e) {
            String errMsg = "runTestFromMessage failed apiInfoKey=" + apiInfoKey
                    + " subcategory=" + subCategory + " summaryId=" + summaryId;
            loggerMaker.errorAndAddToDb(e, errMsg);
            debugLogToDb(singleTestPayload.getAccountId(), errMsg + " cause=" + e.getMessage());
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(errMsg, e);
        } finally {
            TestExecutor.clearActivityContext();
        }
    }

    private void createTimedOutResultFromMessage(String message){
        SingleTestPayload singleTestPayload = null;
        try {
            singleTestPayload = parseTestMessage(message);
            Context.accountId.set(singleTestPayload.getAccountId());
            TestExecutor.setTestRunActivityContext(singleTestPayload.getTestingRunResultSummaryId());
            TestExecutor testExecutor = new TestExecutor();

            String subCategory = singleTestPayload.getSubcategory();
            TestConfig testConfig = TestingConfigurations.getInstance().getTestConfigMap().get(subCategory);

            String testSuperType = testConfig.getInfo().getCategory().getName();
            String testSubType = testConfig.getInfo().getSubCategory();

            TestingRunResult runResult = Utils.generateFailedRunResultForMessage(singleTestPayload.getTestingRunId(), singleTestPayload.getApiInfoKey(), testSuperType, testSubType, singleTestPayload.getTestingRunResultSummaryId(), new ArrayList<>(),  TestError.TEST_TIMED_OUT.getMessage());
            if (!Constants.SKIP_INSERT_TEST_RESULTS) {
                testExecutor.insertResultsAndMakeIssues(Collections.singletonList(runResult), singleTestPayload.getTestingRunResultSummaryId());
            }
        } catch (Exception e) {
            String errMsg = "createTimedOutResultFromMessage failed"
                    + (singleTestPayload != null
                    ? (" apiInfoKey=" + singleTestPayload.getApiInfoKey()
                    + " subcategory=" + singleTestPayload.getSubcategory())
                    : "");
            loggerMaker.errorAndAddToDb(e, errMsg);
            if (singleTestPayload != null) {
                debugLogToDb(singleTestPayload.getAccountId(), errMsg + " cause=" + e.getMessage());
            }
        } finally {
            TestExecutor.clearActivityContext();
        }
    }

    private static void debugLogToDb(int accountId, String message) {
        if (!Constants.KAFKA_DEBUG_MODE) {
            return;
        }
        loggerMaker.warnAndAddToDb("[KAFKA-DEBUG] " + message);
    }

    private static void shutdownExecutorQuietly(int waitSeconds, boolean force) {
        TestingExecutorLifecycle.shutdownQuietly(executor, waitSeconds, force);
    }

    private static void closeKafkaConsumerQuietly(Consumer<String, String> c, String context) {
        if (c == null) return;
        try {
            c.close();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error closing kafka consumer (" + context + "): " + e.getMessage());
        }
    }

    private static void closeParallelConsumerQuietly(ParallelStreamProcessor<String, String> pc, boolean abrupt) {
        if (pc == null) return;
        try {
            if (abrupt) {
                pc.closeDontDrainFirst();
            } else {
                pc.closeDrainFirst();
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error closing parallel consumer: " + e.getClass().getSimpleName()
                    + " " + e.getMessage());
        }
    }

    /**
     * Builds the Kafka consumer for this attempt and wraps it in the parallel-consumer library's
     * processor. Assigns the static consumer field as a side effect (the consumer itself has to
     * exist before ParallelConsumerOptions can reference it).
     */
    private ParallelStreamProcessor<String, String> createParallelConsumer(String summaryIdForTest, int concurrency) {
        Properties consumerProperties = properties;
        if (Constants.CONCURRENT_TESTING) {
            consumerProperties = new Properties();
            consumerProperties.putAll(properties);
            consumerProperties.put(ConsumerConfig.GROUP_ID_CONFIG, Constants.getKafkaGroupIdConfig(summaryIdForTest));
        }
        consumer = new KafkaConsumer<>(consumerProperties);
        ParallelConsumerOptions<String, String> options = ParallelConsumerOptions.<String, String>builder()
            .consumer(consumer)
            .ordering(ParallelConsumerOptions.ProcessingOrder.UNORDERED)
            .maxConcurrency(concurrency)
            .commitMode(ParallelConsumerOptions.CommitMode.PERIODIC_CONSUMER_SYNC)
            // Must stay above the underlying KafkaConsumer's own default.api.timeout.ms (60s) -
            // a commit slower than that but still recoverable (observed: 60.006s) otherwise
            // exhausts the whole budget before even one attempt completes, leaving zero room
            // for the retry this setting exists to allow.
            .offsetCommitTimeout(Duration.ofSeconds(90))
            .batchSize(1)
            .maxFailureHistory(3)
            .build();
        return ParallelStreamProcessor.createEosStreamProcessor(options);
    }

    /**
     * Performs bulk update of lastTested field for all APIs that were tested
     */
    private void flushLastTestedUpdates() {
        if (testedApisMap.isEmpty()) {
            loggerMaker.infoAndAddToDb("No APIs to update for lastTested field");
            return;
        }

        try {
            dataActor.bulkUpdateLastTestedField(testedApisMap);
            testedApisMap.clear();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error during bulk update of lastTested field: " + e.getMessage());
        }
    }

    public void init(int maxRunTimeInSeconds) {
        TestingConfigurations config = TestingConfigurations.getInstance();
        int fallbackAccountId = Context.accountId.get() != null ? Context.accountId.get() : -1;
        Optional<RunPlan> maybePlan = RunPlan.from(TestingStateStore.read(), config.getMaxConcurrentRequest(),
                maxRunTimeInSeconds, clock.getAsInt(), fallbackAccountId);
        if (maybePlan.isEmpty()) {
            loggerMaker.errorAndAddToDb("No testing state available, skipping consumer init.");
            return;
        }
        RunPlan plan = maybePlan.get();
        if (plan.accountId() > 0) {
            Context.accountId.set(plan.accountId());
        }

        shutdownExecutorQuietly(5, true);
        executor = Executors.newFixedThreadPool(plan.concurrency(), workerThreadFactory);

        // Fresh observability for this run (replaces any previous run's state).
        metrics = new TestRunMetrics(plan.summaryId(), plan.startTime(), plan.expectedRecords(), executor);
        metrics.logStart(plan.accountId(), apiCount(config), testCount(config), plan.concurrency(),
                maxRunTimeForTests, plan.maxRunTimeSec());

        AtomicInteger processedRecords = new AtomicInteger(0);
        RecordProcessor processor = new RecordProcessor(executor, maxRunTimeForTests, this::runTestFromMessage);
        ParallelStreamProcessor<String, String> parallelConsumer = null;
        StopReason stopReason = StopReason.UNKNOWN;
        try {
            closeKafkaConsumerQuietly(consumer, "previous run");
            parallelConsumer = createParallelConsumer(plan.summaryId(), plan.concurrency());
            parallelConsumer.subscribe(Arrays.asList(plan.topic()));
            metrics.logConsumerUp(1);

            parallelConsumer.poll(record -> {
                var raw = record.getSingleConsumerRecord();
                String recordId = raw.topic() + "-p" + raw.partition() + "-o" + record.offset();
                handleRecord(recordId, record.value(), processor, plan.accountId(), processedRecords);
            });

            stopReason = drainUntilStopped(plan, parallelConsumer, processedRecords);
        } catch (Exception e) {
            stopReason = StopReason.ERROR;
            loggerMaker.errorAndAddToDb(e, "Error in polling records summaryId=" + plan.summaryId()
                    + " polled=" + metrics.polled()
                    + " executed=" + processedRecords.get()
                    + " expected=" + plan.expectedRecords()
                    + " errorType=" + e.getClass().getName()
                    + " cause=" + (e.getCause() != null ? e.getCause().getClass().getName() + ": " + e.getCause().getMessage() : e.getMessage()));
        } finally {
            metrics.logEnd(stopReason, RunCleanup.isAbrupt(stopReason), processedRecords.get());
            cleanupFor(plan, parallelConsumer).run(stopReason, executor);
        }
    }

    // ----- poll callback -----

    /** Body of the parallel-consumer poll callback: run one record, then map its outcome to side effects. */
    private void handleRecord(String recordId, String message, RecordProcessor processor,
                              int accountId, AtomicInteger processedRecords) {
        String threadName = Thread.currentThread().getName();
        metrics.onPolled();
        loggerMaker.infoAndAddToDb("Thread [" + threadName + "] picked up record recordId=" + recordId + " " + message);
        debugLogToDb(accountId, "picked up recordId=" + recordId + " polled=" + metrics.polled());
        try {
            if (executor.isShutdown()) {
                return;
            }
            metrics.onSubmit(recordId, threadName);
            RecordProcessor.Result result = processor.process(recordId, message);
            applyOutcome(result, recordId, message, accountId);
        } catch (Exception err) {
            String errMsg = "Thread [" + threadName + "] error executing recordId=" + recordId;
            loggerMaker.errorAndAddToDb(err, errMsg);
            debugLogToDb(accountId, errMsg + " cause=" + err.getMessage());
        } finally {
            metrics.onComplete(recordId);
            processedRecords.incrementAndGet();
            loggerMaker.infoAndAddToDb("Thread [" + threadName + "] finished processing record recordId=" + recordId);
            debugLogToDb(accountId, "finished recordId=" + recordId + " executed=" + processedRecords.get());
        }
    }

    private void applyOutcome(RecordProcessor.Result result, String recordId, String message, int accountId) {
        String errMsg;
        switch (result.outcome) {
            case COMPLETED:
                return;
            case TIMED_OUT:
                metrics.markTimedOut();
                errMsg = "Task timed out recordId=" + recordId + " after " + maxRunTimeForTests + "s";
                loggerMaker.errorAndAddToDb(result.error, errMsg);
                debugLogToDb(accountId, errMsg + " cause=" + result.error.getMessage());
                createTimedOutResultFromMessage(message);
                return;
            case INTERRUPTED:
                metrics.markTimedOut();
                errMsg = "Task interrupted recordId=" + recordId;
                loggerMaker.errorAndAddToDb(result.error, errMsg);
                debugLogToDb(accountId, errMsg + " cause=" + result.error.getMessage());
                createTimedOutResultFromMessage(message);
                return;
            case REJECTED:
                metrics.markRejected();
                errMsg = "Task rejected recordId=" + recordId + " (executor shutdown or saturated)";
                loggerMaker.errorAndAddToDb(result.error, errMsg);
                debugLogToDb(accountId, errMsg + " cause=" + result.error.getMessage());
                return;
            case ERRORED:
            default:
                metrics.markErrored();
                errMsg = "Error in task execution recordId=" + recordId + " cause=" + result.error.getMessage();
                loggerMaker.errorAndAddToDb(result.error, errMsg);
                debugLogToDb(accountId, errMsg + " cause=" + result.error.getMessage());
        }
    }

    // ----- drain loop -----

    /**
     * Polls the stop policy every ~100ms until it yields a reason. Completion is decided by Kafka
     * lag, not a local counter: processedRecords restarts at zero on every init(), so a resumed
     * drain could never satisfy a count-based check and simply spun until max runtime. Lag is
     * absolute - zero means every record has been processed and committed, whoever produced or
     * consumed them.
     */
    private StopReason drainUntilStopped(RunPlan plan, ParallelStreamProcessor<String, String> parallelConsumer,
                                         AtomicInteger processedRecords) throws InterruptedException {
        StopPolicy policy = new StopPolicy(plan.maxRunTimeSec(),
                new StopPolicy.ProgressTracker(STALL_TIMEOUT_SECONDS, clock.getAsInt()));
        ObjectId summaryObjectId = new ObjectId(plan.summaryId());

        while (true) {
            int now = clock.getAsInt();
            int processed = processedRecords.get();
            long workRemaining = parallelConsumer.workRemaining();

            StopReason reason = policy.evaluate(new StopPolicy.Signals(
                    runStatus.test(summaryObjectId),
                    now - plan.startTime(),
                    parallelConsumer.isClosedOrFailed(),
                    processed,
                    workRemaining,
                    () -> lagSource.lag(plan.topic(), plan.groupId())), now);

            if (reason != null) {
                logStop(reason, plan, workRemaining);
                stopExecutorFor(reason, plan, now);
                return reason;
            }
            // Only actually calls Kafka at the heartbeat's own cadence (tick decides
            // internally), not once per ~100ms loop iteration.
            metrics.tick(processed, workRemaining, () -> lagSource.lag(plan.topic(), plan.groupId()));
            Thread.sleep(100);
        }
    }

    private void logStop(StopReason reason, RunPlan plan, long workRemaining) {
        switch (reason) {
            case STOPPED:
                loggerMaker.infoAndAddToDb("Tests have been marked stopped.");
                break;
            case MAX_RUNTIME:
                loggerMaker.infoAndAddToDb("Max run time reached. Stopping consumer.");
                break;
            case CONSUMER_FAILED:
                loggerMaker.errorAndAddToDb("Consumer engine closed/failed summaryId=" + plan.summaryId());
                break;
            case STALLED:
                loggerMaker.errorAndAddToDb("No progress for " + STALL_TIMEOUT_SECONDS + "s summaryId="
                        + plan.summaryId() + " workRemaining=" + workRemaining);
                break;
            default:
                break;
        }
    }

    /** ALL_PROCESSED gets a short bounded grace for in-flight work; every other reason reaps immediately. */
    private void stopExecutorFor(StopReason reason, RunPlan plan, int now) {
        if (reason == StopReason.ALL_PROCESSED) {
            int remainingTime = Math.min(plan.remainingSec(now), maxRunTimeForTests);
            shutdownExecutorQuietly(Math.min(remainingTime, 5), true);
        } else {
            executor.shutdownNow();
        }
    }

    // ----- cleanup -----

    private RunCleanup cleanupFor(RunPlan plan, ParallelStreamProcessor<String, String> parallelConsumer) {
        return new RunCleanup(
                TestingExecutorLifecycle::shutdownQuietly,
                this::flushLastTestedUpdates,
                abrupt -> {
                    closeParallelConsumerQuietly(parallelConsumer, abrupt);
                    closeKafkaConsumerQuietly(consumer, "shutdown");
                },
                () -> {
                    Producer.deleteTestResultsTopic(plan.summaryId());
                    TestingStateStore.clear();
                },
                code -> {
                    loggerMaker.errorAndAddToDb("Exiting process summaryId=" + plan.summaryId()
                            + " (state kept for resume)");
                    exitHandler.exit(code);
                });
    }

    private static int apiCount(TestingConfigurations config) {
        return (config.getTestingUtil() != null && config.getTestingUtil().getSampleMessages() != null)
                ? config.getTestingUtil().getSampleMessages().size() : -1;
    }

    private static int testCount(TestingConfigurations config) {
        return config.getTestConfigMap() != null ? config.getTestConfigMap().size() : -1;
    }
}
