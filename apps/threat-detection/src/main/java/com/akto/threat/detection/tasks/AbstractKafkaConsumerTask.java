package com.akto.threat.detection.tasks;

import com.akto.kafka.KafkaConfig;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public abstract class AbstractKafkaConsumerTask<V> implements Task {

  protected Consumer<String, V> kafkaConsumer;
  protected KafkaConfig kafkaConfig;
  protected String kafkaTopic;

  public AbstractKafkaConsumerTask(KafkaConfig kafkaConfig, String kafkaTopic) {
    this.kafkaTopic = kafkaTopic;
    this.kafkaConfig = kafkaConfig;
    this.kafkaConsumer = new KafkaConsumer<>(kafkaConfig.toConsumerProperties());
  }

  @Override
  public void run() {
    this.kafkaConsumer.subscribe(Collections.singletonList(this.kafkaTopic));

    ExecutorService pollingExecutor = Executors.newSingleThreadExecutor();

    pollingExecutor.execute(
        () -> {
          beforePollLoop();
          while (true) {
            ConsumerRecords<String, V> records =
                kafkaConsumer.poll(
                    Duration.ofMillis(kafkaConfig.getConsumerConfig().getPollDurationMilli()));

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

  protected void beforePollLoop() {}

  abstract void processRecords(ConsumerRecords<String, V> records);
}
