package com.akto.threat.detection.scripts;

import com.akto.DaoInit;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic.SampleData;
import com.akto.threat.detection.kafka.KafkaProtoProducer;
import com.akto.kafka.KafkaConfig;
import com.akto.kafka.KafkaConsumerConfig;
import com.akto.kafka.KafkaProducerConfig;
import com.akto.kafka.Serializer;
import com.akto.proto.http_response_param.v1.StringList;
import com.akto.proto.http_response_param.v1.HttpResponseParam;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;

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
    public static long numRecords = 400001L;
    
    
    
    public static final String THREAT_TOPIC = "akto.api.logs";
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

        int targetRatePerSecond = 30000;
        int numCycles = 10;

        // Load records once from MongoDB
        List<HttpResponseParam> records = buildFromMongo(1758787662, null);
        int recordCount = records.size();

        // Calculate target cycle time: time to send recordCount messages at targetRate
        long targetCycleTimeMs = (recordCount * 1000L) / targetRatePerSecond;

        System.out.println(String.format("Target rate: %,d msgs/sec", targetRatePerSecond));
        System.out.println(String.format("Records per cycle: %,d", recordCount));
        System.out.println(String.format("Target cycle time: %d ms", targetCycleTimeMs));
        System.out.println();

        for (int i = 0; i < numCycles; i++) {
            long startTime = System.currentTimeMillis();

            dumpMessagesToKafka(THREAT_TOPIC, records);

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

    public static List<HttpResponseParam> buildRecordsFromCurl(long numRecords) {
        HttpResponseParam.Builder builder = HttpResponseParam.newBuilder();
        List<HttpResponseParam> records = new ArrayList<>();

        // Request headers from curl
        Map<String, StringList> requestHeaders = new HashMap<>();
        requestHeaders.put("referer", StringList.newBuilder().addValues("https://app.acorns.com/present").build());
        requestHeaders.put("x-datadog-sampling-priority", StringList.newBuilder().addValues("1").build());
        requestHeaders.put("sec-fetch-site", StringList.newBuilder().addValues("same-site").build());
        requestHeaders.put("x-datadog-origin", StringList.newBuilder().addValues("rum").build());
        requestHeaders.put("origin", StringList.newBuilder().addValues("https://app.acorns.com").build());
        requestHeaders.put("x-client-app", StringList.newBuilder().addValues("web-app").build());
        requestHeaders.put("x-forwarded-port", StringList.newBuilder().addValues("443").build());
        requestHeaders.put("x-client-os", StringList.newBuilder().addValues("Windows").build());
        requestHeaders.put("x-client-browser-version", StringList.newBuilder().addValues("146.0.0.0").build());
        requestHeaders.put("authorization", StringList.newBuilder().addValues("Aaaaaa-aaAaaAaaAaAAAaA1AaAaAaA1aAA1AaaAAAAaAaaaAAA1AaAaAAAaAAAaAAAaAAAaAAAaAa11AAA1AAa1AaaaAaAaAAAaAaA1-aaAaaAAaAaAaaaAaaAAaAaaaa1AaAaaaAaAaAAAaAAAaAAAaAA11AAaaAAaaAAAaAaa1AAAaAaAaAaAaAaaaaAAaaA1aAAA1AaaaAaaaaaAaAaaaAAAaAaa1AAAaAAAaAa11AAa1AAa1AaAaAaaaAaaaAAAaAaaaAaaaA1aaAaaaAAA1AaAaAaAaA1A1Aa11AaAaAAa1AaAaAaA1AAAaAAAaAaaaAaaaaAAaAaaaAAA1aA1aA11aaaAaA11aAaaaaA1aaAAaAAAaAaaaAAAaaa1aA1AaAaA1aAAaAAaaaAAaAAA1AaaaAAaaAAAaAAaaAAAaAAAaAa1aAAaaAAA1AAAaAaAaAaAaAAAaAaAaAaAaa1aAa1aaaaaaAaaaAaA1AAAaAaAaAaa1Aa11AAAaAAA1AAaaAAAaA1A1AAAaAAAaAaaaaAA1AaaaAaa1AaA1AAA1AAAaaAAaAaA1AaAaAaAaAAA1-aAaA-aA-AaAaaA-Aa1aAaA1Aa11Aaaa1AAa1Aa-1AAA-aaA1A11AAA11aAAAAaAAA1A1AAAAAaAAAAaAaAAAAA1a1aaa1aaAaaaaAAa1AAaA1aAA11aAAaaAaA1Aa1aaaa11A1aAAaa1a1AaAA1A1aAaaaAAa-1aAaAAAAA-aAA-aA11aAaAaaaaaaaA1AA1aaAaaaAAAaAaaaaa1aAaAAa-AaaAaAaAAaaAAAa-1Aa1A1AAAAA1aA1AaaAaA1a1aAAA11a-AAAaAaA-AaaAAAAAA1aAAAA11a1AaA1aAAaAaaA-Aaaaa-a1A1AaAaAA1a-1AA1a1aaAAaAAAaa11a").build());
        requestHeaders.put("x-datadog-parent-id", StringList.newBuilder().addValues("500584873391584059").build());
        requestHeaders.put("sec-ch-ua-mobile", StringList.newBuilder().addValues("?0").build());
        requestHeaders.put("host", StringList.newBuilder().addValues("graphql.acorns.com").build());
        requestHeaders.put("content-type", StringList.newBuilder().addValues("application/json").build());
        requestHeaders.put("x-client-hardware", StringList.newBuilder().addValues("undefined").build());
        requestHeaders.put("auth-strategy", StringList.newBuilder().addValues("jwt").build());
        requestHeaders.put("sec-fetch-mode", StringList.newBuilder().addValues("cors").build());
        requestHeaders.put("x-request-id", StringList.newBuilder().addValues("92ae804f-5790-9e31-a7d9-552f002bfada").build());
        requestHeaders.put("x-client-browser", StringList.newBuilder().addValues("Chrome").build());
        requestHeaders.put("x-forwarded-proto", StringList.newBuilder().addValues("https").build());
        requestHeaders.put("accept-language", StringList.newBuilder().addValues("en-US,en;q=0.9").build());
        requestHeaders.put("cookie", StringList.newBuilder().addValues("_gcl_au=1.1.580898316.1773870941; _mlcp=MLCP.1.1773870941575.1997899626; _mlcs=MLCP.1.1773870941586.1; IR_PI=35e94047-2315-11f1-9230-d5fa68ad209b%7C1773957341457; _scid=l1eCzhl-8ej8-24JGPqigpJsTkMors1f; _tt_enable_cookie=1; _ttp=01KM1F52FN471CS5TES3S78CZT_.tt.1; _axwrt=bd9b1bdc-2ea6-42bb-8f9c-2bebbd642acc; _ScCbts=%5B%5D; _fbp=fb.1.1773870942939.764342515783357324; _sctr=1%7C1773817200000").build());
        requestHeaders.put("x-client-build", StringList.newBuilder().addValues("4.165.0").build());
        requestHeaders.put("x-datadog-trace-id", StringList.newBuilder().addValues("2724877023334930821").build());
        requestHeaders.put("x-forwarded-for", StringList.newBuilder().addValues("97.218.72.4").build());
        requestHeaders.put("priority", StringList.newBuilder().addValues("u=1, i").build());
        requestHeaders.put("accept", StringList.newBuilder().addValues("*/*").build());
        requestHeaders.put("sec-ch-ua", StringList.newBuilder().addValues("\"Chromium\";v=\"146\", \"Not-A.Brand\";v=\"24\", \"Google Chrome\";v=\"146\"").build());
        requestHeaders.put("x-amzn-trace-id", StringList.newBuilder().addValues("Root=1-69c21e66-13aa9a616751a07a46b49261").build());
        requestHeaders.put("x-client-platform", StringList.newBuilder().addValues("web").build());
        requestHeaders.put("sec-ch-ua-platform", StringList.newBuilder().addValues("\"Windows\"").build());
        requestHeaders.put("traceparent", StringList.newBuilder().addValues("00-000000000000000025d0b53738809985-3fd2df1d803f84b9-01").build());
        requestHeaders.put("x-client-auth-method", StringList.newBuilder().addValues("cookies").build());
        requestHeaders.put("x-csrf-token", StringList.newBuilder().addValues("1Aa-AaAAAA1AaaaaA1AAaA1aaaaA1Aa-a1AaAaAA-aa").build());
        requestHeaders.put("x-envoy-expected-rq-timeout-ms", StringList.newBuilder().addValues("15000").build());
        requestHeaders.put("accept-encoding", StringList.newBuilder().addValues("gzip, deflate, br, zstd").build());
        requestHeaders.put("user-agent", StringList.newBuilder().addValues("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/146.0.0.0 Safari/537.36").build());
        requestHeaders.put("sec-fetch-dest", StringList.newBuilder().addValues("empty").build());

        // Response headers from response
        Map<String, StringList> responseHeaders = new HashMap<>();
        responseHeaders.put("access-control-allow-origin", StringList.newBuilder().addValues("https://app.acorns.com").build());
        responseHeaders.put("content-length", StringList.newBuilder().addValues("8657").build());
        responseHeaders.put("access-control-allow-credentials", StringList.newBuilder().addValues("true").build());
        responseHeaders.put("access-control-max-age", StringList.newBuilder().addValues("7200").build());
        responseHeaders.put("content-type", StringList.newBuilder().addValues("application/json; charset=utf-8").build());
        responseHeaders.put("access-control-allow-methods", StringList.newBuilder().addValues("POST, OPTIONS, GET").build());

        // Request payload from curl -d
        String requestPayload = "{\"operationName\":\"ReferralDetailsWithAdvocate\",\"variables\":{},\"extensions\":{\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"a64fb3a340c49bd126c3928589b4f2645cc3c6da4131947e203aacf3a666e695\"}},\"query\":\"query ReferralDetailsWithAdvocate {\\n  acceptanceDocuments {\\n    name\\n    type\\n    summaryUrl\\n    id\\n    url\\n    __typename\\n  }\\n  user {\\n    uuid\\n    email\\n    firstName\\n    lastName\\n    __typename\\n  }\\n  advocate {\\n    campaign {\\n      id\\n      title\\n      description\\n      eligibilityEnd\\n      publishEnd\\n      activeInHouseCampaign\\n      actionFeedDesc\\n      qualificationAction\\n      qualificationAmount\\n      autoAdd\\n      background\\n      rewards {\\n        amount\\n        hurdle\\n        id\\n        targets\\n        type\\n        __typename\\n      }\\n      __typename\\n    }\\n    experienceContent {\\n      shareableLink\\n      __typename\\n    }\\n    __typename\\n  }\\n}\\n\"}";

        // Response payload from response
        String responsePayload = "{\"data\":{\"user\":{\"uuid\":\"6eed41e6-02be-4d70-80d0-c77e3225ebea\",\"email\":\"dschulman951@gmail.com\",\"firstName\":\"Daniel\",\"lastName\":\"Schulman\",\"__typename\":\"User\"},\"acceptanceDocuments\":[{\"name\":\"Checking Pre-Order Terms\",\"type\":\"spend_preorder_terms\",\"summaryUrl\":\"\",\"id\":\"b593578a-19e2-4c37-b1c4-ecd462e8ca1a\",\"url\":\"https://www.acorns.com/documents-plain/preorder-06052018/\",\"__typename\":\"AcceptanceDocument\"}],\"advocate\":{\"campaign\":{\"id\":\"2cf57a70-41e7-48c7-a345-125754705d91\",\"title\":\"$600 for referring 3 friends\",\"description\":\"We'll invest $600 into your Invest account when you invite 3 or more friends to sign up and they invest.\",\"eligibilityEnd\":\"2026-04-13T06:59:59.999Z\",\"publishEnd\":\"2026-03-30T06:59:59.999Z\",\"activeInHouseCampaign\":true,\"actionFeedDesc\":\"We'll invest $600 into your Invest account when you invite 3 friends to join Acorns and they invest.\",\"qualificationAction\":\"initial_investment\",\"qualificationAmount\":5,\"autoAdd\":false,\"background\":\"/referrals/home/header_potential\",\"rewards\":[{\"amount\":600,\"hurdle\":3,\"id\":\"dcbde0c8-d45e-4e4d-8197-cd0bd8ca1c61\",\"targets\":[\"ADVOCATE\"],\"type\":\"INVESTMENT\",\"__typename\":\"ReferralCampaignReward\"}],\"__typename\":\"ReferralCampaignV2\"},\"experienceContent\":{\"shareableLink\":\"https://acorns.com/share/?shareable_code=TN1LRQE&first_name=Daniel&friend_reward=5\",\"__typename\":\"ExperienceContent\"},\"__typename\":\"Advocate\"}},\"extensions\":{}}";

        builder.setMethod("POST")
            .setPath("/graphql")
            .setType("HTTP/1.1")
            .putAllRequestHeaders(requestHeaders)
            .putAllResponseHeaders(responseHeaders)
            .setRequestPayload(requestPayload)
            .setResponsePayload(responsePayload)
            .setApiCollectionId(123)
            .setStatusCode(200)
            .setStatus("OK")
            .setTime((int) (System.currentTimeMillis() / 1000))
            .setAktoAccountId("1000000")
            .setIp("97.218.72.4")
            .setDestIp("graphql.acorns.com")
            .setDirection("INBOUND")
            .setIsPending(false)
            .setSource("MIRRORING")
            .setAktoVxlanId("1313121");

        for (long i = 0; i < numRecords; i++) {
            // if (i % 100 == 0) {
            //     builder.setStatusCode(401);
            // } else {
            //     builder.setStatusCode(200);
            // }
            HttpResponseParam record = builder.build();
            records.add(record);
        }

        return records;
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
        List<HttpResponseParam> records = buildFromMongo(1758787662, null); 
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

    public static List<HttpResponseParam> buildFromMongo(int accountId, List<Integer> apiCollectionIds) {
        List<HttpResponseParam> records = new ArrayList<>();

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
                try {
                    HttpResponseParam param = convertSampleToProto(sample, sampleData);
                    if (param != null) {
                        records.add(param);
                    }
                } catch (Exception e) {
                    System.err.println("Error parsing sample: " + e.getMessage());
                }
            }
        }

        System.out.println("Built " + records.size() + " HttpResponseParam records from MongoDB");
        return records;
    }

    private static HttpResponseParam convertSampleToProto(String sample, SampleData sampleData) {
        JSONObject jsonObject = JSON.parseObject(sample);
        if (jsonObject == null) {
            return null;
        }

        HttpResponseParam.Builder builder = HttpResponseParam.newBuilder();

        // Extract request fields
        String method = jsonObject.getString("method");
        String path = jsonObject.getString("path");
        String type = jsonObject.getString("type");
        String requestPayload = jsonObject.getString("requestPayload");
        String responsePayload = jsonObject.getString("responsePayload");

        // Extract other fields
        Integer statusCode = jsonObject.getInteger("statusCode");
        String status = jsonObject.getString("status");
        Integer time = jsonObject.getInteger("time");
        String aktoAccountId = jsonObject.getString("akto_account_id");
        String ip = jsonObject.getString("ip");
        String destIp = jsonObject.getString("destIp");
        String direction = jsonObject.getString("direction");
        String source = jsonObject.getString("source");
        String aktoVxlanId = jsonObject.getString("akto_vxlan_id");

        // Parse request headers
        Map<String, StringList> requestHeaders = parseHeaders(jsonObject, "requestHeaders");

        // Parse response headers
        Map<String, StringList> responseHeaders = parseHeaders(jsonObject, "responseHeaders");

        // Build proto
        builder.setMethod(method != null ? method : "GET")
            .setPath(path != null ? path : "/")
            .setType(type != null ? type : "HTTP/1.1")
            .putAllRequestHeaders(requestHeaders)
            .putAllResponseHeaders(responseHeaders)
            .setRequestPayload(requestPayload != null ? requestPayload : "")
            .setResponsePayload(responsePayload != null ? responsePayload : "")
            .setApiCollectionId(sampleData.getId() != null ? sampleData.getId().getApiCollectionId() : 0)
            .setStatusCode(statusCode != null ? statusCode : 200)
            .setStatus(status != null ? status : "OK")
            .setTime(time != null ? time : (int) (System.currentTimeMillis() / 1000))
            .setAktoAccountId(aktoAccountId != null ? aktoAccountId : String.valueOf(Context.accountId.get()))
            .setIp(ip != null ? ip : "")
            .setDestIp(destIp != null ? destIp : "")
            .setDirection(direction != null ? direction : "INBOUND")
            .setIsPending(false)
            .setSource(source != null ? source : "MIRRORING")
            .setAktoVxlanId(aktoVxlanId != null ? aktoVxlanId : "0");

        return builder.build();
    }

    private static Map<String, StringList> parseHeaders(JSONObject jsonObject, String headerKey) {
        Map<String, StringList> headers = new HashMap<>();

        Object headersObj = jsonObject.get(headerKey);
        if (headersObj == null) {
            return headers;
        }

        // Headers can be stored as JSON string or as JSONObject
        JSONObject headersJson;
        if (headersObj instanceof String) {
            try {
                headersJson = JSON.parseObject((String) headersObj);
            } catch (Exception e) {
                return headers;
            }
        } else if (headersObj instanceof JSONObject) {
            headersJson = (JSONObject) headersObj;
        } else {
            return headers;
        }

        if (headersJson == null) {
            return headers;
        }

        for (String key : headersJson.keySet()) {
            Object value = headersJson.get(key);
            StringList.Builder listBuilder = StringList.newBuilder();

            if (value instanceof List) {
                for (Object v : (List<?>) value) {
                    listBuilder.addValues(v.toString());
                }
            } else if (value != null) {
                listBuilder.addValues(value.toString());
            }

            headers.put(key.toLowerCase(), listBuilder.build());
        }

        return headers;
    }
}

// Config for kafka url, topic and payload source -> HAR file, local json/curl
// files
// Convert these sources to httpResponseparm protobuff
// Benchmark results: records/sec, records/min, Gb/sec
// Payload sizes: 4kb, 10kb, 50kb, 1MB
// Payload source: HAR file, local json/curl files
// Payload dump from curl