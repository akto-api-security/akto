package com.akto.threat.backend.dao;

import com.akto.threat.backend.constants.MongoDBCollection;
import com.akto.threat.backend.db.ActorInfoModel;
import com.mongodb.client.MongoCollection;

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
}


