package com.akto.kafka;

import com.akto.log.LoggerMaker;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DeleteTopicsResult;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.akto.log.LoggerMaker.LogDb;

import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Kafka {
    private static final Logger logger = LoggerFactory.getLogger(Kafka.class);
    private static final LoggerMaker loggerMaker = new LoggerMaker(Kafka.class, LogDb.TESTING);
    private KafkaProducer<String, String> producer;
    public boolean producerReady;

    public Kafka(String brokerIP, int lingerMS, int batchSize, int maxRequestTimeout, int maxRetries) {
        producerReady = false;
        try {
            setProducer(brokerIP, lingerMS, batchSize, maxRequestTimeout, maxRetries);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Kafka(String brokerIP, int lingerMS, int batchSize) {
        producerReady = false;
        try {
            setProducer(brokerIP, lingerMS, batchSize, 5000, 0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Kafka(String brokerIP, int lingerMS, int batchSize, String username, String password, boolean isAuthenticationEnabled) {
        producerReady = false;
        try {
            setProducer(brokerIP, lingerMS, batchSize, 5000, 0, username, password, isAuthenticationEnabled );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendWithCounter(String message, String topic, AtomicInteger counter) {
        if (!this.producerReady) {
            loggerMaker.errorAndAddToDb("Producer not ready. Cannot send message. Counter will remain incremented. Current counter: " + counter.get());
            return;
        };

        ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
        try {
            producer.send(record, (recordMetadata, e) -> {
                if (e != null) {
                    loggerMaker.infoAndAddToDb("Failed to send message to Kafka. Error: " + e.getMessage()+ " for message " +message+ ". Counter will remain incremented. Current counter: " + counter.get());
                } else {
                    logger.info(message + " sent to topic " + topic + " with offset " + recordMetadata.offset());
                    counter.decrementAndGet();
                }
            });
        } catch (Exception sendException) {
            loggerMaker.errorAndAddToDb(sendException, "Exception occurred while sending message to Kafka: " + sendException.getMessage() + ". Counter will remain incremented. Current counter: " + counter.get());
        }
    }

    public void send(String message,String topic) {
        if (!this.producerReady) return;

        ProducerRecord<String, String> record = new ProducerRecord<>(topic,message);
        producer.send(record, new DemoProducerCallback());
    }

    public void close() {
        //this.producerReady = false;
        //producer.close(Duration.ofMillis(0)); // close immediately
    }

    private void setProducer(String brokerIP, int lingerMS, int batchSize, int maxRequestTimeout, int maxRetries) {
        setProducer(brokerIP, lingerMS, batchSize, maxRequestTimeout, maxRetries, null, null, false);
    }

    private void setProducer(String brokerIP, int lingerMS, int batchSize, int maxRequestTimeout, int maxRetries, String username, String password, boolean isAuthenticationEnabled) {
        if (producer != null) close(); // close existing producer connection

        Properties kafkaProps = KafkaConfig.createProducerProperties(
            brokerIP, lingerMS, batchSize, maxRequestTimeout, maxRetries,
            isAuthenticationEnabled, username, password
        );

        if (kafkaProps == null) {
            loggerMaker.errorAndAddToDb("Failed to create Kafka producer properties");
            return;
        }

        kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        producer = new KafkaProducer<String, String>(kafkaProps);

        // test if connection successful by sending a test message in a blocking way
        // calling .get() blocks the thread till we receive a message
        // if any error then close the connection
        ProducerRecord<String, String> record = new ProducerRecord<>("akto.misc", "ping");
        try {
            producer.send(record).get();
            producerReady = true;
        } catch (Exception e) {
            close();
            loggerMaker.errorAndAddToDb(e, "Kafka producer initialization failed with unexpected error: ");
        }
    }

    private class DemoProducerCallback implements Callback {
        @Override
        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
            if (e != null) {
                Kafka.this.close();
                logger.error("onCompletion error: " + e.getMessage());
            }
        }
    }

    public static void deleteKafkaTopic(String topicName, String kafkaBrokerUrl) {
        try {
            loggerMaker.infoAndAddToDb("Attempting to delete Kafka topic: " + topicName);

            // Use centralized method to create admin properties
            Properties adminProps = KafkaConfig.createAdminProperties(
                kafkaBrokerUrl,
                KafkaConfig.isKafkaAuthenticationEnabled(),
                KafkaConfig.getKafkaUsername(),
                KafkaConfig.getKafkaPassword()
            );

            if (adminProps == null) {
                loggerMaker.warnAndAddToDb("Failed to create Kafka admin properties, skipping topic deletion");
                return;
            }

            try (AdminClient adminClient = AdminClient.create(adminProps)) {
                DeleteTopicsResult result = adminClient.deleteTopics(Collections.singletonList(topicName));
                result.all().get(30, TimeUnit.SECONDS);
                loggerMaker.infoAndAddToDb("Successfully deleted Kafka topic: " + topicName);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error deleting Kafka topic: " + topicName + " - " + e.getMessage());
        }
    }

}


