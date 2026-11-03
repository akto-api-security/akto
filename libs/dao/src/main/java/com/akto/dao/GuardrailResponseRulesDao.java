package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.GuardrailResponseRule;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.List;

public class GuardrailResponseRulesDao extends AccountsContextDao<GuardrailResponseRule> {
    public static final String COLLECTION_NAME = "guardrail_response_rules";
    public static final GuardrailResponseRulesDao instance = new GuardrailResponseRulesDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<GuardrailResponseRule> getClassT() {
        return GuardrailResponseRule.class;
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
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{"contextSource", "createdTimestamp"}, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[]{"active"}, false);
    }

    public List<GuardrailResponseRule> findPage(int skip, int limit) {
        BasicDBObject sort = new BasicDBObject("createdTimestamp", -1);
        return instance.findAll(contextSourceFilter(), skip, limit, sort);
    }

    public long totalCount() {
        return getMCollection().countDocuments(contextSourceFilter());
    }

    public Bson contextSourceFilter() {
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        if (contextSource == null || contextSource == CONTEXT_SOURCE.AGENTIC) {
            return Filters.or(
                Filters.eq("contextSource", CONTEXT_SOURCE.AGENTIC),
                Filters.exists("contextSource", false)
            );
        }
        return Filters.eq("contextSource", contextSource);
    }
}
