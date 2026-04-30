package com.akto.threat.detection.tasks;

import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

public abstract class AbstractKafkaConsumerTask<V> implements Task {

  private static final LoggerMaker logger = new LoggerMaker(AbstractKafkaConsumerTask.class, LogDb.THREAT_DETECTION);

  protected Consumer<String, V> kafkaConsumer;
  protected KafkaConfig kafkaConfig;
  protected String kafkaTopic;
  protected String instanceId;

  private int recordsReadCount = 0;
  private long lastRecordCountLogTime = System.currentTimeMillis();

  public AbstractKafkaConsumerTask(KafkaConfig kafkaConfig, String kafkaTopic, String instanceId) {
    this.kafkaTopic = kafkaTopic;
    this.kafkaConfig = kafkaConfig;
    this.instanceId = instanceId;
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
              logRecordsPerMin(records.count());
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

  private void logRecordsPerMin(int count) {
    recordsReadCount += count;
    long currentTime = System.currentTimeMillis();
    long timeDiff = currentTime - lastRecordCountLogTime;
    if (timeDiff >= 60000) {
      logger.warnAndAddToDb(instanceId + ": Kafka records read in last minute: " + recordsReadCount +
          " (avg " + String.format("%.2f", recordsReadCount / (timeDiff / 1000.0)) + " records/sec)");
      logger.warnAndAddToDb(instanceId + ": Assigned partitions: " + kafkaConsumer.assignment());
      logger.warnAndAddToDb(instanceId + ": Subscription: " + kafkaConsumer.subscription());
      if (kafkaConsumer.assignment().isEmpty()) {
        logger.warnAndAddToDb(instanceId + ": WARNING - No partitions assigned! Consumer may not receive records.");
      }
      recordsReadCount = 0;
      lastRecordCountLogTime = currentTime;
    }
  }

  protected void beforePollLoop() {}

  abstract void processRecords(ConsumerRecords<String, V> records);
}
