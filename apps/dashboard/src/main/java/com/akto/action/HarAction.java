package com.akto.action;

import com.akto.har.HAR;
import com.akto.listener.KafkaListener;
import de.sstoehr.harreader.HarReaderException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HarAction extends UserAction {
    private String harString;
    private List<String> harErrors;


    @Override
    public String execute() throws IOException {
//        File file = new File("/home/avneesh/Downloads/localhost_Archive [21-12-28 18-01-36].har");
//        harString = FileUtils.readFileToString(file, StandardCharsets.UTF_8.toString());
//        System.out.println(harString.length());
        String topic = System.getenv("AKTO_KAFKA_TOPIC_NAME");
        if (topic == null) topic = "akto.api.logs";
        if (harString == null) return ERROR.toUpperCase();

        try {
            HAR har = new HAR();
            List<String> messages = har.getMessages(harString);
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

    public void setHarString(String harString) {
        this.harString = harString;
    }

    public List<String> getHarErrors() {
        return harErrors;
    }
}
