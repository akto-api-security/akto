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

              if (!records.isEmpty() && shouldCommitAfterProcessing()) {
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

  /**
   * Whether run() should commit the batch it just handed to processRecords().
   *
   * <p>Default true: commit whatever was polled, which is correct for tasks that
   * cannot meaningfully retry — losing a record costs a detection, and blocking
   * the loop would cost every later one.
   *
   * <p>A task whose downstream can be temporarily unavailable overrides this to
   * false and takes ownership of committing: it commits the offsets it actually
   * delivered and seeks back to the first it did not, so an outage delays
   * records instead of dropping them.
   *
   * <p>Called on the polling thread immediately after processRecords() returns,
   * so an implementation may base its answer on what that call just did.
   */
  protected boolean shouldCommitAfterProcessing() {
    return true;
  }

  abstract void processRecords(ConsumerRecords<String, V> records);

  public Consumer<String, V> getKafkaConsumer() {
    return kafkaConsumer;
  }
}
