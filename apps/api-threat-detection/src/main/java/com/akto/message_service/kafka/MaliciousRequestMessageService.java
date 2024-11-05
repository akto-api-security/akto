package com.akto.message_service.kafka;

import com.akto.malicious_request.MaliciousRequest;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;

import java.util.List;
import java.util.Properties;

// Responsible for pushing malicious requests to the Kafka topic.
public class MaliciousRequestMessageService {

    private static final String KAFKA_TOPIC = "malicious_requests";
    private static final int LINGER_MS = 1000; // 1 second
    private static final int BATCH_SIZE = 1024 * 32; // 32 KB
    private static final int BUFFER_MEMORY = 1024 * 1024 * 32; // 32 MB

    private final Producer<String, String> producer;

    public MaliciousRequestMessageService(Properties kafkaProperties) {
        Properties props = (Properties) kafkaProperties.clone();
        props.put("linger.ms", LINGER_MS);
        props.put("batch.size", BATCH_SIZE);
        props.put("buffer.memory", BUFFER_MEMORY);

        this.producer = new KafkaProducer<>(props);
    }

    // Push the message to the Kafka topic in batches.
    public void pushMessage(MaliciousRequest maliciousRequest) {
        Message m = new Message(maliciousRequest);
        m.serialize()
                .ifPresent(
                        message ->
                                producer.send(
                                        new org.apache.kafka.clients.producer.ProducerRecord<>(
                                                KAFKA_TOPIC, message)));
    }

    public void pushMessages(List<MaliciousRequest> maliciousRequests) {
        maliciousRequests.forEach(this::pushMessage);
    }

    public void close() {
        producer.close();
    }
}
