package com.akto.kafka;

import com.akto.log.LoggerMaker;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.akto.log.LoggerMaker.LogDb;

import java.util.Properties;
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
          loggerMaker.insertImportantTestingLog("Producer not ready. Cannot send message. Counter will remain incremented. Current counter: " + counter.get());
          return;
        };
    
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
        try {
            producer.send(record, (recordMetadata, e) -> {
                if (e != null) {
                    loggerMaker.insertImportantTestingLog("Failed to send message to Kafka. Error: " + e.getMessage()+ " for message " +message+ ". Counter will remain incremented. Current counter: " + counter.get());
                } else {
                    logger.info(message + " sent to topic " + topic + " with offset " + recordMetadata.offset());
                    counter.decrementAndGet();
                }
            });
        } catch (Exception sendException) {
            loggerMaker.insertImportantTestingLog("Exception occurred while sending message to Kafka: " + sendException.getMessage() + ". Counter will remain incremented. Current counter: " + counter.get());
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

        Properties kafkaProps = new Properties();
        kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerIP);
        kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMS);
        kafkaProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        kafkaProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, maxRequestTimeout);
        kafkaProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, lingerMS + maxRequestTimeout);

        if(maxRetries > 0){
            kafkaProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
        }

        // Add authentication if username and password are provided
        if (isAuthenticationEnabled) {
            if(StringUtils.isEmpty(username) || StringUtils.isEmpty(password)){
                logger.error("Kafka authentication credentials not provided");
                return;
            }
            kafkaProps.put("security.protocol", "SASL_PLAINTEXT");
            kafkaProps.put("sasl.mechanism", "PLAIN");
            String jaasConfig = String.format(
                "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"%s\" password=\"%s\";",
                username, password
            );
            kafkaProps.put("sasl.jaas.config", jaasConfig);
        }

        producer = new KafkaProducer<String, String>(kafkaProps);

        // test if connection successful by sending a test message in a blocking way
        // calling .get() blocks the thread till we receive a message
        // if any error then close the connection
        ProducerRecord<String, String> record = new ProducerRecord<>("akto.misc", "ping");
        try {
            producer.send(record).get();
            producerReady = true;
        } catch (Exception ignored) {
            close();
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

}


