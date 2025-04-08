package com.akto.kafka;

import org.apache.kafka.clients.producer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class Kafka {
  private static final Logger logger = LoggerFactory.getLogger(Kafka.class);
  private KafkaProducer<String, String> producer;
  public boolean producerReady;

  public Kafka(KafkaConfig kafkaConfig) {
    this(
        kafkaConfig.getBootstrapServers(),
        kafkaConfig.getProducerConfig().getLingerMs(),
        kafkaConfig.getProducerConfig().getBatchSize(),
        kafkaConfig.getKeySerializer(),
        kafkaConfig.getValueSerializer());
  }

  public Kafka(
      String brokerIP,
      int lingerMS,
      int batchSize,
      Serializer keySerializer,
      Serializer valueSerializer) {
    producerReady = false;
    try {
      setProducer(brokerIP, lingerMS, batchSize, keySerializer, valueSerializer, 5000, 0);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Kafka(
      String brokerIP,
      int lingerMS,
      int batchSize,
      Serializer keySerializer,
      Serializer valueSerializer,
      int requestTimeout,
      int retriesConfig) {
    producerReady = false;
    try {
      setProducer(brokerIP, lingerMS, batchSize, keySerializer, valueSerializer, requestTimeout, retriesConfig);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public Kafka(String brokerIP, int lingerMS, int batchSize) {
    this(brokerIP, lingerMS, batchSize, Serializer.STRING, Serializer.STRING);
  }

  public Kafka(String brokerIP, int lingerMS, int batchSize, int maxRequestTimeout, int retriesConfig) {
    this(brokerIP, lingerMS, batchSize, Serializer.STRING, Serializer.STRING, maxRequestTimeout, retriesConfig);
  }

  public void sendWithCounter(String message, String topic, AtomicInteger counter) {
    if (!this.producerReady) {
      logger.error("Producer not ready. Cannot send message.");
      return;
    };

    ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
    producer.send(record, (recordMetadata, e) -> {
      if (e != null) {
        logger.error("onCompletion error: " + e.getMessage());
      } else {
        // decrement the counter if message sent successfully
        counter.decrementAndGet();
      }
    });
  }

  public void send(String message, String topic) {
    if (!this.producerReady) {
      logger.error("Producer not ready. Cannot send message.");
      return;
    };

    ProducerRecord<String, String> record = new ProducerRecord<>(topic, message);
    producer.send(record, new DemoProducerCallback());
  }

  public void close() {
    // this.producerReady = false;
    // producer.close(Duration.ofMillis(0)); // close immediately
  }

  private void setProducer(
      String brokerIP,
      int lingerMS,
      int batchSize,
      Serializer keySerializer,
      Serializer valueSerializer,
      int maxRequestTimeout,
      int retriesConfig
      ) {
    if (producer != null) close(); // close existing producer connection

    Properties kafkaProps = new Properties();
    kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerIP);
    kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, valueSerializer.getSerializer());
    kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, keySerializer.getSerializer());
    kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
    kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMS);
    kafkaProps.put(ProducerConfig.RETRIES_CONFIG, retriesConfig);
    kafkaProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, maxRequestTimeout);
    kafkaProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, lingerMS + maxRequestTimeout);
    if(retriesConfig > 0){
      kafkaProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
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
