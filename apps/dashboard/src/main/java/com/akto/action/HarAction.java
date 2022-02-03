package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.har.HAR;
import com.akto.kafka.Kafka;
import com.akto.listener.KafkaListener;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.sun.jna.*;

import org.apache.commons.io.FileUtils;

import de.sstoehr.harreader.HarReaderException;

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

        try {
            HAR har = new HAR();
            List<String> messages = har.getMessages(harString, apiCollectionId);
            harErrors = har.getErrors();
            for (String message: messages){
                if (message.length() < 0.8 * Kafka.BATCH_SIZE_CONFIG) {
                    KafkaListener.kafka.send(message,topic);
                } else {
                    harErrors.add("Message too big size: " + message.length());
                }
            }

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