package com.akto.kafka;

import org.apache.kafka.clients.producer.*;

import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class Kafka {
  /**
   * Producer max.request.size when AKTO_KAFKA_MAX_REQUEST_SIZE is unset: 5MB, well
   * above Kafka's own 1MB default. Kept in step with the broker's message.max.bytes
   * and the ingest API's Struts maxLength — a producer allowed to send more than the
   * broker accepts just moves the rejection one hop later, where it is harder to see.
   */
  private static final String DEFAULT_MAX_REQUEST_SIZE = "5242880";

  private static LoggerMaker logger = new LoggerMaker(Kafka.class, LogDb.TESTING);
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
      logger.errorAndAddToDb("Error while creating producer: " + e.getMessage());
      e.printStackTrace();
    }
  }

  public Kafka(
      String brokerIP,
      int lingerMS,
      int batchSize,
      Serializer keySerializer,
      Serializer valueSerializer,
      LogDb logDb) {
    producerReady = false;
    try {
      logger = new LoggerMaker(Kafka.class, logDb);
      setProducer(brokerIP, lingerMS, batchSize, keySerializer, valueSerializer, 5000, 0);
    } catch (Exception e) {
      logger.errorAndAddToDb("Error while creating producer: " + e.getMessage());
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

  public Kafka(String brokerIP, int lingerMS, int batchSize, LogDb logDb) {
    this(brokerIP, lingerMS, batchSize, Serializer.STRING, Serializer.STRING, logDb);
  }

  public Kafka(String brokerIP, int lingerMS, int batchSize, String username, String password, LogDb logDb) {
    // Default to PLAIN mechanism for backward compatibility
    this(brokerIP, lingerMS, batchSize, username, password, "PLAIN", logDb);
  }

  public Kafka(String brokerIP, int lingerMS, int batchSize, String username, String password, String saslMechanism, LogDb logDb) {
    producerReady = false;
    try {
      logger = new LoggerMaker(Kafka.class, logDb);
      setProducer(brokerIP, lingerMS, batchSize, Serializer.STRING, Serializer.STRING, 5000, 0, username, password, saslMechanism);
    } catch (Exception e) {
      logger.errorAndAddToDb("Error while creating producer: " + e.getMessage());
      e.printStackTrace();
    }
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
        logger.debug(message + " sent to topic " + topic + " with offset " + recordMetadata.offset());
        // decrement the counter if message sent successfully
        counter.decrementAndGet();
      }
    });
  }

  public void send(String message, String topic) {
    if (!this.producerReady) {
      logger.errorAndAddToDb("Producer not ready. Cannot send message.");
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
    setProducer(brokerIP, lingerMS, batchSize, keySerializer, valueSerializer, maxRequestTimeout, retriesConfig, null, null, null);
  }

  private void setProducer(
      String brokerIP,
      int lingerMS,
      int batchSize,
      Serializer keySerializer,
      Serializer valueSerializer,
      int maxRequestTimeout,
      int retriesConfig,
      String username,
      String password,
      String saslMechanism
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
    kafkaProps.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

    if(retriesConfig > 0){
      kafkaProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);
    }

    /*
     * Kafka's own max.request.size default is 1MB, and it refuses anything larger at
     * send() with "The message is N bytes when serialized which is larger than
     * max.request.size" — asynchronously, so the caller has already been told the
     * request succeeded and the record is silently lost. Mirrored AI traffic carries
     * whole conversations, so a single record routinely exceeds 1MB.
     *
     * Must stay <= the broker's message.max.bytes and the topic's max.message.bytes,
     * or the record is accepted here and then rejected on the broker. Note Kafka adds
     * ~100 bytes of per-record overhead (topic, key, batch framing), so the usable
     * payload is slightly below this number.
     */
    String maxRequestSize = System.getenv().getOrDefault("AKTO_KAFKA_MAX_REQUEST_SIZE", DEFAULT_MAX_REQUEST_SIZE);
    try {
      kafkaProps.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, Integer.parseInt(maxRequestSize.trim()));
    } catch (NumberFormatException e) {
      logger.error("AKTO_KAFKA_MAX_REQUEST_SIZE is not a number: '" + maxRequestSize
          + "', falling back to " + DEFAULT_MAX_REQUEST_SIZE + " bytes");
      kafkaProps.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, Integer.parseInt(DEFAULT_MAX_REQUEST_SIZE));
    }

    // Add SASL authentication if username and password are provided
    if (username != null && !username.isEmpty() && password != null && !password.isEmpty()) {
      // Read security protocol from environment, default to SASL_PLAINTEXT
      String securityProtocol = System.getenv().getOrDefault("AKTO_KAFKA_SECURITY_PROTOCOL", KafkaConfig.SECURITY_PROTOCOL_SASL_PLAINTEXT);

      if (saslMechanism != null && !saslMechanism.isEmpty()) {
        KafkaConfig.addAuthenticationProperties(kafkaProps, username, password, saslMechanism, securityProtocol);
      } else {
        KafkaConfig.addAuthenticationProperties(kafkaProps, username, password, "PLAIN", securityProtocol);
      }
    }

    try {
      producer = new KafkaProducer<String, String>(kafkaProps);
    } catch (Exception e) {
      logger.errorAndAddToDb("Error while creating kafka producer: " + e.getMessage());
      return;
    }

    // test if connection successful by sending a test message in a blocking way
    // calling .get() blocks the thread till we receive a message
    // if any error then close the connection
    ProducerRecord<String, String> record = new ProducerRecord<>("akto.misc", "ping");
    try {
      producer.send(record).get();
      producerReady = true;
    } catch (Exception e) {
      logger.error("Producer not ready. Cannot send message. Cause: " + e.getClass().getName() + ": " + e.getMessage());
      close();
    }
  }

  private class DemoProducerCallback implements Callback {
    @Override
    public void onCompletion(RecordMetadata recordMetadata, Exception e) {
      if (e != null) {
        Kafka.this.close();
        logger.errorAndAddToDb("onCompletion error: " + e.getMessage(), LogDb.DATA_INGESTION);
      }
    }
  }
}