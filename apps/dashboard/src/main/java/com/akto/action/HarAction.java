package com.akto.action;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dto.ApiCollection;
import com.akto.har.HAR;
import com.akto.kafka.Kafka;
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
    private String apiCollectionName;


    @Override
    public String execute() throws IOException {
        if (apiCollectionName != null) {
            ApiCollection apiCollection =  ApiCollectionsDao.instance.findByName(apiCollectionName);
            if (apiCollectionName == null) return ERROR.toUpperCase();
            apiCollectionId = apiCollection.getId();
        }

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
                if (message.length() < 0.8 * Kafka.BATCH_SIZE_CONFIG) {
                    KafkaListener.kafka.send(message,topic);
                } else {
                    harErrors.add("Message too big size: " + message.length());
                }
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

    public void setApiCollectionName(String apiCollectionName) {
        this.apiCollectionName = apiCollectionName;
    }

    public List<String> getHarErrors() {
        return harErrors;
    }
}
