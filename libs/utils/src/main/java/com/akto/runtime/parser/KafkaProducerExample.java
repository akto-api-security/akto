package com.akto.runtime.parser;

import org.apache.kafka.clients.producer.*;
import org.apache.kafka.common.serialization.StringSerializer;

import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import com.akto.DaoInit;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic.SampleData;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Collectors;

public class KafkaProducerExample {
    // Kafka configuration
    private static final String BOOTSTRAP_SERVERS = "localhost:9092";
    private static final String TOPIC = "akto.api.logs";

    enum RecordGeneratorMode {
        DEMO,
        FILE_READ,
        SAMPLE_DATA
    }

    public static void main(String[] args) {
        // Configure Kafka producer properties
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");

        // -Dmode=DEMO|FILE_READ|SAMPLE_DATA, -Daccount, -DurlFilter (regex, empty = all), -Dcollections (ids, comma separated),
        // -Dfile (path; default: classpath kafka.txt), -DshiftToNow=true (move sample times so the newest is now)
        RecordGeneratorMode mode = RecordGeneratorMode.valueOf(System.getProperty("mode", "SAMPLE_DATA"));

        List<ProducerRecord<String, String>> records;
        switch (mode) {
            case FILE_READ:
                records = fileReadRecordGeneration(System.getProperty("file", "kafka.txt"));
                break;
            case SAMPLE_DATA:
                records = sampleDataRecordGenerator(Integer.parseInt(System.getProperty("account", "1758787662")),
                        System.getProperty("urlFilter", "typename"), System.getProperty("collections", ""),
                        Boolean.parseBoolean(System.getProperty("shiftToNow", "false")));
                break;
            case DEMO:
            default:
                records = demoRecordGenerator();
                break;
        }

        for (int i = 0; i < 1; i++) {
            produceRecords(props, records);
        }

    }

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

        if (payload.getTag() != null && !payload.getTag().isEmpty()) {
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

    public static List<ProducerRecord<String, String>> sampleDataRecordGenerator(int accountId, String urlFilter,
            String collections, boolean shiftToNow) {
        Context.accountId.set(accountId);
        DaoInit.init(new ConnectionString("mongodb://localhost:27017"));
        List<Bson> filters = new ArrayList<>();
        if (!urlFilter.isEmpty()) {
            filters.add(Filters.regex("_id.url", urlFilter));
        }
        if (!collections.isEmpty()) {
            filters.add(Filters.in("_id.apiCollectionId",
                    Arrays.stream(collections.split(",")).map(String::trim).map(Integer::parseInt).collect(Collectors.toList())));
        }
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(filters.isEmpty() ? new BasicDBObject() : Filters.and(filters));

        // Replay in request-time order so per-session sequences arrive as they happened
        List<BasicDBObject> samples = new ArrayList<>();
        for (SampleData sampleDoc : sampleDataList) {
            if (sampleDoc.getSamples() == null) {
                continue;
            }
            for (String sample : sampleDoc.getSamples()) {
                samples.add(BasicDBObject.parse(sample));
            }
        }
        samples.sort(Comparator.comparingLong(KafkaProducerExample::sampleTime));
        long shift = 0;
        if (shiftToNow && !samples.isEmpty()) {
            shift = System.currentTimeMillis() / 1000 - sampleTime(samples.get(samples.size() - 1));
        }

        List<ProducerRecord<String, String>> records = new ArrayList<>();
        for (BasicDBObject sample : samples) {
            if (shift != 0) {
                sample.put("time", String.valueOf(sampleTime(sample) + shift));
            }
            records.add(new ProducerRecord<>(TOPIC, null, sample.toJson()));
        }
        return records;
    }

    private static long sampleTime(BasicDBObject sample) {
        try {
            return Long.parseLong(String.valueOf(sample.get("time")));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static List<ProducerRecord<String, String>> demoRecordGenerator() {
        List<String> hosts = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            hosts.add("host-" + i + "-auth-type.enigma");
        }
        List<String> paths = Arrays.asList("json-one", "/json-two", "/json/-three", "/json-four",
                "/json/5/test");
        // List<String> paths = Arrays.asList("json-one", "/json-two");
        Random random = new Random();
        List<ProducerRecord<String, String>> records = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String randomHost = hosts.get(random.nextInt(hosts.size()));
            String randomPath = paths.get(random.nextInt(paths.size()));
            String requestHeaders;

            // Add API_KEY header for every 5th record, MTLS for every 10th record (mutually
            // exclusive)
            // if (i % 2 == 0) {
            // MTLS header for every 10th record
            requestHeaders = "{\"host\":\"" + randomHost
                    + "\",\"user-agent\":\"Mozilla/5.0\", \"location\":\"pagenotd\",\"x-forwarded-client-cert\":\"By=spiffe://cluster.local/ns/default/sa/frontend;Hash=abc123;Subject=CN=client.example.com\"}";
            // } else if (i % 3 == 0) {
            // // API_KEY header for every 5th record (but not 10th)
            // requestHeaders = "{\"host\":\"" + randomHost +
            // "\",\"user-agent\":\"Mozilla/5.0\",
            // \"location\":\"pagenotd\",\"x-api-key\":\"sk_live_2_" +
            // random.nextInt(100000) + "\"}";
            // } else {
            // requestHeaders = "{\"host\":\"" + randomHost +
            // "\",\"user-agent\":\"Mozilla/5.0\", \"location\":\"pagenotd\"}";
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
                    "MIRRORING");
            String record = insertData(payload);
            records.add(new ProducerRecord<>(TOPIC, null, record));
        }
        return records;
    }

    public static List<ProducerRecord<String, String>> fileReadRecordGeneration(String filePath) {
        List<ProducerRecord<String, String>> records = new ArrayList<>();
        try (InputStream is = Files.exists(Paths.get(filePath)) ? Files.newInputStream(Paths.get(filePath))
                : KafkaProducerExample.class.getClassLoader().getResourceAsStream(filePath)) {
            if (is == null) {
                throw new RuntimeException(filePath + " not found as a file or classpath resource");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        records.add(new ProducerRecord<>(TOPIC, null, line));
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error reading " + filePath + ": " + e.getMessage(), e);
        }
        return records;
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