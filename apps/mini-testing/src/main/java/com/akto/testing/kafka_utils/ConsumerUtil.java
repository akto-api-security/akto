package com.akto.testing.kafka_utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import org.apache.kafka.clients.consumer.*;
import org.bson.types.ObjectId;

import com.akto.crons.GetRunningTestsStatus;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.test_editor.TestConfig;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.TestingRunResult;
import com.akto.dto.testing.TestResult.TestError;
import com.akto.dto.testing.info.SingleTestPayload;
import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.test_editor.execution.TestPhaseTimer;
import com.akto.testing.TestExecutor;
import com.akto.testing.TestingLease;
import com.akto.testing.Utils;
import com.akto.testing.kafka_utils.TestRunMetrics.Stage;
import com.akto.util.Constants;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.BasicDBObject;

import io.confluent.parallelconsumer.ParallelConsumerOptions;
import io.confluent.parallelconsumer.ParallelStreamProcessor;

public class ConsumerUtil {

    /** If queue stays empty with no processed progress this long, treat remaining expected records as lost. */
    private static final long DRAIN_IDLE_GRACE_MS = 5L * 60L * 1000L;

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
                + " maxPollRecords=" + Constants.AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG);
    }
    private static Consumer<String, String> consumer = Constants.IS_NEW_TESTING_ENABLED ? new KafkaConsumer<>(properties) : null;

    // Named so diagnose.sh's jstack-based worker classifier (which keys off "mini-test-worker") can
    // actually find these threads - the default Executors thread factory names them "pool-N-thread-M".
    private static final AtomicInteger workerThreadCounter = new AtomicInteger();
    private static final ThreadFactory workerThreadFactory =
            r -> new Thread(r, "mini-test-worker-" + workerThreadCounter.incrementAndGet());

    public static ExecutorService executor = Executors.newFixedThreadPool(150, workerThreadFactory);
    private static final int maxRunTimeForTests = 5 * 60;
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final ConcurrentHashMap<ApiInfoKey, Integer> testedApisMap = new ConcurrentHashMap<>();

    /** All observability for the current run. Recreated per init(); set before any task is submitted. */
    private TestRunMetrics metrics;

    /** For per-test CPU accounting (compute vs I/O). Unsupported -> CPU reported as 0. */
    private static final ThreadMXBean THREAD_MX = ManagementFactory.getThreadMXBean();
    private static final boolean CPU_TIME_SUPPORTED = THREAD_MX.isThreadCpuTimeSupported();

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

    /**
     * @param summaryIdForTest the attempt being drained - supplied by the caller now rather than
     *                         read back from a file, which is what lets a different pod resume it
     * @param pickedUpTimestamp when the attempt started, so a resumed drain inherits the original
     *                          deadline instead of restarting the clock
     */
    public void init(int maxRunTimeInSeconds, String summaryIdForTest, int pickedUpTimestamp) {
        if (summaryIdForTest == null) {
            loggerMaker.errorAndAddToDb("No summary id supplied, skipping consumer init.");
            return;
        }

        TestingConfigurations instance = TestingConfigurations.getInstance();
        int concurrency = instance.getMaxConcurrentRequest();
        shutdownExecutorQuietly(5, true);
        executor = Executors.newFixedThreadPool(concurrency, workerThreadFactory);

        final ObjectId summaryObjectId = new ObjectId(summaryIdForTest);
        int startTime = pickedUpTimestamp > 0 ? pickedUpTimestamp : Context.now();
        int effectiveMaxRunTime = maxRunTimeInSeconds;
        final int accountId = Context.accountId.get() != null ? Context.accountId.get() : -1;
        final String topicName = Constants.getTestResultsTopicName(summaryIdForTest);
        final String groupId = Constants.getKafkaGroupIdConfig(summaryIdForTest);
        AtomicBoolean firstRecordRead = new AtomicBoolean(false);
        AtomicInteger processedRecords = new AtomicInteger(0);

        // Fresh observability for this run (replaces any previous run's state).
        metrics = new TestRunMetrics(summaryIdForTest, startTime, -1, executor);
        int apiCount = (instance.getTestingUtil() != null && instance.getTestingUtil().getSampleMessages() != null)
                ? instance.getTestingUtil().getSampleMessages().size() : -1;
        int testCount = instance.getTestConfigMap() != null ? instance.getTestConfigMap().size() : -1;
        metrics.logStart(accountId, apiCount, testCount, concurrency,
                maxRunTimeForTests, effectiveMaxRunTime);

        // reaching init() at all means production is done for this attempt - the caller checks
        // producerDone before getting here, so there is no separate in-band flag to consult
        boolean isConsumerRunning = true;

        ParallelStreamProcessor<String, String> parallelConsumer = null;

        /*
         * Edge case:
         * In case the module restarts and starts processing the incomplete testing run,
         * then the consumer will process some of the records again.
         * This happens because the commits to kafka are periodic (5 seconds, default) and not per message.
         */
        
        boolean consumerFailed = false;
        TestRunMetrics.StopReason stopReason = TestRunMetrics.StopReason.UNKNOWN;
        int consumerAttempt = 0;
        try {
            boolean restartConsumer = isConsumerRunning;
            while (restartConsumer) {
                restartConsumer = false;
                consumerAttempt++;
                if (parallelConsumer != null) {
                    try {
                        parallelConsumer.closeDontDrainFirst();
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error closing parallel consumer: " + e.getClass().getSimpleName()
                                + " " + e.getMessage());
                    }
                    parallelConsumer = null;
                    firstRecordRead.set(false);
                }
                if (consumer != null) {
                    try {
                        consumer.close();
                    } catch (Exception e) {
                        loggerMaker.warnAndAddToDb("Error closing previous kafka consumer: " + e.getMessage());
                    }
                }
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
                    .batchSize(1)
                    .maxFailureHistory(3)
                    .build();
                parallelConsumer = ParallelStreamProcessor.createEosStreamProcessor(options);
                parallelConsumer.subscribe(Arrays.asList(Constants.getTestResultsTopicName(summaryIdForTest)));
                metrics.logConsumerUp(consumerAttempt);

                parallelConsumer.poll(record -> {
                    String threadName = Thread.currentThread().getName();
                    String message = record.value();
                    String recordId = record.getSingleConsumerRecord().topic() + "-p" + record.getSingleConsumerRecord().partition() + "-o" + record.offset();
                    metrics.onPolled();
                    loggerMaker.infoAndAddToDb("Thread [" + threadName + "] picked up record recordId=" + recordId + " " + message);
                    debugLogToDb(accountId, "picked up recordId=" + recordId + " polled=" + metrics.polled());
                    try {
                        if(!executor.isShutdown()){
                            metrics.onSubmit(recordId, threadName);
                            Future<?> future = executor.submit(() -> runTestFromMessage(message, recordId));
                            firstRecordRead.set(true);
                            try {
                                future.get(maxRunTimeForTests, TimeUnit.SECONDS);
                            } catch (TimeoutException e) {
                                metrics.markTimedOut();
                                String errMsg = "Task timed out recordId=" + recordId
                                        + " after " + maxRunTimeForTests + "s";
                                loggerMaker.errorAndAddToDb(e, errMsg);
                                debugLogToDb(accountId, errMsg + " cause=" + e.getMessage());
                                future.cancel(true);
                                createTimedOutResultFromMessage(message);
                            } catch (InterruptedException e) {
                                metrics.markTimedOut();
                                Thread.currentThread().interrupt();
                                String errMsg = "Task interrupted recordId=" + recordId;
                                loggerMaker.errorAndAddToDb(e, errMsg);
                                debugLogToDb(accountId, errMsg + " cause=" + e.getMessage());
                                future.cancel(true);
                                createTimedOutResultFromMessage(message);
                            } catch(RejectedExecutionException e){
                                metrics.markRejected();
                                String errMsg = "Task rejected recordId=" + recordId
                                        + " (executor shutdown or saturated)";
                                loggerMaker.errorAndAddToDb(e, errMsg);
                                debugLogToDb(accountId, errMsg + " cause=" + e.getMessage());
                                future.cancel(true);
                            } catch (Exception e) {
                                metrics.markErrored();
                                future.cancel(true);
                                String errMsg = "Error in task execution recordId=" + recordId + "cause=" + e.getMessage();
                                loggerMaker.errorAndAddToDb(e, errMsg);
                                debugLogToDb(accountId, errMsg + " cause=" + e.getMessage());
                            }
                        }
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
                });

            long drainIdleSinceMs = -1L;
            int lastProcessedSeen = -1;
            while (parallelConsumer != null) {
                if(!GetRunningTestsStatus.getRunningTests().isTestRunning(summaryObjectId)){
                    stopReason = TestRunMetrics.StopReason.STOPPED;
                    loggerMaker.infoAndAddToDb("Tests have been marked stopped.");
                    executor.shutdownNow();
                    break;
                }
                else if ((Context.now() - startTime >= effectiveMaxRunTime)) {
                    stopReason = TestRunMetrics.StopReason.MAX_RUNTIME;
                    loggerMaker.infoAndAddToDb("Max run time reached. Stopping consumer.");
                    executor.shutdownNow();
                    break;
                }

                long nowMs = System.currentTimeMillis();
                int processed = processedRecords.get();
                if (processed > lastProcessedSeen) {
                    lastProcessedSeen = processed;
                    drainIdleSinceMs = -1L;
                }

                long workRemaining = parallelConsumer.workRemaining();
                metrics.tick(processed, workRemaining);

                /*
                 * Completion is decided by kafka, not by counting locally. The old check compared
                 * processedRecords - which restarts at zero on every init() - against a total that
                 * does not, so a resumed drain could never satisfy it and simply spun until max
                 * runtime. Lag is absolute: whoever produced the messages and whoever consumed
                 * them, zero means every record has been processed and committed.
                 */
                boolean locallyEmpty = workRemaining == 0;
                if (locallyEmpty) {
                    long lag = Producer.getConsumerLag(topicName, groupId);
                    if (lag == 0) {
                        stopReason = TestRunMetrics.StopReason.ALL_PROCESSED;
                        int remainingTime = Math.min(Math.max(0, effectiveMaxRunTime - (Context.now() - startTime)), maxRunTimeForTests);
                        shutdownExecutorQuietly(Math.min(remainingTime, 5), true);
                        break;
                    }

                    if (drainIdleSinceMs < 0) {
                        drainIdleSinceMs = nowMs;
                    } else if (nowMs - drainIdleSinceMs >= DRAIN_IDLE_GRACE_MS) {
                        if (lag > 0) {
                            // work is still outstanding but none is arriving - re-subscribe
                            stopReason = TestRunMetrics.StopReason.IDLE_RESTART;
                            metrics.logRestart(DRAIN_IDLE_GRACE_MS, processed, workRemaining);
                            restartConsumer = true;
                            break;
                        }
                        // lag unreadable (-1) for a full idle grace: stop rather than spin, but
                        // never treat unreadable as zero - a broker hiccup is not completion
                        stopReason = TestRunMetrics.StopReason.IDLE_COMPLETE;
                        int remainingTime = Math.min(Math.max(0, effectiveMaxRunTime - (Context.now() - startTime)), maxRunTimeForTests);
                        shutdownExecutorQuietly(Math.min(remainingTime, 5), true);
                        break;
                    }
                } else {
                    drainIdleSinceMs = -1L;
                }

                if (TestingLease.getInstance().isLost()) {
                    stopReason = TestRunMetrics.StopReason.LEASE_LOST;
                    loggerMaker.errorAndAddToDb("Lease lost while draining summary " + summaryIdForTest
                            + "; another module owns it now. Stopping without touching its topic.");
                    executor.shutdownNow();
                    break;
                }
                    Thread.sleep(100);
                }
            }

        } catch (Exception e) {
            consumerFailed = true;
            stopReason = TestRunMetrics.StopReason.ERROR;
            String errMsg = "Error in polling records summaryId=" + summaryIdForTest
                    + " polled=" + metrics.polled()
                    + " executed=" + processedRecords.get()
                    + " errorType=" + e.getClass().getName()
                    + " cause=" + (e.getCause() != null ? e.getCause().getClass().getName() + ": " + e.getCause().getMessage() : e.getMessage());
            loggerMaker.errorAndAddToDb(e, errMsg);
        }finally{
            metrics.logEnd(stopReason, consumerFailed, processedRecords.get());

            flushLastTestedUpdates();
            shutdownExecutorQuietly(consumerFailed ? 5 : 30, consumerFailed);

            if(parallelConsumer != null){
                try {
                    if (consumerFailed) {
                        parallelConsumer.closeDontDrainFirst();
                    } else {
                        parallelConsumer.closeDrainFirst();
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error closing parallel consumer: " + e.getClass().getSimpleName()
                            + " " + e.getMessage());
                }
            }
            parallelConsumer = null;
            if (consumer != null) {
                try {
                    consumer.close();
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e,"Error closing kafka consumer: " + e.getMessage());
                }
            }
            /*
             * Only the current owner may destroy the work set. A pod that stalled long enough to
             * lose its lease can wake up inside this block while the new owner is mid-drain, and
             * deleting the topic there would take the run down with it - the very failure the
             * lease exists to prevent, arriving through the cleanup path instead of the work path.
             */
            if (stopReason == TestRunMetrics.StopReason.LEASE_LOST || TestingLease.getInstance().isLost()) {
                loggerMaker.errorAndAddToDb("Not deleting topic for summary " + summaryIdForTest
                        + ": lease no longer held.");
            } else {
                Producer.deleteTestResultsTopic(summaryIdForTest);
            }
        }
    }
}
