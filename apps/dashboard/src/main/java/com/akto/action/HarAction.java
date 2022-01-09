package com.akto.action;

import com.akto.har.HAR;
import com.akto.listener.KafkaListener;
import com.mongodb.BasicDBObject;

import de.sstoehr.harreader.HarReaderException;

import java.io.IOException;
import java.util.List;

public class HarAction extends UserAction {
    private String harString;
    private List<String> harErrors;
    private BasicDBObject content;
    private int apiCollectionId;


    @Override
    public String execute() throws IOException {
        if (harString == null) {
            harString = this.content.toString();
        }
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";
        if (harString == null) return ERROR.toUpperCase();
        try {
            HAR har = new HAR();
            List<String> messages = har.getMessages(harString, apiCollectionId);
            harErrors = har.getErrors();
            for (String message: messages){
                KafkaListener.kafka.send(message,topic);
            }
        } catch (HarReaderException e) {
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

    public List<String> getHarErrors() {
        return harErrors;
    }
}
