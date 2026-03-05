package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.GuardrailPolicyRecommendation;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.List;

public class GuardrailPolicyRecommendationDao extends AccountsContextDao<GuardrailPolicyRecommendation> {

    public static final String COLLECTION_NAME = "guardrail_policy_recommendations";
    public static final GuardrailPolicyRecommendationDao instance = new GuardrailPolicyRecommendationDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<GuardrailPolicyRecommendation> getClassT() {
        return GuardrailPolicyRecommendation.class;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col : clients[0].getDatabase(Context.accountId.get() + "").listCollectionNames()) {
            if (getCollName().equalsIgnoreCase(col)) {
                exists = true;
                break;
            }
        }

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get() + "").createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{"createdTimestamp"}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{"vulnerabilityNewsUrl"}, false);
    }

    public List<GuardrailPolicyRecommendation> findAllByCreatedTimestamp(int skip, int limit) {
        BasicDBObject sort = new BasicDBObject("createdTimestamp", -1);
        return instance.findAll(Filters.empty(), skip, limit, sort);
    }

    public long countUnseenForAccount(int lastSeenTimestamp) {
        Bson filter = Filters.gt("createdTimestamp", lastSeenTimestamp);
        return instance.getMCollection().countDocuments(filter);
    }
}
