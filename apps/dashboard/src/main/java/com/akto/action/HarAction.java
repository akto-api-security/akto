package com.akto.action;

import com.akto.har.HAR;
import com.akto.listener.KafkaListener;
import com.akto.parsers.HttpCallParser;
import com.akto.parsers.HttpCallParser.HttpResponseParams;
import com.mongodb.BasicDBObject;

import de.sstoehr.harreader.HarReaderException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HarAction extends UserAction {
    private String harString;
    private List<String> harErrors;
    private BasicDBObject content;
    private int apiCollectionId;
    private boolean skipKafka;

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
                harString = origHarString.replaceAll("2022-01-03", "2022-01-" + (i>9?i:("0"+i)));

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
}
