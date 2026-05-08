package com.akto.threat.detection.scripts;

import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.proto.http_response_param.v1.HttpResponseParam;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class KafkaBenchmark {

    public static List<String> payloadSizes = new ArrayList<>(Arrays.asList(
            "4KB",
            "10KB",
            "20KB",
            "1MB"));

    public static String FILE_PATH = "sample-payloads/";
    public static String payloadFormat = "json";
    public static String payloadSize = payloadSizes.get(0);
    public static long numRecords = 41L;
    
    
    
    public static final String THREAT_TOPIC = "akto.api.logs2";
    public static final String KAFKA_URL = "localhost:9092";
    private static final String CONSUMER_GROUP_ID = "akto.threat_detection";
    private static KafkaConfig internalKafkaConfig =
        KafkaConfig.newBuilder()
            .setGroupId(CONSUMER_GROUP_ID)
            .setBootstrapServers(KAFKA_URL)
            .setConsumerConfig(
                KafkaConsumerConfig.newBuilder()
                    .setMaxPollRecords(100)
                    .setPollDurationMilli(100)
                    .build())
            .setProducerConfig(
                KafkaProducerConfig.newBuilder().setBatchSize(100).setLingerMs(100).build())
            .setKeySerializer(Serializer.STRING)
            .setValueSerializer(Serializer.BYTE_ARRAY)
            .build();
    private static final KafkaProtoProducer internalKafka = new KafkaProtoProducer(internalKafkaConfig);

    public static void main(String[] args) throws Exception {
        System.out.printf("\n\n*******Running KafkaBenchmark******\n\n");
        System.out.println(String.format("Payload size: %s, Number of records: %,d", payloadSize, numRecords));
        dumpRecords(payloadSize, numRecords);
        Thread.sleep(numRecords * 1000);

        // timeFunction(() -> {
        // buildRecords(1024, 100000L);
        // return null;
        // });

    }

    public static String loadJsonFile(String payloadSizeFilename) throws Exception {
        InputStream inputStream = KafkaBenchmark.class.getClassLoader()
                .getResourceAsStream(FILE_PATH + payloadSizeFilename + "." + payloadFormat);

        if (inputStream == null) {
            throw new FileNotFoundException("File not found: " + payloadSizeFilename);
        }

        StringBuilder stringBuilder = new StringBuilder();
        int ch;
        while ((ch = inputStream.read()) != -1) {
            stringBuilder.append((char) ch);
        }
        inputStream.close();
        String jsonString = stringBuilder.toString();

        return jsonString;
    }

    public static List<HttpResponseParam> buildRecords(String payloadSize, long numRecords) {
        HttpResponseParam.Builder builder = HttpResponseParam.newBuilder();
        List<HttpResponseParam> records = new ArrayList<>();
        String payload = "";

        try{
        payload = loadJsonFile(payloadSize);
        }catch (Exception e){
            e.printStackTrace();
        }

        // Add request headers
        Map<String, StringList> requestHeaders = new HashMap<>();
        requestHeaders.put("content-type", StringList.newBuilder().addValues("application/json").build());
        requestHeaders.put("authorization", StringList.newBuilder().addValues("Bearer token").build());
        requestHeaders.put("user-agent", StringList.newBuilder().addValues("KafkaBenchmark/1.0/alert('XSS')").build());
        requestHeaders.put("x-forwarded-for", StringList.newBuilder().addValues("14.143.179.162").build());
            
        // Add response headers
        Map<String, StringList> responseHeaders = new HashMap<>();
        responseHeaders.put("content-type", StringList.newBuilder().addValues("application/json").build());
        responseHeaders.put("cache-control", StringList.newBuilder().addValues("no-cache").build());
        responseHeaders.put("server", StringList.newBuilder().addValues("nginx").build());

        builder.setMethod("POST")
            .setPath("v1/api/test/orders")
            .setType("HTTP/1.1")
            .putAllRequestHeaders(requestHeaders)
            .putAllResponseHeaders(responseHeaders)
            .setRequestPayload(payload)
            .setResponsePayload(payload)
            .setApiCollectionId(123)
            .setStatusCode(200)
            .setStatus("OK")
            .setTime((int) (System.currentTimeMillis() / 1000))
            .setAktoAccountId("1000000")
            .setIp("14.143.179.162")
            .setDestIp("154.248.155.13")
            .setDirection("INBOUND")
            .setIsPending(false)
            .setSource("MIRRORING")
            .setAktoVxlanId("1313121");
        
        for (long i = 0; i < numRecords; i++) {
            if (i%100 == 0) {
                builder.setStatusCode(401);
            } else {
                builder.setStatusCode(200);
            }
            HttpResponseParam record = builder.build();
            records.add(record);
        }

        return records;
    }

    public static void dumpRecords(String payloadSize, long numRecords) {
        List<HttpResponseParam> records = buildRecords(payloadSize, numRecords);
        System.out.printf("Dumping %,d records of size %s to Kafka \n", records.size(), payloadSize);
        timeFunction(() -> {
            dumpMessagesToKafka(THREAT_TOPIC, records);
            return null;
        });
    }

    private static void dumpMessagesToKafka(String topic, List<HttpResponseParam> records) {
        
        for (long i = 0; i < records.size(); i++) {
            internalKafka.send(topic, records.get((int)i));
        }
    }

    public static <T> T timeFunction(Supplier<T> function) {
        String functionName = Thread.currentThread().getStackTrace()[2].getMethodName();

        long startTime = System.nanoTime();

        T result = function.get();

        long endTime = System.nanoTime();
        long duration = endTime - startTime;

        System.out.println("Execution time for " + functionName + ": " + (duration / 1_000_000) + " milliseconds");
        return result;
    }
}

// Config for kafka url, topic and payload source -> HAR file, local json/curl
// files
// Convert these sources to httpResponseparm protobuff
// Benchmark results: records/sec, records/min, Gb/sec
// Payload sizes: 4kb, 10kb, 50kb, 1MB
// Payload source: HAR file, local json/curl files
// Payload dump from curl