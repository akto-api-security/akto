package com.akto.testing;

import org.bson.types.ObjectId;

import com.akto.dto.billing.SyncLimit;
import com.akto.dto.testing.TestingRun;
import com.akto.kafka.Kafka;
import com.akto.util.Constants;

public class TestingProducer {

    private final Kafka producer = new Kafka(Constants.LOCAL_KAFKA_BROKER_URL, 500, 1000);
    
    public static void performActionAndPushDataToKafka(){
        
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
