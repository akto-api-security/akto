package com.akto.threat.detection.kafka;

import com.akto.kafka.KafkaConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewPartitions;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;

/**
 * Drops all data in a topic by deleting it and recreating it with the same partition count,
 * replication factor and topic-level config overrides.
 *
 * <p>Recreating explicitly matters: otherwise the topic comes back via broker auto-create on the
 * next produce/subscribe, with the broker's default partition count (often 1) and no overrides.
 */
public class KafkaTopicResetter {

  private static final LoggerMaker logger = new LoggerMaker(KafkaTopicResetter.class, LogDb.THREAT_DETECTION);
  private static final long TIMEOUT_SECONDS = 30;
  private static final long DELETE_WAIT_MS = 60_000;

  public static void resetTopic(String bootstrapServers, String topic) {
    if (bootstrapServers == null || bootstrapServers.isEmpty()) {
      logger.errorAndAddToDb("Skipping reset of topic " + topic + ": no bootstrap servers configured");
      return;
    }

    Properties props = new Properties();
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    KafkaConfig.addAuthenticationFromEnv(props);

    try (AdminClient admin = AdminClient.create(props)) {
      TopicDescription original = describe(admin, topic);
      if (original == null) {
        logger.warnAndAddToDb("Topic " + topic + " does not exist, nothing to reset");
        return;
      }
      int partitions = original.partitions().size();
      short replicationFactor = (short) original.partitions().get(0).replicas().size();
      Map<String, String> overrides = topicConfigOverrides(admin, topic);

      admin.deleteTopics(Collections.singleton(topic)).all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

      // Deletion is asynchronous, and producers/consumers still running (here or in other pods)
      // can auto-create the topic the moment it is gone. The topic id tells the old topic apart
      // from a new one.
      long deadline = System.currentTimeMillis() + DELETE_WAIT_MS;
      while (true) {
        TopicDescription current = describe(admin, topic);
        if (current == null) {
          try {
            admin.createTopics(Collections.singleton(
                    new NewTopic(topic, partitions, replicationFactor).configs(overrides)))
                .all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            logger.warnAndAddToDb("Reset topic " + topic + " on " + bootstrapServers + " with partitions=" + partitions
                + " replicationFactor=" + replicationFactor + " configs=" + overrides);
            return;
          } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
              throw e;
            }
            // auto-created or still marked for deletion; look again below
          }
        } else if (!current.topicId().equals(original.topicId())) {
          if (current.partitions().size() < partitions) {
            admin.createPartitions(Collections.singletonMap(topic, NewPartitions.increaseTo(partitions)))
                .all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
          }
          if (!overrides.isEmpty()) {
            List<AlterConfigOp> ops = new ArrayList<>();
            for (Map.Entry<String, String> o : overrides.entrySet()) {
              ops.add(new AlterConfigOp(new ConfigEntry(o.getKey(), o.getValue()), AlterConfigOp.OpType.SET));
            }
            admin.incrementalAlterConfigs(Collections.singletonMap(
                    new ConfigResource(ConfigResource.Type.TOPIC, topic), ops))
                .all().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
          }
          logger.warnAndAddToDb("Reset topic " + topic + " on " + bootstrapServers + " (auto-created by a client before"
              + " reset finished) restored to partitions>=" + partitions + " configs=" + overrides);
          return;
        }
        if (System.currentTimeMillis() > deadline) {
          throw new IllegalStateException("Topic " + topic + " still present " + DELETE_WAIT_MS + "ms after delete");
        }
        Thread.sleep(500);
      }
    } catch (Exception e) {
      logger.errorAndAddToDb(e, "Error resetting topic " + topic + " on " + bootstrapServers + ": " + e.getMessage());
    }
  }

  private static TopicDescription describe(AdminClient admin, String topic) throws Exception {
    try {
      return admin.describeTopics(Collections.singleton(topic)).allTopicNames()
          .get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get(topic);
    } catch (ExecutionException e) {
      if (e.getCause() instanceof UnknownTopicOrPartitionException) {
        return null;
      }
      throw e;
    }
  }

  private static Map<String, String> topicConfigOverrides(AdminClient admin, String topic) throws Exception {
    ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topic);
    Config config = admin.describeConfigs(Collections.singleton(resource)).all()
        .get(TIMEOUT_SECONDS, TimeUnit.SECONDS).get(resource);
    Map<String, String> overrides = new HashMap<>();
    for (ConfigEntry entry : config.entries()) {
      if (entry.source() == ConfigEntry.ConfigSource.DYNAMIC_TOPIC_CONFIG) {
        overrides.put(entry.name(), entry.value());
      }
    }
    return overrides;
  }
}
