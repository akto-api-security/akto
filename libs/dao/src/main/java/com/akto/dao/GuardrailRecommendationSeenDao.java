package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.GuardrailRecommendationSeen;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

public class GuardrailRecommendationSeenDao extends AccountsContextDao<GuardrailRecommendationSeen> {

    public static final String COLLECTION_NAME = "guardrail_recommendation_seen";
    public static final GuardrailRecommendationSeenDao instance = new GuardrailRecommendationSeenDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<GuardrailRecommendationSeen> getClassT() {
        return GuardrailRecommendationSeen.class;
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
    }

    public int getLastSeenTimestamp() {
        GuardrailRecommendationSeen doc = instance.findOne(Filters.eq("_id", GuardrailRecommendationSeen.ID_VALUE));
        return doc != null ? doc.getLastSeenTimestamp() : 0;
    }

    public void setLastSeenTimestamp(int timestamp) {
        Bson filter = Filters.eq("_id", GuardrailRecommendationSeen.ID_VALUE);
        GuardrailRecommendationSeen doc = instance.findOne(filter);
        if (doc == null) {
            doc = new GuardrailRecommendationSeen();
            doc.setLastSeenTimestamp(timestamp);
            instance.insertOne(doc);
        } else {
            instance.updateOne(filter, Updates.set(GuardrailRecommendationSeen.LAST_SEEN_TIMESTAMP, timestamp));
        }
    }
}
