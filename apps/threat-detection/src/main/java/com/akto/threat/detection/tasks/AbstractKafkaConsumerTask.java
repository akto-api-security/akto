package com.akto.threat.detection.tasks;

import com.akto.kafka.KafkaConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;

public abstract class AbstractKafkaConsumerTask<V> implements Task {

  protected Consumer<String, V> kafkaConsumer;
  protected KafkaConfig kafkaConfig;
  protected String kafkaTopic;

  public AbstractKafkaConsumerTask(KafkaConfig kafkaConfig, String kafkaTopic) {
    this.kafkaTopic = kafkaTopic;
    this.kafkaConfig = kafkaConfig;

    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConfig.getBootstrapServers());
    properties.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        kafkaConfig.getValueSerializer().getDeserializer());
    properties.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        kafkaConfig.getValueSerializer().getDeserializer());
    properties.put(
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
        kafkaConfig.getConsumerConfig().getMaxPollRecords());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, kafkaConfig.getGroupId());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    this.kafkaConsumer = new KafkaConsumer<>(properties);
  }

  @Override
  public void run() {
    this.kafkaConsumer.subscribe(Collections.singletonList(this.kafkaTopic));

    ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();

    pollingExecutor.execute(
        () -> {
          // Poll data from Kafka topic
          while (true) {
            ConsumerRecords<String, V> records =
                kafkaConsumer.poll(
                    Duration.ofMillis(kafkaConfig.getConsumerConfig().getPollDurationMilli()));
            if (records.isEmpty()) {
              continue;
            }

            try {
              processRecords(records);

              if (!records.isEmpty()) {
                kafkaConsumer.commitSync();
              }
            } catch (Exception ex) {
              ex.printStackTrace();
            }
          }
        });
  }

  abstract void processRecords(ConsumerRecords<String, V> records);
}
