package com.akto.runtime.parser;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;
import org.checkerframework.checker.index.qual.LTOMLengthOf;

import com.mongodb.BasicDBObject;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;



public class KafkaProducerExample {
    // Kafka configuration
    private static final String BOOTSTRAP_SERVERS = "localhost:29092";
    private static final String TOPIC = "akto.api.logs";

    @lombok.Getter
    @lombok.Setter
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    private static class IngestDataBatch {

        String path;
        String requestHeaders;
        String responseHeaders;
        String method;
        String requestPayload;
        String responsePayload;
        String ip;
        String time;
        String statusCode;
        String type;
        String status;
        String akto_account_id;
        String akto_vxlan_id;
        String is_pending;
        String source;
        String tag;

        public IngestDataBatch(String path, String requestHeaders, String responseHeaders, String method,
                              String requestPayload, String responsePayload, String ip, String time,
                              String statusCode, String type, String status, String akto_account_id,
                              String akto_vxlan_id, String is_pending, String source) {
            this.path = path;
            this.requestHeaders = requestHeaders;
            this.responseHeaders = responseHeaders;
            this.method = method;
            this.requestPayload = requestPayload;
            this.responsePayload = responsePayload;
            this.ip = ip;
            this.time = time;
            this.statusCode = statusCode;
            this.type = type;
            this.status = status;
            this.akto_account_id = akto_account_id;
            this.akto_vxlan_id = akto_vxlan_id;
            this.is_pending = is_pending;
            this.source = source;
        }

    }

    public static String insertData(IngestDataBatch payload) {
        BasicDBObject obj = new BasicDBObject();
        obj.put("path", payload.getPath());
        obj.put("requestHeaders", payload.getRequestHeaders());
        obj.put("responseHeaders", payload.getResponseHeaders());
        obj.put("method", payload.getMethod());
        obj.put("requestPayload", payload.getRequestPayload());
        obj.put("responsePayload", payload.getResponsePayload());
        obj.put("ip", payload.getIp());
        obj.put("time", payload.getTime());
        obj.put("statusCode", payload.getStatusCode());
        obj.put("type", payload.getType());
        obj.put("status", payload.getStatus());
        obj.put("akto_account_id", payload.getAkto_account_id());
        obj.put("akto_vxlan_id", payload.getAkto_vxlan_id());
        obj.put("is_pending", payload.getIs_pending());
        obj.put("source", payload.getSource());

        
        if(payload.getTag() != null && !payload.getTag().isEmpty()) {
            obj.put("tag", payload.getTag());
        }
        return obj.toString();
    }

    public static String escapeJson(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.writeValueAsString(objectMapper.readTree(json));
        } catch (Exception e) {
            throw new RuntimeException("Error escaping JSON: " + e.getMessage(), e);
        }
    }


    public static List<ProducerRecord<String, String>> demoRecordGenerator() {
        List<String> hosts = new ArrayList<>();
        for (int i = 6; i <= 10; i++) {
            hosts.add("host" + i + ".akto.notag.com");
        }
        List<String> paths = java.util.Arrays.asList("/first", "/second", "/third", "/fourth", "/fifth");
        Random random = new Random();
        List<ProducerRecord<String, String>> records = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String randomHost = hosts.get(random.nextInt(hosts.size()));
            String randomPath = paths.get(random.nextInt(paths.size()));
            String requestHeaders = "{\"host\":\"" + randomHost + "\",\"user-agent\":\"Mozilla/5.0\"}";
            IngestDataBatch payload = new IngestDataBatch(
                    randomPath,
                    requestHeaders,
                    requestHeaders,
                    "POST",
                    "",
                    "{\"data\":\"asd\"}",
                    "0.0.0.0",
                    "0",
                    "200",
                    "HTTP/1.1",
                    "",
                    "1000000",
                    "123",
                    "false",
                    "MIRRORING"
            );
            String record = insertData(payload);
            records.add(new ProducerRecord<>(TOPIC, null, record));
        }
        return records;
    }

    public static List<ProducerRecord<String, String>> fileReadRecordGeneration(String filePath) {
        List<ProducerRecord<String, String>> records = new ArrayList<>();
        try (java.io.InputStream is = KafkaProducerExample.class.getClassLoader().getResourceAsStream("kafka.txt")) {
            if (is == null) {
                throw new RuntimeException("Resource kafka.txt not found in classpath");
            }
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    records.add(new ProducerRecord<>(TOPIC, null, line));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading kafka.txt from resources: " + e.getMessage(), e);
        }
        return records;
    }

    public static void main(String[] args) {
        // Configure Kafka producer properties
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        boolean fileReadMode = true; // Set to true to use fileReadRecordGeneration, false for demoRecordGenerator

        List<ProducerRecord<String, String>> records;
        if (fileReadMode) {
            records = fileReadRecordGeneration("kafka.txt");
        } else {
            records = demoRecordGenerator();
        }


        for (int i = 0; i < 1_000_000; i++){
            produceRecords(props, records);
        }

    }

    private static void produceRecords(Properties props, List<ProducerRecord<String, String>> records) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (ProducerRecord<String, String> record : records) {
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        System.err.println("Error sending record: " + exception.getMessage());
                    } else {
                        System.out.println("Record sent to partition " + metadata.partition() +
                                " with offset " + metadata.offset());
                    }
                });
            }
            producer.flush();
            System.out.println("Successfully sent " + records.size() + " records to Kafka topic: " + TOPIC);
        } catch (Exception e) {
            System.err.println("Error in Kafka producer: " + e.getMessage());
        }
    }
}