package com.akto.threat.detection.kafka;

import com.akto.kafka.KafkaConfig;
import com.google.protobuf.Message;
import java.time.Duration;
import java.util.Properties;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

public class KafkaProtoProducer {
  private KafkaProducer<String, byte[]> producer;
  public boolean producerReady;

  public KafkaProtoProducer(KafkaConfig kafkaConfig) {
    this.producer = generateProducer(
        kafkaConfig.getBootstrapServers(),
        kafkaConfig.getProducerConfig().getLingerMs(),
        kafkaConfig.getProducerConfig().getBatchSize());
  }

  public void send(String topic, Message message) {
    byte[] messageBytes = message.toByteArray();
    this.producer.send(new ProducerRecord<>(topic, messageBytes));
  }

  public void close() {
    this.producerReady = false;
    producer.close(Duration.ofMillis(0)); // close immediately
  }

  private KafkaProducer<String, byte[]> generateProducer(String brokerIP, int lingerMS, int batchSize) {
    if (producer != null)
      close(); // close existing producer connection

    int requestTimeoutMs = 5000;
    Properties kafkaProps = new Properties();
    kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerIP);
    kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.ByteArraySerializer");
    kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
    kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, lingerMS);
    kafkaProps.put(ProducerConfig.RETRIES_CONFIG, 0);
    kafkaProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, requestTimeoutMs);
    kafkaProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, lingerMS + requestTimeoutMs);
    return new KafkaProducer<String, byte[]>(kafkaProps);
  }
}
