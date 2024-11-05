package com.akto.traffic;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.function.FailableFunction;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.errors.WakeupException;
import com.akto.DaoInit;
import com.akto.RuntimeMode;
import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.AccountSettings;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.metrics.AllMetrics;
import com.akto.runtime.utils.Utils;
import com.mongodb.ConnectionString;

public class KafkaRunner {
    private static final LoggerMaker loggerMaker =
            new LoggerMaker(KafkaRunner.class, LogDb.RUNTIME);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static void init(
            Consumer<String, String> kafkaConsumer,
            LogDb module,
            List<String> topics,
            FailableFunction<ConsumerRecords<String, String>, Void, Exception> recordProcessor) {
        loggerMaker.setDb(module);

        boolean hybridSaas = RuntimeMode.isHybridDeployment();
        boolean connected = false;
        if (hybridSaas) {
            AccountSettings accountSettings = dataActor.fetchAccountSettings();
            if (accountSettings != null) {
                int acc = accountSettings.getId();
                Context.accountId.set(acc);
                connected = true;
            }
        } else {
            String mongoURI = System.getenv("AKTO_MONGO_CONN");
            DaoInit.init(new ConnectionString(mongoURI));
            Context.accountId.set(1_000_000);
            connected = true;
        }

        if (connected) {
            loggerMaker.infoAndAddToDb(
                    String.format("Starting module for account : %d", Context.accountId.get()));
            AllMetrics.instance.init(module);
        }

        final Thread mainThread = Thread.currentThread();
        final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);

        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread() {
                            public void run() {
                                kafkaConsumer.wakeup();
                                try {
                                    if (!exceptionOnCommitSync.get()) {
                                        mainThread.join();
                                    }
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                } catch (Error e) {
                                    loggerMaker.errorAndAddToDb(
                                            "Error in main thread: " + e.getMessage());
                                }
                            }
                        });

        scheduler.scheduleAtFixedRate(
                () -> {
                    try {
                        logKafkaMetrics(kafkaConsumer);
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(
                                e,
                                String.format(
                                        "Failed to get kafka metrics for %s error: %s",
                                        module.name(), e.toString()));
                    }
                },
                0,
                1,
                TimeUnit.MINUTES);

        try {
            kafkaConsumer.subscribe(topics);
            loggerMaker.infoAndAddToDb(
                    String.format("Consumer subscribed for topics : %s", topics.toString()));
            while (true) {
                ConsumerRecords<String, String> records =
                        kafkaConsumer.poll(Duration.ofMillis(10000));
                kafkaConsumer.commitSync();

                try {
                    recordProcessor.apply(records);
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error while processing kafka messages " + e);
                }
            }
        } catch (WakeupException ignored) {
            // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
            exceptionOnCommitSync.set(true);
            Utils.printL(e);
            loggerMaker.errorAndAddToDb("Error in Kafka consumer: " + e.getMessage());
            e.printStackTrace();
            System.exit(0);
        } finally {
            kafkaConsumer.close();
        }
    }

    private static void logKafkaMetrics(Consumer<String, String> kafkaConsumer) {
        Map<MetricName, ? extends Metric> metrics = kafkaConsumer.metrics();
        for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
            MetricName key = entry.getKey();
            Metric value = entry.getValue();

            if (key.name().equals("records-lag-max")) {
                double val =
                        value.metricValue().equals(Double.NaN) ? 0d : (double) value.metricValue();
                AllMetrics.instance.setKafkaRecordsLagMax((float) val);
            }
            if (key.name().equals("records-consumed-rate")) {
                double val =
                        value.metricValue().equals(Double.NaN) ? 0d : (double) value.metricValue();
                AllMetrics.instance.setKafkaRecordsConsumedRate((float) val);
            }

            if (key.name().equals("fetch-latency-avg")) {
                double val =
                        value.metricValue().equals(Double.NaN) ? 0d : (double) value.metricValue();
                AllMetrics.instance.setKafkaFetchAvgLatency((float) val);
            }

            if (key.name().equals("bytes-consumed-rate")) {
                double val =
                        value.metricValue().equals(Double.NaN) ? 0d : (double) value.metricValue();
                AllMetrics.instance.setKafkaBytesConsumedRate((float) val);
            }
        }
    }
}
