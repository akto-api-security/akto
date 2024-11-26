package com.akto.threat.detection.tasks;

import com.akto.runtime.utils.Utils;
import com.akto.threat.detection.config.kafka.KafkaConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

public abstract class AbstractKafkaConsumerTask implements Task {

  protected Consumer<String, String> kafkaConsumer;
  protected KafkaConfig kafkaConfig;
  protected String kafkaTopic;

  public AbstractKafkaConsumerTask(KafkaConfig kafkaConfig, String kafkaTopic) {
    this.kafkaTopic = kafkaTopic;

    String kafkaBrokerUrl = kafkaConfig.getBootstrapServers();
    String groupId = kafkaConfig.getGroupId();

    Properties properties =
        Utils.configProperties(
            kafkaBrokerUrl, groupId, kafkaConfig.getConsumerConfig().getMaxPollRecords());
    this.kafkaConsumer = new KafkaConsumer<>(properties);
  }

  abstract ExecutorService getPollingExecutor();

  @Override
  public void run() {
    this.kafkaConsumer.subscribe(Collections.singletonList(this.kafkaTopic));

    this.getPollingExecutor()
        .execute(
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
