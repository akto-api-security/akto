package com.akto.threat.protection.db;

import java.util.ArrayList;
import java.util.List;

import com.akto.proto.threat_protection.consumer_service.v1.MaliciousEvent;
import com.akto.proto.threat_protection.consumer_service.v1.SmartEvent;
import com.mongodb.client.MongoClient;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.InsertOneModel;
import com.mongodb.client.model.WriteModel;

public class DBService {

    // We are doing this way instead of using DaoInit.init since we are using
    // separate mongo for saving events.
    // Current Dao approach doesnt work well with 2 separate mongo connections.
    private final MongoClient mongoClient;

    public DBService(MongoClient client) {
        this.mongoClient = client;
    }

    public void saveMaliciousEvents(String db, Iterable<MaliciousEvent> events) {
        List<WriteModel<MaliciousEventModel>> bulkUpdates = new ArrayList<>();
        events.forEach(event -> {
            bulkUpdates.add(
                    new InsertOneModel<>(
                            new MaliciousEventModel(
                                    event.getFilterId(),
                                    event.getActorId(),
                                    event.getIp(),
                                    event.getUrl(),
                                    event.getMethod(),
                                    event.getPayload(),
                                    event.getTimestamp())));
        });
        this.mongoClient
                .getDatabase(db)
                .getCollection("malicious_events", MaliciousEventModel.class)
                .bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
    }

    public void saveSmartEvent(String db, SmartEvent events) {
        this.mongoClient
                .getDatabase(db)
                .getCollection("smart_events", SmartEventModel.class)
                .insertOne(
                        new SmartEventModel(
                                events.getFilterId(),
                                events.getActorId(),
                                events.getDetectedAt()));
    }
}
