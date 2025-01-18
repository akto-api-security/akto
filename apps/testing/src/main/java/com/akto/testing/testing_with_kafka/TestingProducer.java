package com.akto.testing.testing_with_kafka;

import java.util.List;

import org.bson.types.ObjectId;

import com.akto.dto.billing.SyncLimit;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.info.TestMessages;
import com.akto.kafka.Kafka;
import com.akto.testing.TestExecutor;
import com.akto.util.Constants;

public class TestingProducer {

    public static final Kafka producer = new Kafka(Constants.LOCAL_KAFKA_BROKER_URL, 500, 1000);
    
    public static Void pushMessagesToKafka(List<TestMessages> messages){
        for(TestMessages testMessages: messages){
            String messageString = testMessages.toString();
            producer.send(messageString, Constants.TEST_RESULTS_TOPIC_NAME);
        }
        return null;
    }

    private boolean shouldClearKafkaRecords(TestingRun testingRun, ObjectId summaryId){
        return false;
    }

    private boolean isKafkaEmpty(){
        return true;
    }

    public void initProducer(TestingRun testingRun, ObjectId summaryId, SyncLimit syncLimit){
        TestExecutor executor = new TestExecutor();

        boolean doInitOnly = !isKafkaEmpty();
        executor.init(testingRun, summaryId, syncLimit, doInitOnly);
    }
}
