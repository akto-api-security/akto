package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.ActorInfoModel;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;

public class ActorInfoDao extends AccountBasedDao<ActorInfoModel> {

    public static final ActorInfoDao instance = new ActorInfoDao();

    private ActorInfoDao() {}

    @Override
    protected String getCollectionName() {
        return MongoDBCollection.ThreatDetection.ACTOR_INFO;
    }

    @Override
    protected Class<ActorInfoModel> getClassType() {
        return ActorInfoModel.class;
    }

    public MongoCollection<ActorInfoModel> getCollection(String accountId) {
        return super.getCollection(accountId);
    }

    public void createIndicesIfAbsent(String accountId) {
        MongoCollection<ActorInfoModel> coll = getCollection(accountId);

        java.util.Set<String> existing = new java.util.HashSet<>();
        try (MongoCursor<Document> it = coll.listIndexes().iterator()) {
            while (it.hasNext()) {
                Document idx = it.next();
                existing.add(idx.getString("name"));
            }
        }

        java.util.Map<String, org.bson.conversions.Bson> required = new java.util.LinkedHashMap<>();

        // Unique index on actorId for upsert operations in FlushMessagesToDB
        required.put("idx_actorId", Indexes.ascending("actorId"));

        required.put("idx_discoveredAt", Indexes.descending("discoveredAt"));
        // Note: idx_lastAttackTs removed - redundant with compound indexes and confuses query planner

        // Compound indexes for optimized queries
        // For listThreatActorsFromActorInfo - cursor pagination with descending sort
        required.put("idx_lastAttackTs_id_desc", Indexes.compoundIndex(
            Indexes.descending("lastAttackTs"),
            Indexes.descending("_id")
        ));

        // For getDailyActorCountsFromActorInfo - Critical actors count
        required.put("idx_lastAttackTs_contextSource_isCritical", Indexes.compoundIndex(
            Indexes.ascending("lastAttackTs"),
            Indexes.ascending("contextSource"),
            Indexes.ascending("isCritical")
        ));

        // For getDailyActorCountsFromActorInfo - Active actors count
        required.put("idx_lastAttackTs_contextSource_status", Indexes.compoundIndex(
            Indexes.ascending("lastAttackTs"),
            Indexes.ascending("contextSource"),
            Indexes.ascending("status")
        ));

        // For getThreatActorByCountryFromActorInfo - Country grouping
        required.put("idx_lastAttackTs_contextSource_country", Indexes.compoundIndex(
            Indexes.ascending("lastAttackTs"),
            Indexes.ascending("contextSource"),
            Indexes.ascending("country")
        ));

        for (java.util.Map.Entry<String, org.bson.conversions.Bson> e : required.entrySet()) {
            if (!existing.contains(e.getKey())) {
                IndexOptions options = new IndexOptions().name(e.getKey());
                // Make actorId index unique
                if ("idx_actorId".equals(e.getKey())) {
                    options.unique(true);
                }
                coll.createIndex(e.getValue(), options);
            }
        }
    }
}


