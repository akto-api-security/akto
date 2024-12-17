package com.akto.threat.detection.tasks;

import com.akto.kafka.KafkaConfig;
import com.akto.runtime.utils.Utils;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public abstract class AbstractKafkaConsumerTask implements Task {

  protected Consumer<String, String> kafkaConsumer;
  protected KafkaConfig kafkaConfig;
  protected String kafkaTopic;

  public AbstractKafkaConsumerTask(KafkaConfig kafkaConfig, String kafkaTopic) {
    this.kafkaTopic = kafkaTopic;
    this.kafkaConfig = kafkaConfig;

    String kafkaBrokerUrl = kafkaConfig.getBootstrapServers();
    String groupId = kafkaConfig.getGroupId();

    Properties properties =
        Utils.configProperties(
            kafkaBrokerUrl, groupId, kafkaConfig.getConsumerConfig().getMaxPollRecords());
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
            ConsumerRecords<String, String> records =
                kafkaConsumer.poll(
                    Duration.ofMillis(kafkaConfig.getConsumerConfig().getPollDurationMilli()));
            if (records.isEmpty()) {
              continue;
            }

            processRecords(records);
          }
        });
  }

  abstract void processRecords(ConsumerRecords<String, String> records);
}
