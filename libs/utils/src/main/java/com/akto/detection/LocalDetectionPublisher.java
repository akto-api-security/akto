package com.akto.detection;

import com.akto.log.LoggerMaker;
import com.google.gson.Gson;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

/**
 * Publishes locally-detected values to Kafka for async refinement by the external classifier.
 *
 * Sends to topic: akto.detection-candidates
 * Message: { "apiCollectionId": 1, "url": "/api/user", "method": "POST", "param": "email",
 *            "value": "alice@example.com", "type": "EMAIL" }
 *
 * The external classifier (running in customer's VPC with access to internal services)
 * subscribes, refines the type, and publishes verdicts back.
 */
public class LocalDetectionPublisher implements CandidatePublisher {

    private static final LoggerMaker loggerMaker = new LoggerMaker(LocalDetectionPublisher.class);
    private static final Gson gson = new Gson();

    private final KafkaProducer<String, String> producer;
    private final String topic;

    public LocalDetectionPublisher(String brokerUrl, String topic) {
        this.topic = topic;
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerUrl);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy");
        this.producer = new KafkaProducer<>(props);
        loggerMaker.info("[local-detection-publisher] ready on topic " + topic + " brokers " + brokerUrl);
    }

    public void publish(DetectionCandidate candidate) {
        try {
            String msg = gson.toJson(candidate);
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, candidate.getParam(), msg);
            producer.send(record, (metadata, exception) -> {
                if (exception != null) {
                    loggerMaker.error("[local-detection-publisher] failed publishing " + candidate.getParam()
                            + " value=" + candidate.getValue() + ": " + exception.getMessage());
                }
            });
        } catch (Exception e) {
            loggerMaker.error("[local-detection-publisher] error publishing: " + e.getMessage());
        }
    }

    public void close() {
        producer.close();
        loggerMaker.info("[local-detection-publisher] closed");
    }
}
