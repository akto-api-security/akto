package com.akto;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import com.akto.agent_risk.AgentRiskApiCollectionWriter;
import com.akto.agent_risk.AgentRiskScore;
import com.akto.agent_risk.AgentRiskScorer;
import com.akto.dao.context.Context;
import com.akto.kafka.AgentRiskKafkaProducer;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.utils.elasticsearch.AgentQueryRecord;
import com.akto.utils.elasticsearch.ElasticSearchClient;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;

/**
 * Consumes akto.agent.risk. Scores locally (hash cache → optional Go sidecar → dummy rules)
 * and persists to ES. Started only when AGENT_RISK_KAFKA_CONSUMER_ENABLED=true.
 * Do not log per record — infoAndAddToDb writes Mongo.
 */
public class AgentRiskKafkaConsumer implements Runnable {

    private static final LoggerMaker loggerMaker = new LoggerMaker(AgentRiskKafkaConsumer.class, LogDb.DB_ABS);
    private static final Gson gson = new Gson();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final TypeReference<List<AgentQueryRecord>> AGENT_QUERY_RECORDS =
            new TypeReference<List<AgentQueryRecord>>() {};

    private final KafkaConsumer<String, String> consumer;
    private final AtomicBoolean running;
    private final String topicName;
    private long totalProcessed = 0;

    public AgentRiskKafkaConsumer(String brokerUrl, String topicName, String groupId, int maxPollRecords) {
        this.topicName = topicName;
        this.running = new AtomicBoolean(true);

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecords);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        this.consumer = new KafkaConsumer<>(props);
        loggerMaker.infoAndAddToDb("AgentRiskKafkaConsumer initialized: topic=" + topicName +
                ", group=" + groupId + ", broker=" + brokerUrl, LogDb.DB_ABS);
    }

    public static void startFromEnv() {
        AgentRiskKafkaConsumer c = new AgentRiskKafkaConsumer(
                AgentRiskKafkaProducer.getRiskBrokerUrl(),
                AgentRiskKafkaProducer.getRiskTopicName(),
                AgentRiskKafkaProducer.getRiskConsumerGroupId(),
                AgentRiskKafkaProducer.getRiskMaxPollRecords()
        );
        c.start();
    }

    public void start() {
        consumer.subscribe(Collections.singletonList(topicName));
        Thread consumerThread = new Thread(this, "agent-risk-consumer");
        consumerThread.setDaemon(false);
        consumerThread.start();
        loggerMaker.infoAndAddToDb("AgentRiskKafkaConsumer subscribed to topic: " + topicName, LogDb.DB_ABS);
    }

    @Override
    public void run() {
        try {
            while (running.get()) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));
                    if (records.isEmpty()) {
                        continue;
                    }
                    List<AgentRiskScore> scored = new ArrayList<>();
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            processMessage(record.value(), scored);
                        } catch (Exception e) {
                            // keep going; do not log every record to Mongo
                        }
                    }
                    if (!scored.isEmpty()) {
                        ElasticSearchClient.instance().bulkIndexAgentRiskScores(scored);
                        // ElasticSearchClient.instance().markAgentQueryTopicProcessed(scored);
                        AgentRiskApiCollectionWriter.persist(scored);
                    }
                    try {
                        consumer.commitSync();
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error committing agent-risk offset: " + e.getMessage(), LogDb.DB_ABS);
                    }
                    totalProcessed += records.count();
                    if (totalProcessed % 10_000 == 0) {
                        loggerMaker.infoAndAddToDb("AgentRiskKafkaConsumer: Total processed: " + totalProcessed, LogDb.DB_ABS);
                    }
                } catch (org.apache.kafka.common.errors.WakeupException e) {
                    break;
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in agent-risk consumer loop: " + e.getMessage(), LogDb.DB_ABS);
                }
            }
        } finally {
            try {
                consumer.close();
                loggerMaker.infoAndAddToDb("AgentRiskKafkaConsumer stopped. Total processed: " + totalProcessed, LogDb.DB_ABS);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error closing agent-risk consumer: " + e.getMessage(), LogDb.DB_ABS);
            }
        }
    }

    private void processMessage(String message, List<AgentRiskScore> scored) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> json = gson.fromJson(message, Map.class);
        if (json == null) {
            return;
        }
        String triggerMethod = (String) json.get("triggerMethod");
        if (!"scoreAgentQueryRecords".equals(triggerMethod)) {
            return;
        }
        String payload = (String) json.get("payload");
        Double accIdDouble = (Double) json.get("accountId");
        int accountId = accIdDouble == null ? 0 : accIdDouble.intValue();
        Context.accountId.set(accountId);

        List<AgentQueryRecord> records = mapper.readValue(payload, AGENT_QUERY_RECORDS);
        if (records == null) {
            return;
        }
        for (AgentQueryRecord r : records) {
            AgentRiskScore score = AgentRiskScorer.instance().score(r, accountId);
            if (score != null) {
                scored.add(score);
            }
        }
    }

    public void shutdown() {
        running.set(false);
        consumer.wakeup();
    }
}
