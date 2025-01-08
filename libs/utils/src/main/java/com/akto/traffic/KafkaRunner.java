package com.akto.traffic;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
    private Consumer<String, String> consumer;
    private static final LoggerMaker loggerMaker = new LoggerMaker(KafkaRunner.class, LogDb.RUNTIME);
    private static final DataActor dataActor = DataActorFactory.fetchInstance();

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    public static void processKafkaRecords(LogDb module, List<String> topics,
            FailableFunction<ConsumerRecords<String, String>, Void, Exception> recordProcessor) {

        loggerMaker.setDb(module);

        boolean hybridSaas = RuntimeMode.isHybridDeployment();
        boolean connected =false;
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
            loggerMaker.infoAndAddToDb(String.format("Starting module for account : %d", Context.accountId.get()));
            AllMetrics.instance.init(module);
        }

        String kafkaBrokerUrl = "kafka1:19092";
        String isKubernetes = System.getenv("IS_KUBERNETES");
        if (isKubernetes != null && isKubernetes.equalsIgnoreCase("true")) {
            loggerMaker.infoAndAddToDb("is_kubernetes: true");
            kafkaBrokerUrl = "127.0.0.1:29092";
        }
        String groupIdConfig = System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG");
        int maxPollRecordsConfig = Integer
                .parseInt(System.getenv().getOrDefault("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG", "100"));

        Properties properties = Utils.configProperties(kafkaBrokerUrl, groupIdConfig, maxPollRecordsConfig);
        final KafkaRunner main = new KafkaRunner();
        main.consumer = new KafkaConsumer<>(properties);

        final Thread mainThread = Thread.currentThread();
        final AtomicBoolean exceptionOnCommitSync = new AtomicBoolean(false);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                main.consumer.wakeup();
                try {
                    if (!exceptionOnCommitSync.get()) {
                        mainThread.join();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Error e) {
                    loggerMaker.errorAndAddToDb("Error in main thread: " + e.getMessage());
                }
            }
        });

        scheduler.scheduleAtFixedRate(() -> {
            try {
                logKafkaMetrics(main, module);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e,
                        String.format("Failed to get kafka metrics for %s error: %s", module.name(), e.toString()));
            }
        }, 0, 1, TimeUnit.MINUTES);

        try {
            main.consumer.subscribe(topics);
            loggerMaker.infoAndAddToDb(String.format("Consumer subscribed for topics : %s", topics.toString()));
            while (true) {
                ConsumerRecords<String, String> records = main.consumer.poll(Duration.ofMillis(10000));
                try {
                    main.consumer.commitSync();
                } catch (Exception e) {
                    throw e;
                }

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
            main.consumer.close();
        }
    }

    private static void logKafkaMetrics(KafkaRunner main, LogDb module) {
        Map<MetricName, ? extends Metric> metrics = main.consumer.metrics();
        for (Map.Entry<MetricName, ? extends Metric> entry : metrics.entrySet()) {
            MetricName key = entry.getKey();
            Metric value = entry.getValue();

            if (key.name().equals("records-lag-max")) {
                double val = value.metricValue().equals(Double.NaN) ? 0d : (double) value.metricValue();
                AllMetrics.instance.setKafkaRecordsLagMax((float) val);
            }
            if (key.name().equals("records-consumed-rate")) {
                double val = value.metricValue().equals(Double.NaN) ? 0d : (double) value.metricValue();
                AllMetrics.instance.setKafkaRecordsConsumedRate((float) val);
            }

            if (key.name().equals("fetch-latency-avg")) {
                double val = value.metricValue().equals(Double.NaN) ? 0d : (double) value.metricValue();
                AllMetrics.instance.setKafkaFetchAvgLatency((float) val);
            }

            if (key.name().equals("bytes-consumed-rate")) {
                double val = value.metricValue().equals(Double.NaN) ? 0d : (double) value.metricValue();
                AllMetrics.instance.setKafkaBytesConsumedRate((float) val);
            }
        }
    }

}