package com.akto.action;

import com.akto.har.HAR;
import com.akto.listener.KafkaListener;
import com.akto.parsers.HttpCallParser;
import com.akto.parsers.HttpCallParser.HttpResponseParams;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.sun.jna.*;

import org.apache.commons.io.FileUtils;

import de.sstoehr.harreader.HarReaderException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class HarAction extends UserAction {
    private String harString;
    private List<String> harErrors;
    private BasicDBObject content;
    private int apiCollectionId;
    private boolean skipKafka;
    private byte[] tcpContent;

    @Override
    public String execute() throws IOException {
        if (harString == null) {
            harString = this.content.toString();
        }
        List<HttpResponseParams> responseParams = new ArrayList<>();

        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";
        if (harString == null) return ERROR.toUpperCase();
        String origHarString = harString;
        for(int i = 1; i < 2;i++) {
            try {
                HAR har = new HAR();
                harString = origHarString;//.replaceAll("2022-01-03", "2022-01-" + (i>9?i:("0"+i)));

                List<String> messages = har.getMessages(harString, apiCollectionId);
                harErrors = har.getErrors();
                for (String message: messages){
                    
                    try {
                        
                        for(int j = 0; j < 1; j++) {
                            responseParams.add(HttpCallParser.parseKafkaMessage(message));
                            if (!skipKafka) {
                                KafkaListener.kafka.send(message,topic);
                            }
                        }
                        
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }                
                }

            } catch (HarReaderException e) {
                e.printStackTrace();
                return SUCCESS.toUpperCase();
            }
        }

        if (skipKafka) {
            HttpCallParser parser = new HttpCallParser("access-token", 1, 1, 60);
            parser.syncFunction(responseParams);    
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