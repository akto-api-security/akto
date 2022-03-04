package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.har.HAR;
import com.akto.kafka.Kafka;
import com.akto.listener.KafkaListener;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.policies.AktoPolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.akto.dto.HttpResponseParams;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.sun.jna.*;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class HarAction extends UserAction {
    private String harString;
    private List<String> harErrors;
    private BasicDBObject content;
    private int apiCollectionId;
    private String apiCollectionName;

    private boolean skipKafka;
    private byte[] tcpContent;

    public static void main(String[] args) throws InterruptedException, JsonProcessingException {
        String brokerIP = "172.18.0.5:9092";
        Kafka kafka = new Kafka(brokerIP);

        // Map<String, Object> m = new HashMap<>();
        // m.put("group_name", "adsf");
        // m.put("vxlanId", 1234);
        // m.put("vpc_cidr", Arrays.asList("192.1.1.1/16"));
        // ObjectMapper mapper = new ObjectMapper();
        // String message_initial = mapper.writeValueAsString(m);
        // kafka.send(message_initial, "akto.api.logs");



        String message = "{\"method\":\"GET\",\"requestPayload\":\"{\\\"steatus\\\":\\\"pending\\\"}\",\"responsePayload\":\"[{\\\"id\\\":2799412,\\\"category\\\":{\\\"id\\\":19475553,\\\"name\\\":\\\"nostrud cupidatat labore\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"culpa occaecat\\\",\\\"ad incididunt\\\"],\\\"tags\\\":[{\\\"id\\\":47291519,\\\"name\\\":\\\"consequat aute pariatur\\\"},{\\\"id\\\":-53726990,\\\"name\\\":\\\"Lorem enim consectetur\\\"}],\\\"status\\\":\\\"pending\\\"},{\\\"id\\\":1641165953,\\\"category\\\":{\\\"id\\\":1,\\\"name\\\":\\\"categoryNameUpdated\\\"},\\\"name\\\":\\\"Lauren\\\",\\\"photoUrls\\\":[\\\"photoUrl1Updated\\\",\\\"photoUrl2Updated\\\"],\\\"tags\\\":[{\\\"id\\\":34932781,\\\"name\\\":\\\"Ut dolore\\\"},{\\\"id\\\":93600857,\\\"name\\\":\\\"sint\\\"}],\\\"status\\\":\\\"pending\\\"},{\\\"id\\\":1641165973,\\\"category\\\":{\\\"id\\\":1,\\\"name\\\":\\\"categoryNameUpdated\\\"},\\\"name\\\":\\\"Alanis\\\",\\\"photoUrls\\\":[\\\"photoUrl1Updated\\\",\\\"photoUrl2Updated\\\"],\\\"tags\\\":[{\\\"id\\\":34932781,\\\"name\\\":\\\"Ut dolore\\\"},{\\\"id\\\":93600857,\\\"name\\\":\\\"sint\\\"}],\\\"status\\\":\\\"pending\\\"},{\\\"id\\\":81723614,\\\"category\\\":{\\\"id\\\":-35894100,\\\"name\\\":\\\"sit reprehenderit fugiat amet aliqua\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"occaecat nostrud dolore fugiat tempor\\\",\\\"id est\\\"],\\\"tags\\\":[{\\\"id\\\":-85885711,\\\"name\\\":\\\"pariatur labore Lorem\\\"},{\\\"id\\\":-37987020,\\\"name\\\":\\\"v\\\"}],\\\"status\\\":\\\"pending\\\"},{\\\"id\\\":39185211,\\\"cateagory\\\":{\\\"id\\\":12899832,\\\"name\\\":\\\"anim enim elit incididunt\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"adipisicing in\\\",\\\"pariatur veniam do\\\"],\\\"tags\\\":[{\\\"id\\\":-97403520,\\\"namee\\\":\\\"dolore minim\\\"},{\\\"id\\\":53200598,\\\"name\\\":\\\"in dolore\\\"}],\\\"status\\\":\\\"pending\\\"}]\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"0\",\"path\":\"https://petstore.swagger.io/v1/books\",\"requestHeaders\":\"{\\\"TE\\\":\\\"trailers\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Mon, 03 Jan 2022 07:16:32 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641194192\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"201\",\"status\":\"OK\"}";
        String message1 = "{\"method\":\"GET\",\"requestPayload\":\"{\\\"stebtus\\\":\\\"pending\\\"}\",\"responsePayload\":\"[{\\\"id\\\":2799412,\\\"category\\\":{\\\"id\\\":19475553,\\\"name\\\":\\\"nostrud cupidatat labore\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"culpa occaecat\\\",\\\"ad incididunt\\\"],\\\"tags\\\":[{\\\"id\\\":47291519,\\\"name\\\":\\\"consequat aute pariatur\\\"},{\\\"id\\\":-53726990,\\\"name\\\":\\\"Lorem enim consectetur\\\"}],\\\"status\\\":\\\"pending\\\"},{\\\"id\\\":1641165953,\\\"category\\\":{\\\"id\\\":1,\\\"name\\\":\\\"categoryNameUpdated\\\"},\\\"name\\\":\\\"Lauren\\\",\\\"photoUrls\\\":[\\\"photoUrl1Updated\\\",\\\"photoUrl2Updated\\\"],\\\"tags\\\":[{\\\"id\\\":34932781,\\\"name\\\":\\\"Ut dolore\\\"},{\\\"id\\\":93600857,\\\"name\\\":\\\"sint\\\"}],\\\"status\\\":\\\"pending\\\"},{\\\"id\\\":1641165973,\\\"category\\\":{\\\"id\\\":1,\\\"name\\\":\\\"categoryNameUpdated\\\"},\\\"name\\\":\\\"Alanis\\\",\\\"photoUrls\\\":[\\\"photoUrl1Updated\\\",\\\"photoUrl2Updated\\\"],\\\"tags\\\":[{\\\"id\\\":34932781,\\\"name\\\":\\\"Ut dolore\\\"},{\\\"id\\\":93600857,\\\"name\\\":\\\"sint\\\"}],\\\"status\\\":\\\"pending\\\"},{\\\"id\\\":81723614,\\\"category\\\":{\\\"id\\\":-35894100,\\\"name\\\":\\\"sit reprehenderit fugiat amet aliqua\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"occaecat nostrud dolore fugiat tempor\\\",\\\"id est\\\"],\\\"tags\\\":[{\\\"id\\\":-85885711,\\\"name\\\":\\\"pariatur labore Lorem\\\"},{\\\"id\\\":-37987020,\\\"name\\\":\\\"v\\\"}],\\\"status\\\":\\\"pending\\\"},{\\\"id\\\":39185211,\\\"cateagory\\\":{\\\"id\\\":12899832,\\\"name\\\":\\\"anim enim elit incididunt\\\"},\\\"name\\\":\\\"doggie\\\",\\\"photoUrls\\\":[\\\"adipisicing in\\\",\\\"pariatur veniam do\\\"],\\\"tags\\\":[{\\\"id\\\":-97403520,\\\"namee\\\":\\\"dolore minim\\\"},{\\\"id\\\":53200598,\\\"name\\\":\\\"in dolore\\\"}],\\\"status\\\":\\\"pending\\\"}]\",\"ip\":\"null\",\"source\":\"MIRRORING\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"0\",\"path\":\"https://petstore.swagger.io/v1/car/findByStatus\",\"requestHeaders\":\"{\\\"TE\\\":\\\"trailers\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"Mon, 03 Jan 2022 07:16:32 GMT\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641194192\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"201\",\"status\":\"OK\"}";
        for (int i=0;i < 10; i++) {
            if (i == 15 || i == 29) {
                System.out.println("sss");
                kafka.send(message1,"akto.api.logs");
            }
            kafka.send(message,"akto.api.logs");
            Thread.sleep(5000);
        }
        Thread.sleep(5000);
    }

    @Override
    public String execute() throws IOException {
        if (apiCollectionName != null) {
            ApiCollection apiCollection =  ApiCollectionsDao.instance.findByName(apiCollectionName);
            if (apiCollection == null) {
                addActionError("Invalid collection name");
                return ERROR.toUpperCase();
            }
            apiCollectionId = apiCollection.getId();
        }

        if (harString == null) {
            harString = this.content.toString();
        }
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";
        if (harString == null) {
            addActionError("Empty content");
            return ERROR.toUpperCase();
        }
        HttpCallParser parser = new HttpCallParser("userIdentifier", 1, 1, 1);

        try {
            HAR har = new HAR();
            List<String> messages = har.getMessages(harString, apiCollectionId);
            harErrors = har.getErrors();
            List<HttpResponseParams> responses = new ArrayList<>();
            for (String message: messages){
                if (message.length() < 0.8 * Kafka.BATCH_SIZE_CONFIG) {
                    if (!skipKafka) {
                        KafkaListener.kafka.send(message,topic);
                    } else {
                        HttpResponseParams responseParams =  HttpCallParser.parseKafkaMessage(message);
                        responseParams.getRequestParams().setApiCollectionId(apiCollectionId);
                        responses.add(responseParams);
                    }
                } else {
                    harErrors.add("Message too big size: " + message.length());
                }
            }
            
            // AktoPolicy aktoPolicy = new AktoPolicy();
            if(skipKafka)
                parser.syncFunction(responses);
                // aktoPolicy.main(responses);
        } catch (Exception e) {
            e.printStackTrace();
            return SUCCESS.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public void setContent(BasicDBObject content) {
        this.content = content;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public void setHarString(String harString) {
        this.harString = harString;
    }

    public void setApiCollectionName(String apiCollectionName) {
        this.apiCollectionName = apiCollectionName;
    }

    public List<String> getHarErrors() {
        return harErrors;
    }

    public boolean getSkipKafka() {
        return this.skipKafka;
    }

    public void setSkipKafka(boolean skipKafka) {
        this.skipKafka = skipKafka;
    }

    public void setTcpContent(byte[] tcpContent) {
        this.tcpContent = tcpContent;
    }

    Awesome awesome = null;

    public String uploadTcp() {
        
        File tmpDir = FileUtils.getTempDirectory();
        String filename = UUID.randomUUID().toString() + ".pcap";
        File tcpDump = new File(tmpDir, filename);
        try {
            FileUtils.writeByteArrayToFile(tcpDump, tcpContent);
            Awesome awesome =  (Awesome) Native.load("awesome", Awesome.class);
            Awesome.GoString.ByValue str = new Awesome.GoString.ByValue();
            str.p = tcpDump.getAbsolutePath();
            str.n = str.p.length();
    
            Awesome.GoString.ByValue str2 = new Awesome.GoString.ByValue();
            str2.p = System.getenv("AKTO_KAFKA_BROKER_URL");
            str2.n = str2.p.length();
    
            awesome.readTcpDumpFile(str, str2 , apiCollectionId);
    
            return Action.SUCCESS.toUpperCase();            
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return Action.ERROR.toUpperCase();        
        }

    }

    interface Awesome extends Library {          
        public static class GoString extends Structure {
            /** C type : const char* */
            public String p;
            public long n;
            public GoString() {
                super();
            }
            protected List<String> getFieldOrder() {
                return Arrays.asList("p", "n");
            }
            /** @param p C type : const char* */
            public GoString(String p, long n) {
                super();
                this.p = p;
                this.n = n;
            }
            public static class ByReference extends GoString implements Structure.ByReference {}
            public static class ByValue extends GoString implements Structure.ByValue {}
        }
        
        public void readTcpDumpFile(GoString.ByValue filepath, GoString.ByValue kafkaURL, long apiCollectionId);
        
    }
}