package com.akto.message_service.kafka;

import com.akto.malicious_request.MaliciousRequest;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

// Represents a message that will be pushed to the Kafka topic.
// Adding this wrapper, since it helps in future if we want to add more meta fields.
public class Message {
    private final MaliciousRequest maliciousRequest;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public Message(MaliciousRequest maliciousRequest) {
        this.maliciousRequest = maliciousRequest;
    }

    public MaliciousRequest getMaliciousRequest() {
        return maliciousRequest;
    }

    public Optional<String> serialize() {
        return Message.serialize(this);
    }

    public static Optional<String> serialize(Message message) {
        try {
            return Optional.of(objectMapper.writeValueAsString(message));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<Message> deserialize(String message) {
        try {
            return Optional.of(objectMapper.readValue(message, Message.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
