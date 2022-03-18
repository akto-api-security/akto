package com.akto.runtime;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.akto.DaoInit;
import com.akto.dao.APIConfigsDao;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.RuntimeFilterDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.APIConfig;
import com.akto.dto.AccountSettings;
import com.akto.dto.ApiCollection;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.KafkaHealthMetric;
import com.akto.parsers.HttpCallParser;
import com.akto.dto.HttpResponseParams;
import com.akto.runtime.policies.AktoPolicy;
import com.google.gson.Gson;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private Consumer<String, String> consumer;
    public static final String GROUP_NAME = "group_name";
    public static final String VXLAN_ID = "vxlanId";
    public static final String VPC_CIDR = "vpc_cidr";
    private static final Logger logger = LoggerFactory.getLogger(HttpCallParser.class);

    private static int debugPrintCounter = 500;
    private static void printL(Object o) {
        if (debugPrintCounter > 0) {
            debugPrintCounter--;
            System.out.println(o);
        }
    }   

    public static boolean tryForCollectionName(String message) {
        boolean ret = false;
        try {
            Gson gson = new Gson();

            Map<String, Object> json = gson.fromJson(message, Map.class);

            logger.info("Json size: " + json.size());
            boolean withoutCidrCond = json.size() == 2 && json.containsKey(GROUP_NAME) && json.containsKey(VXLAN_ID);
            boolean withCidrCond = json.size() == 3 && json.containsKey(GROUP_NAME) && json.containsKey(VXLAN_ID) && json.containsKey(VPC_CIDR);
            if (withCidrCond || withoutCidrCond) {
                ret = true;
                String groupName = (String) (json.get(GROUP_NAME));
                String vxlanIdStr = ((Double) json.get(VXLAN_ID)).intValue() + "";
                int vxlanId = Integer.parseInt(vxlanIdStr);
                Bson findQ = Filters.eq("_id", vxlanId);
                ApiCollection currCollection = ApiCollectionsDao.instance.findOne(findQ);
                if (currCollection == null) {
                    ApiCollection newCollection = new ApiCollection(vxlanId, groupName, Context.now(), new HashSet<>());
                    ApiCollectionsDao.instance.getMCollection().insertOne(newCollection);
                } else if (currCollection.getName() == null || currCollection.getName().length() == 0) {
                    ApiCollectionsDao.instance.getMCollection().updateOne(findQ, Updates.set("name", groupName));
                }

                if (json.size() == 3) {
                    Bson accFBson = Filters.eq("_id", Context.accountId.get());
                    List<String> cidrList = (List<String>) json.get(VPC_CIDR);
                    logger.info("cidrList: " + cidrList);
                    AccountSettingsDao.instance.getMCollection().updateOne(
                        accFBson, Updates.addEachToSet("privateCidrList", cidrList), new UpdateOptions().upsert(true)
                    );
                }
            }
        } catch (Exception e) {
            logger.error("error in try collection", e);
        }

        return ret;
    }


    public static void createIndices() {
        SingleTypeInfoDao.instance.createIndicesIfAbsent();
    }

    public static void insertRuntimeFilters() {
        RuntimeFilterDao.instance.initialiseFilters();
    }

    // REFERENCE: https://www.oreilly.com/library/view/kafka-the-definitive/9781491936153/ch04.html (But how do we Exit?)
    public static void main(String[] args) {
        String mongoURI = System.getenv("AKTO_MONGO_CONN");;
        String configName = System.getenv("AKTO_CONFIG_NAME");
        String topicName = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        String kafkaBrokerUrl = System.getenv("AKTO_KAFKA_BROKER_URL");
        String groupIdConfig =  System.getenv("AKTO_KAFKA_GROUP_ID_CONFIG");
        int maxPollRecordsConfig = Integer.parseInt(System.getenv("AKTO_KAFKA_MAX_POLL_RECORDS_CONFIG"));

        if (topicName == null) topicName = "akto.api.logs";

        // mongoURI = "mongodb://write_ops:write_ops@cluster0-shard-00-00.yg43a.mongodb.net:27017,cluster0-shard-00-01.yg43a.mongodb.net:27017,cluster0-shard-00-02.yg43a.mongodb.net:27017/myFirstDatabase?ssl=true&replicaSet=atlas-qd3mle-shard-0&authSource=admin&retryWrites=true&w=majority";
        DaoInit.init(new ConnectionString(mongoURI));
        Context.accountId.set(1_000_000);
        SingleTypeInfoDao.instance.getMCollection().updateMany(Filters.exists("apiCollectionId", false), Updates.set("apiCollectionId", 0));

        createIndices();
        insertRuntimeFilters();

        ApiCollection apiCollection = ApiCollectionsDao.instance.findOne("_id", 0);
        if (apiCollection == null) {
            Set<String> urls = new HashSet<>();
            for(SingleTypeInfo singleTypeInfo: SingleTypeInfoDao.instance.fetchAll()) {
                urls.add(singleTypeInfo.getUrl());
            }
            ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", Context.now(), urls));
        }

        APIConfig apiConfig;
        apiConfig = APIConfigsDao.instance.findOne(Filters.eq("name", configName));
        if (apiConfig == null) {
            apiConfig = new APIConfig(configName,"access-token", 1, 10_000_000, 120);
        }

        final Main main = new Main();
        Properties properties = main.configProperties(kafkaBrokerUrl, groupIdConfig, maxPollRecordsConfig);
        main.consumer = new KafkaConsumer<>(properties);

        final Thread mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                main.consumer.wakeup();
                try {
                    mainThread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Map<String, HttpCallParser> httpCallParserMap = new HashMap<>();
        Map<String, Flow> flowMap = new HashMap<>();
        Map<String, AktoPolicy> aktoPolicyMap = new HashMap<>();

        // sync infra metrics thread
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        KafkaHealthMetricSyncTask task = new KafkaHealthMetricSyncTask();
        executor.scheduleAtFixedRate(task, 2, 60, TimeUnit.SECONDS);

        long lastSyncOffset = 0;

        try {
            main.consumer.subscribe(Collections.singleton(topicName));
            while (true) {
                ConsumerRecords<String, String> records = main.consumer.poll(Duration.ofMillis(10000));
                main.consumer.commitSync();
                
                // TODO: what happens if exception
                Map<String, List<HttpResponseParams>> responseParamsToAccountMap = new HashMap<>();
                for (ConsumerRecord<String,String> r: records) {
                    HttpResponseParams httpResponseParams;
                    try {
                         
                        printL(r.value());
                        lastSyncOffset++;

                        if (lastSyncOffset % 100 == 0) {
                            System.out.println("Committing offset at position: " + lastSyncOffset);
                        }

                        if (tryForCollectionName(r.value())) {
                            continue;
                        }

                        httpResponseParams = HttpCallParser.parseKafkaMessage(r.value());
                         
                    } catch (Exception e) {
                        logger.error("Error while parsing kafka message " + e);
                        continue;
                    }
                    String accountId = httpResponseParams.getAccountId();
                    if (!responseParamsToAccountMap.containsKey(accountId)) {
                        responseParamsToAccountMap.put(accountId, new ArrayList<>());
                    }
                    responseParamsToAccountMap.get(accountId).add(httpResponseParams);
                }

                for (String accountId: responseParamsToAccountMap.keySet()) {
                    int accountIdInt;
                    try {
                        accountIdInt = Integer.parseInt(accountId);
                    } catch (Exception ignored) {
                        // TODO:
                        logger.info("Account id not string");
                        continue;
                    }

                    Context.accountId.set(accountIdInt);

                    if (!httpCallParserMap.containsKey(accountId)) {
                        HttpCallParser parser = new HttpCallParser(
                                apiConfig.getUserIdentifier(), apiConfig.getThreshold(), apiConfig.getSync_threshold_count(),
                                apiConfig.getSync_threshold_time()
                        );

                        httpCallParserMap.put(accountId, parser);
                    }

                    if (!flowMap.containsKey(accountId)) {
                        Flow flow= new Flow(
                                apiConfig.getThreshold(), apiConfig.getSync_threshold_count(), apiConfig.getSync_threshold_time(),
                                apiConfig.getThreshold(), apiConfig.getSync_threshold_count(), apiConfig.getSync_threshold_time(),
                                apiConfig.getUserIdentifier()
                        );

                        flowMap.put(accountId, flow);
                    }

                    if (!aktoPolicyMap.containsKey(accountId)) {
                        AktoPolicy aktoPolicy = new AktoPolicy(true);
                        aktoPolicyMap.put(accountId, aktoPolicy);
                    }

                    HttpCallParser parser = httpCallParserMap.get(accountId);
                    Flow flow = flowMap.get(accountId);
                    AktoPolicy aktoPolicy = aktoPolicyMap.get(accountId);

                    try {
                        List<HttpResponseParams> accWiseResponse = responseParamsToAccountMap.get(accountId);
                        parser.syncFunction(accWiseResponse);
                        // flow.init(accWiseResponse);
                        // aktoPolicy.main(accWiseResponse);
                    } catch (Exception e) {
                        logger.error(e.toString());
                    }
                }

                // for (TopicPartition tp: main.consumer.assignment()) {
                //     String tpName = tp.topic();
                //     long position = main.consumer.position(tp);
                //     long endOffset = main.consumer.endOffsets(Collections.singleton(tp)).get(tp);
                //     int partition = tp.partition();

                //     if ((position-lastSyncOffset) > 100) {
                //         System.out.println("Committing offset at position: " + position + " for partition " + partition);
                //         lastSyncOffset = position;
                //     }


                //     KafkaHealthMetric kafkaHealthMetric = new KafkaHealthMetric(tpName, partition,
                //             position,endOffset,Context.now());
                //     task.kafkaHealthMetricsMap.put(kafkaHealthMetric.hashCode()+"", kafkaHealthMetric);
                // }
            }

        } catch (WakeupException ignored) {
          // nothing to catch. This exception is called from the shutdown hook.
        } catch (Exception e) {
            printL(e);
        } finally {
            main.consumer.close();
        }
    }


    private Properties configProperties(String kafkaBrokerUrl, String groupIdConfig, int maxPollRecordsConfig) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBrokerUrl);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, maxPollRecordsConfig);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, groupIdConfig);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return properties;
    }
}
