package com.akto.datadog;

import com.akto.data_actor.DataActorFactory;
import com.akto.data_actor.DataActor;
import com.akto.dto.Config;
import com.akto.log.LoggerMaker;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class DatadogForwarderMain {

    private static final LoggerMaker loggerMaker = new LoggerMaker(DatadogForwarderMain.class, LoggerMaker.LogDb.RUNTIME);

    public static void main(String[] args) throws Exception {
        String kafkaBrokerUrl = System.getenv("AKTO_KAFKA_BROKER_MAL");
        String kafkaTopic = System.getenv("AKTO_KAFKA_TOPIC_NAME");

        if (kafkaBrokerUrl == null || kafkaBrokerUrl.isEmpty()) {
            loggerMaker.errorAndAddToDb("AKTO_KAFKA_BROKER_MAL env var is required", LoggerMaker.LogDb.RUNTIME);
            System.exit(1);
        }
        if (kafkaTopic == null || kafkaTopic.isEmpty()) {
            loggerMaker.errorAndAddToDb("AKTO_KAFKA_TOPIC_NAME env var is required", LoggerMaker.LogDb.RUNTIME);
            System.exit(1);
        }

        DataActor dataActor = DataActorFactory.fetchInstance();

        AtomicReference<Config.DatadogForwarderConfig> configRef = new AtomicReference<>();
        configRef.set(dataActor.fetchDatadogForwarderConfig());

        ScheduledExecutorService configPoller = Executors.newSingleThreadScheduledExecutor();
        configPoller.scheduleAtFixedRate(() -> {
            try {
                configRef.set(dataActor.fetchDatadogForwarderConfig());
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error polling DatadogForwarderConfig: " + e, LoggerMaker.LogDb.RUNTIME);
            }
        }, 60, 60, TimeUnit.SECONDS);

        DatadogForwarder forwarder = new DatadogForwarder(kafkaBrokerUrl, kafkaTopic, configRef);
        forwarder.run();
    }
}
