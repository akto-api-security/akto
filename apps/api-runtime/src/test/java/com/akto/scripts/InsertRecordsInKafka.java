package com.akto.scripts;

import com.akto.DaoInit;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.KafkaHealthMetric;
import com.akto.dto.traffic.SampleData;
import com.akto.kafka.Kafka;
import com.akto.runtime.Main;
import com.akto.runtime.utils.Utils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;

import java.time.Duration;
import java.util.*;

public class InsertRecordsInKafka {

    public static final String RUNTIME_TOPIC = "akto.api.logs";
    public static final String ANALYSE_TOPIC = "akto.central";
    public static final String KAFKA_URL = "localhost:29092";

    public static void main(String[] args) throws InterruptedException {
//        insertSampleDataIntoKafka();
        checkKafkaQueueSize(RUNTIME_TOPIC, "asdfd3", KAFKA_URL);
        //209447
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void insertSampleDataIntoKafka() {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);
        Kafka kafka = new Kafka(KAFKA_URL,0, 0);
        List<SampleData> sampleDataList = SampleDataDao.instance.findAll(new BasicDBObject());
        System.out.println("size: " + sampleDataList.size());
        int i =0;
        for (SampleData sampleData: sampleDataList) {
            for (String message: sampleData.getSamples()) {
                i += 1;
                System.out.println("s: " + i);
                kafka.send(message, RUNTIME_TOPIC);

//                if (i%30 == 0 && !kafka.producerReady) {
//                    kafka = new Kafka(KAFKA_URL,0, 0);
//                }

                try {
//                    Thread.sleep(1000);
                } catch (Exception ignored) {

                }
            }

        }

        System.out.println("sent: " + i);
        try {
            Thread.sleep(10_000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void insertRandomRecords() throws InterruptedException {
        String kafkaBrokerUrl = "localhost:29092";

        Kafka kafka = new Kafka(kafkaBrokerUrl, 1000, 999900);

        for (int apiCollectionId=0; apiCollectionId < 10; apiCollectionId++ ) {
            for (String url: Arrays.asList("a", "b", "c", "d", "e", "f", "g", "h", "1", "i", "j", "2", "bc/d", "c/d/e/f", "a/b")) {
                for (String method: Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")) {
                    for (int ip=0; ip<255; ip++) {
                        try {
                            Map<String,String> data = generate(
                                    "/"+url, method, "192.100.23." + ip, generateRequestPayload(), generateResponsePayload(url, method), apiCollectionId
                            );
                            String message= mapper.writeValueAsString(data);
                            kafka.send(message, RUNTIME_TOPIC);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                System.out.println(apiCollectionId + " " + url);
            }
        }

        Thread.sleep(5000);
    }

    private final static ObjectMapper mapper = new ObjectMapper();

    public static Map<String,String> generate(String path, String method, String ip, String requestPayload, String responsePayload, int apiCollectionId) throws Exception {
        Map<String, List<String>> requestHeaders = new HashMap<>();
        Map<String, List<String>> responseHeaders = new HashMap<>();

        requestHeaders.put("x-forwarded-for", Collections.singletonList(ip));

        String requestHeadersString = mapper.writeValueAsString(requestHeaders);
        String responseHeadersString = mapper.writeValueAsString(responseHeaders);

        Map<String,String> result = new HashMap<>();
        result.put("akto_account_id", 1_000_000+"");
        result.put("path",path);
        result.put("requestHeaders", requestHeadersString);
        result.put("responseHeaders", responseHeadersString);
        result.put("method",method);
        result.put("requestPayload",requestPayload);
        result.put("responsePayload",responsePayload);
        result.put("ip", "127.0.0.1");
        result.put("time", Context.now()+"");
        result.put("statusCode", 200+"");
        result.put("type", "");
        result.put("status","");
        result.put("contentType", "application/json");
        result.put("source", "MIRRORING");
        result.put("akto_vxlan_id", apiCollectionId+"");

        return result;
    }

    public static String generateRequestPayload() {
        return "{\"user\": \"avneesh\", \"age\": 99, \"company\": \"AKTO\", \"mobileNum\": \"+917021916328\", \"email\": \"avneesh@akto.io\"}";
    }

    public static String generateResponsePayload(String url, String method) throws Exception{
        Map<String, String> v = new HashMap<>();
        v.put("unique/"+url+method, "Hi");
        v.put("unique1/"+url+method, "Hello");
        v.put("unique2/"+url+method, "Wassup");
        v.put("unique3/"+url+method, "Wadddddupppp");
        v.put("unique4/"+url+method, "^^");

        return mapper.writeValueAsString(v);
    }

    public static void checkSize() throws InterruptedException {
        String kafkaBrokerUrl = "localhost:29092";
        checkKafkaQueueSize(RUNTIME_TOPIC,"asdf", kafkaBrokerUrl);

        while (true) {
            Thread.sleep(10_000);
            System.out.println("ALIVE....");
        }
    }

    public static void checkKafkaQueueSize(String topicName, String groupIdConfig, String kafkaBrokerUrl) {
        Properties properties = Utils.configProperties(kafkaBrokerUrl, groupIdConfig, 1000);
        Consumer<String, String> consumer = new KafkaConsumer<>(properties);
        consumer.subscribe(Collections.singleton(topicName));
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));

        System.out.println(consumer.assignment().size());
        for (TopicPartition tp: consumer.assignment()) {
            String tpName = tp.topic();
            System.out.println(tpName);
            long endOffset = consumer.endOffsets(Collections.singleton(tp)).get(tp);
            System.out.println(endOffset);
            System.out.println(" ");
        }

        consumer.close();
    }
}
