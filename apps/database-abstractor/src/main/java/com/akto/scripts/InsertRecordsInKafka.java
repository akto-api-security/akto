package com.akto.scripts;

import com.akto.DaoInit;
import com.akto.dao.SampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.KafkaHealthMetric;
import com.akto.dto.traffic.SampleData;
import com.akto.kafka.Kafka;
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
    public static final String KAFKA_URL = "10.100.131.197:9092";

//     public static void main(String[] args) throws InterruptedException {
// //        insertSampleDataIntoKafka();
//         checkKafkaQueueSize(RUNTIME_TOPIC, "asdf", KAFKA_URL);
//         //209447
//         try {
//             Thread.sleep(10_000);
//         } catch (InterruptedException e) {
//             throw new RuntimeException(e);
//         }
//     }

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


    static Kafka kafka = null;
    public static void initKafka(String kafkaBrokerUrl){
        if(kafka == null){
            kafka = new Kafka(kafkaBrokerUrl, 1000, 999900);
        }
    }
    public static void insertRandomRecords(int x, String kafkaBrokerUrl) throws InterruptedException {
        initKafka(kafkaBrokerUrl);

        for (int apiCollectionId=x; apiCollectionId < x+10; apiCollectionId++ ) {
            for (String url: Arrays.asList("a", "b", "c", "d")) {
                for (String method: Arrays.asList("GET")) {
                    for (int ip=0; ip<3; ip++) {
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

        // Thread.sleep(5000);
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
        return "[\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02bc50626052c4dc801\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 0,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"05671768-92d7-4d36-9b50-4e510d66d726\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": true,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$1,414.78\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"patsywalters@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Officia reprehenderit esse excepteur velit adipisicing cillum officia aute deserunt nisi. Ut dolor cillum Lorem voluptate enim eu exercitation in Lorem dolore. Fugiat ullamco ex occaecat amet quis nostrud nostrud labore non. Dolore quis ex commodo qui occaecat labore cupidatat cillum esse deserunt. Reprehenderit et aliquip et nisi excepteur exercitation laboris et aute culpa duis enim sit cillum. Excepteur elit qui non amet cillum ullamco veniam.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2016-12-03T05:25:59 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": 22.458589,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": 39.964539,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"eiusmod\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Finley Wilder\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 2 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"banana\\\\\\\"\\\\r\\\\n" + //
                        "  },\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02b69ac2f6cd5800f03\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 1,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"4c1b5d05-3ef6-4ec6-b438-1293b4c58820\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": false,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$2,476.35\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"finleywilder@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Magna exercitation aliquip in elit reprehenderit eiusmod voluptate dolor duis. Ipsum do ea laborum aliquip qui adipisicing. Laboris ex magna excepteur incididunt.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2017-02-10T06:47:47 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": -40.794951,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": -143.320167,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"excepteur\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Madden Hogan\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 1 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"apple\\\\\\\"\\\\r\\\\n" + //
                        "  },\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02bde400f9e7acdb95d\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 2,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"0e541ba0-c348-4795-a859-a8256dcf7cee\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": false,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$1,923.00\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"maddenhogan@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Esse nostrud labore tempor do Lorem exercitation aliqua magna aliquip anim est adipisicing. Voluptate aliqua esse laboris velit ex minim dolore sunt labore. Voluptate et nulla dolore ullamco qui dolor exercitation minim ullamco. Ipsum commodo est ex elit enim velit ipsum ullamco nulla. Nulla ut magna exercitation cupidatat.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2014-01-17T04:06:09 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": -21.829806,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": 131.654867,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"quis\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Dawn Koch\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 2 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"apple\\\\\\\"\\\\r\\\\n" + //
                        "  },\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02b1dc23f831b8be2c4\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 3,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"3c5fafb7-9ccf-485f-b8e1-241a2b3b6495\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": false,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$3,636.37\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"dawnkoch@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Culpa sit in pariatur sint fugiat eiusmod consequat. Adipisicing laboris ex tempor do duis labore sit laboris proident ullamco tempor cillum nostrud. Ut ad sunt irure eiusmod adipisicing.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2019-01-10T01:58:09 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": 75.595992,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": 137.559147,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"excepteur\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Fuentes Copeland\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 2 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"banana\\\\\\\"\\\\r\\\\n" + //
                        "  },\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02b7b1764f9ce1ff3ff\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 4,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"d6aebf4a-2977-4563-85c0-13cd29fa83d5\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": false,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$1,802.93\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"fuentescopeland@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Minim cillum fugiat reprehenderit ad ullamco nulla quis duis proident. Consequat ea laborum labore proident labore ad cupidatat mollit qui pariatur do sit. Anim nulla excepteur amet culpa amet cupidatat. Veniam excepteur sint ipsum veniam. Et qui anim sit laboris sit deserunt.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2018-12-11T03:49:52 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": 50.587289,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": 174.866178,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"ad\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Olive Hawkins\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 1 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"strawberry\\\\\\\"\\\\r\\\\n" + //
                        "  },\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02b48eccc1629cb49db\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 5,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"30cec9bd-9759-47e9-8151-3b2761171fd8\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": false,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$2,389.36\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"olivehawkins@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Labore cillum sint officia consectetur aliquip esse deserunt. Irure dolor nostrud incididunt esse do amet pariatur. Esse non nostrud ad pariatur velit veniam esse ipsum irure pariatur. Culpa sunt enim ad amet magna mollit mollit enim. Adipisicing sit eiusmod cillum laborum et officia. Culpa minim enim consectetur ipsum duis qui dolor occaecat dolore sunt veniam.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2021-06-30T06:02:55 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": -50.713783,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": 136.127531,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"veniam\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Lesley Romero\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 1 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"apple\\\\\\\"\\\\r\\\\n" + //
                        "  },\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02b0694c29251a08858\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 6,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"11263d43-fc5f-4d96-a166-de747adaf9b2\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": true,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$3,632.86\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"lesleyromero@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Dolore Lorem qui et qui anim do esse irure occaecat ipsum commodo laborum duis. Lorem voluptate adipisicing aliquip nostrud. Minim magna sit velit voluptate eiusmod. Nostrud commodo consectetur est mollit elit excepteur duis nisi in consequat Lorem deserunt Lorem. Laborum nulla cillum deserunt consectetur.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2014-06-29T02:45:43 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": -83.177188,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": 90.931296,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"nulla\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Susan Mcknight\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 2 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"banana\\\\\\\"\\\\r\\\\n" + //
                        "  },\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02b4e9704d45e6d6b4c\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 7,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"0bfe39f9-123c-4342-b37c-593be0e39c06\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": true,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$1,112.03\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"susanmcknight@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Ea dolore cillum do ad esse consequat. Sint ipsum culpa consectetur ut labore sint pariatur elit. Dolor ut ea excepteur nostrud ex pariatur sint amet laborum laboris dolore veniam.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2015-09-29T01:39:09 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": -24.028806,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": -34.039247,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"magna\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Hays Glenn\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 1 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"banana\\\\\\\"\\\\r\\\\n" + //
                        "  },\\\\r\\\\n" + //
                        "  {\\\\r\\\\n" + //
                        "    \\\\\\\"_id\\\\\\\": \\\\\\\"6697a02b89db068fe7e0a66f\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"index\\\\\\\": 8,\\\\r\\\\n" + //
                        "    \\\\\\\"guid\\\\\\\": \\\\\\\"178f1767-b009-4684-bd9f-d6f222ef6544\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"isActive\\\\\\\": false,\\\\r\\\\n" + //
                        "    \\\\\\\"balance\\\\\\\": \\\\\\\"$1,413.95\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"picture\\\\\\\": \\\\\\\"http:\\\\\\/\\\\\\/placehold.it\\\\\\/32x32\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"email\\\\\\\": \\\\\\\"haysglenn@comcubine.com\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"about\\\\\\\": \\\\\\\"Ex pariatur est duis sit aute aliqua sunt irure tempor elit. Laboris sit pariatur incididunt qui veniam officia elit et non est aute. Consequat est occaecat dolore minim veniam ad. Occaecat est mollit veniam esse proident laboris. Elit sit consectetur labore commodo reprehenderit irure do. Excepteur irure reprehenderit do qui fugiat enim velit officia.\\\\\\\\r\\\\\\\\n" + //
                        "\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"registered\\\\\\\": \\\\\\\"2016-04-03T08:00:04 -06:-30\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"latitude\\\\\\\": 46.837897,\\\\r\\\\n" + //
                        "    \\\\\\\"longitude\\\\\\\": -105.117414,\\\\r\\\\n" + //
                        "    \\\\\\\"tags\\\\\\\": [\\\\r\\\\n" + //
                        "      \\\\\\\"fugiat\\\\\\\"\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"friends\\\\\\\": [\\\\r\\\\n" + //
                        "      {\\\\r\\\\n" + //
                        "        \\\\\\\"id\\\\\\\": 0,\\\\r\\\\n" + //
                        "        \\\\\\\"name\\\\\\\": \\\\\\\"Elena Anthony\\\\\\\"\\\\r\\\\n" + //
                        "      }\\\\r\\\\n" + //
                        "    ],\\\\r\\\\n" + //
                        "    \\\\\\\"greeting\\\\\\\": \\\\\\\"Hello, undefined! You have 2 unread messages.\\\\\\\",\\\\r\\\\n" + //
                        "    \\\\\\\"favoriteFruit\\\\\\\": \\\\\\\"apple\\\\\\\"\\\\r\\\\n" + //
                        "  }\\\\r\\\\n" + //
                        "]";
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

    // public static void checkSize() throws InterruptedException {
    //     String kafkaBrokerUrl = "localhost:29092";
    //     checkKafkaQueueSize(RUNTIME_TOPIC,"asdf", kafkaBrokerUrl);

    //     while (true) {
    //         Thread.sleep(10_000);
    //         System.out.println("ALIVE....");
    //     }
    // }

    // public static void checkKafkaQueueSize(String topicName, String groupIdConfig, String kafkaBrokerUrl) {
    //     Properties properties = Main.configProperties(kafkaBrokerUrl, groupIdConfig, 1000);
    //     Consumer<String, String> consumer = new KafkaConsumer<>(properties);
    //     consumer.subscribe(Collections.singleton(topicName));
    //     ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(10000));

    //     System.out.println(consumer.assignment().size());
    //     for (TopicPartition tp: consumer.assignment()) {
    //         String tpName = tp.topic();
    //         System.out.println(tpName);
    //         long endOffset = consumer.endOffsets(Collections.singleton(tp)).get(tp);
    //         System.out.println(endOffset);
    //         System.out.println(" ");
    //     }

    //     consumer.close();
    // }
}
