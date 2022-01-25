package com.akto.kafka;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

public class Kafka {
    public static final int BATCH_SIZE_CONFIG = 999900;
    private final KafkaProducer<String, String> producer;
    public Kafka(String brokerIP) {
        Properties kafkaProps = new Properties();
        kafkaProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, brokerIP);
        kafkaProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaProps.put(ProducerConfig.BATCH_SIZE_CONFIG, BATCH_SIZE_CONFIG);
        kafkaProps.put(ProducerConfig.LINGER_MS_CONFIG, 1000);
        kafkaProps.put(ProducerConfig.RETRIES_CONFIG, 5);
        producer = new KafkaProducer<String, String>(kafkaProps);
    }

    public void send(String message,String topic) {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic,message);
        try {
            producer.send(record, new DemoProducerCallback());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class DemoProducerCallback implements Callback {
        @Override
        public void onCompletion(RecordMetadata recordMetadata, Exception e) {
            if (e != null) {
                e.printStackTrace();
            }
        }
    }

}


