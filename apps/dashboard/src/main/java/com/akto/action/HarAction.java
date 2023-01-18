package com.akto.action;

import com.akto.DaoInit;
import com.akto.analyser.ResourceAnalyser;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.RuntimeFilterDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.har.HAR;
import com.akto.listener.KafkaListener;
import com.akto.parsers.HttpCallParser;
import com.akto.runtime.APICatalogSync;
import com.akto.runtime.policies.AktoPolicy;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.utils.DashboardMode;
import com.akto.utils.Utils;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
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

    private boolean skipKafka = DashboardMode.isLocalDeployment();
    private byte[] tcpContent;

    public static void main(String[] args) {
        // messages.add("{\"method\":\"POST\",\"requestPayload\":\"{\\\"petId\\\":1,\\\"quantity\\\":0,\\\"id\\\":0,\\\"shipDate\\\":\\\"2022-01-04T20:10:16.578Z\\\",\\\"complete\\\":true,\\\"status\\\":\\\"avnebbesh@akto.io\\\"}\",\"responsePayload\":\"{\\\"id\\\":9223372036854772476,\\\"petId\\\":0,\\\"quantity\\\":0,\\\"shipDate\\\":\\\"2022-01-04T20:10:16.578+0000\\\",\\\"status\\\":\\\"av@gmail.com\\\",\\\"complete\\\":true, \\\"avav\\\": \\\"eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJBa3RvIiwic3ViIjoicmVmcmVzaFRva2VuIiwic2lnbmVkVXAiOiJ0cnVlIiwidXNlcm5hbWUiOiJhbmtpdGFAZ21haWwuY29tIiwiaWF0IjoxNjM0OTcxMTMxLCJleHAiOjE2MzUwNTc1MzF9.Ph4Jv-fdggwvnbdVViD9BWUReYL0dVfVGuMRz4d2oZNnYzWV0JCmjpB68p6k0yyPPua_yagIWVZf_oYH9PUgS7EuaPYR-Vg6uxKR1HuXRA6wb8Xf4RPoFjJYkhWoYmv38V9Cz2My9U85wgGHGZXEufu8ubrFmIfOP6-A39M4meNGw48f5oOz8V337SX45uPc6jE0EfmM4l9EbqFFCF0lRXbMMzn-ijsyXxLkI5npWnqtW3PAHC2Rs3FV40tkRqHYF-WM6SzyHLBh6bVeyeOsFRBoEjv-zFh8yrYnT6OvCa6jII2A6uj4MQ2k11-5bDBhfVPVc4hEQz37H_DWwtf23g\\\"}\",\"ip\":\"null\",\"source\":\"HAR\",\"type\":\"HTTP/2\",\"akto_vxlan_id\":\"0\",\"path\":\"https://petstore.swagger.io/v2/store/order/1\",\"requestHeaders\":\"{\\\"Origin\\\":\\\"avneesh@gmail.com\\\",\\\"Accept\\\":\\\"application/json\\\",\\\"User-Agent\\\":\\\"Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:95.0) Gecko/20100101 Firefox/95.0\\\",\\\"Referer\\\":\\\"https://petstore.swagger.io/\\\",\\\"Connection\\\":\\\"keep-alive\\\",\\\"Sec-Fetch-Dest\\\":\\\"empty\\\",\\\"Sec-Fetch-Site\\\":\\\"same-origin\\\",\\\"Host\\\":\\\"petstore.swagger.io\\\",\\\"Accept-Encoding\\\":\\\"gzip, deflate, br\\\",\\\"Sec-Fetch-Mode\\\":\\\"cors\\\",\\\"TE\\\":\\\"trailers\\\",\\\"Accept-Language\\\":\\\"en-US,en;q=0.5\\\",\\\"Content-Length\\\":\\\"128\\\",\\\"Content-Type\\\":\\\"application/json\\\"}\",\"responseHeaders\":\"{\\\"date\\\":\\\"+917021916328\\\",\\\"access-control-allow-origin\\\":\\\"*\\\",\\\"server\\\":\\\"Jetty(9.2.9.v20150224)\\\",\\\"access-control-allow-headers\\\":\\\"Content-Type, api_key, Authorization\\\",\\\"X-Firefox-Spdy\\\":\\\"h2\\\",\\\"content-type\\\":\\\"application/json\\\",\\\"access-control-allow-methods\\\":\\\"GET, POST, DELETE, PUT\\\"}\",\"time\":\"1641327021\",\"contentType\":\"application/json\",\"akto_account_id\":\"1000000\",\"statusCode\":\"200\",\"status\":\"OK\"}");
        DaoInit.init(new ConnectionString("mongodb://localhost:27017/admini"));
        Context.accountId.set(1_000_000);
        RuntimeFilterDao.instance.initialiseFilters();
        System.out.println(RuntimeFilterDao.instance.findAll(new BasicDBObject()));
        ApiCollectionsDao.instance.insertOne(new ApiCollection(0, "Default", Context.now(), new HashSet<>(), null,0));
    }

    @Override
    public String execute() throws IOException {
        ApiCollection apiCollection = null;
        if (apiCollectionName != null) {
            apiCollection =  ApiCollectionsDao.instance.findByName(apiCollectionName);
            apiCollectionId = apiCollection.getId();
        } else {
            apiCollection =  ApiCollectionsDao.instance.findOne(Filters.eq("_id", apiCollectionId));
        }

        if (apiCollection == null) {
            addActionError("Invalid collection name");
            return ERROR.toUpperCase();
        }

        if (apiCollection.getHostName() != null)  {
            addActionError("Traffic mirroring collection can't be used");
            return ERROR.toUpperCase();
        }

        if (KafkaListener.kafka == null) {
            addActionError("Dashboard kafka not running");
            return ERROR.toUpperCase();
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

        try {
            HAR har = new HAR();
            List<String> messages = har.getMessages(harString, apiCollectionId);
            harErrors = har.getErrors();
            Utils.pushDataToKafka(apiCollectionId, topic, messages, harErrors, skipKafka);
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