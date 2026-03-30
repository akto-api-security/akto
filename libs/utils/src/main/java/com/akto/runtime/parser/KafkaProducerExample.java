package com.akto.runtime.parser;
import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.akto.DaoInit;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic.SampleData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Properties;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;



public class KafkaProducerExample {
    // Kafka configuration
    // private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    public static final String BOOTSTRAP_SERVERS= "13.89.142.170:9093";
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
        for (int i = 1; i <= 10; i++) {
            hosts.add("host-" + i + "-auth-type.enigma");
        }
        List<String> paths = java.util.Arrays.asList("json-one", "/json-two", "/json/-three", "/json-four", "/json/5/test");
        // List<String> paths = java.util.Arrays.asList("json-one", "/json-two");
        Random random = new Random();
        List<ProducerRecord<String, String>> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String randomHost = hosts.get(random.nextInt(hosts.size()));
            String randomPath = paths.get(random.nextInt(paths.size())) ;
            String requestHeaders;

            // Add API_KEY header for every 5th record, MTLS for every 10th record (mutually exclusive)
            // if (i % 2 == 0) {
                // MTLS header for every 10th record
                requestHeaders = "{\"host\":\"" + randomHost + "\",\"user-agent\":\"Mozilla/5.0\", \"location\":\"pagenotd\",\"x-forwarded-client-cert\":\"By=spiffe://cluster.local/ns/default/sa/frontend;Hash=abc123;Subject=CN=client.example.com\"}";
            // } else if (i % 3 == 0) {
            //     // API_KEY header for every 5th record (but not 10th)
                // requestHeaders = "{\"host\":\"" + randomHost + "\",\"user-agent\":\"Mozilla/5.0\", \"location\":\"pagenotd\",\"x-api-key\":\"sk_live_2_" + random.nextInt(100000) + "\"}";
            // } else {
                // requestHeaders = "{\"host\":\"" + randomHost + "\",\"user-agent\":\"Mozilla/5.0\", \"location\":\"pagenotd\"}";
            // }

            IngestDataBatch payload = new IngestDataBatch(
                    randomPath,
                    requestHeaders,
                    requestHeaders,
                    "POST",
                    "{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"companyId\":{\"type\":\"string\"},\"companyName\":{\"type\":\"string\"}}}}",
                    "{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"companyId\":{\"type\":\"string\"},\"companyName\":{\"type\":\"string\"}}}}",
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


    public static void main(String[] args) throws Exception {
        System.out.printf("\n\n*******Running KafkaBenchmark******\n\n");

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        int targetRatePerSecond = 30000;
        int numCycles = 10;

        // Load records once from MongoDB
        List<ProducerRecord<String, String>> records = buildFromMongo(1758787662, null);
        int recordCount = records.size();

        // Calculate target cycle time: time to send recordCount messages at targetRate
        long targetCycleTimeMs = (recordCount * 1000L) / targetRatePerSecond;

        System.out.println(String.format("Target rate: %,d msgs/sec", targetRatePerSecond));
        System.out.println(String.format("Records per cycle: %,d", recordCount));
        System.out.println(String.format("Target cycle time: %d ms", targetCycleTimeMs));
        System.out.println();

        for (int i = 0; i < numCycles; i++) {
            long startTime = System.currentTimeMillis();

            produceRecords(props, records);

            long elapsed = System.currentTimeMillis() - startTime;
            long sleepTime = targetCycleTimeMs - elapsed;

            double actualRate = (recordCount * 1000.0) / elapsed;
            System.out.println(String.format("Cycle %d: sent %,d records in %d ms (%.0f msgs/sec), sleeping %d ms",
                i + 1, recordCount, elapsed, actualRate, Math.max(0, sleepTime)));

            if (sleepTime > 0) {
                Thread.sleep(sleepTime);
            }
        }

        System.out.println(String.format("\nCompleted: %,d total messages", recordCount * numCycles));
    }

    public static void main21(String[] args) {
        // Configure Kafka producer properties
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        // Set to true to use fileReadRecordGeneration, false for demoRecordGenerator
        boolean fileReadMode = false; 

        List<ProducerRecord<String, String>> records;
        if (fileReadMode) {
            records = fileReadRecordGeneration("kafka.txt");
        } else {
            records = demoRecordGenerator();
        }


        for (int i = 0; i < 1; i++){
            produceRecords(props, records);
        }

    }

    public static List<ProducerRecord<String, String>> buildFromMongo(int accountId, List<Integer> apiCollectionIds) {
        List<ProducerRecord<String, String>> records = new ArrayList<>();

        // Initialize MongoDB connection
        String mongoUri = System.getenv("AKTO_MONGO_CONN") != null
            ? System.getenv("AKTO_MONGO_CONN")
            : "mongodb://localhost:27017";
        DaoInit.init(new ConnectionString(mongoUri));

        // Set account context
        Context.accountId.set(accountId);

        // Fetch sample data filtered by apiCollectionIds (or all if null)
        List<SampleData> sampleDataList;
        if (apiCollectionIds == null || apiCollectionIds.isEmpty()) {
            sampleDataList = SampleDataDao.instance.findAll(Filters.empty());
        } else {
            sampleDataList = SampleDataDao.instance.findAll(
                Filters.in("_id.apiCollectionId", apiCollectionIds)
            );
        }

        System.out.println("Found " + sampleDataList.size() + " sample data documents");

        for (SampleData sampleData : sampleDataList) {
            if (sampleData.getSamples() == null) {
                continue;
            }
            for (String sample : sampleData.getSamples()) {
                // Samples are already stored in the same JSON format as demoRecordGenerator produces
                // (same format as IngestDataBatch -> insertData() output)
                records.add(new ProducerRecord<>(TOPIC, null, sample));
            }
        }

        System.out.println("Built " + records.size() + " ProducerRecord records from MongoDB");
        return records;
    }

    private static void produceRecords(Properties props, List<ProducerRecord<String, String>> records) {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            for (ProducerRecord<String, String> record : records) {
                producer.send(record, (metadata, exception) -> {
                    if (exception != null) {
                        System.err.println("Error sending record: " + exception.getMessage());
                    } else {
                        // System.out.println("Record sent to partition " + metadata.partition() +
                        //         " with offset " + metadata.offset());
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