package com.akto.dao;

import com.akto.dto.GuardrailPolicies;
import com.akto.dao.context.Context;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;

import java.util.ArrayList;
import java.util.List;

public class GuardrailPoliciesDao extends AccountsContextDao<GuardrailPolicies> {
    public static final String COLLECTION_NAME = "guardrail_policies";
    public static final GuardrailPoliciesDao instance = new GuardrailPoliciesDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<GuardrailPolicies> getClassT() {
        return GuardrailPolicies.class;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        }

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {"createdTimestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"updatedTimestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
        
        fieldNames = new String[]{"severity"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"isActive"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public List<GuardrailPolicies> findAllSortedByCreatedTimestamp(int skip, int limit) {
        BasicDBObject sort = new BasicDBObject();
        sort.put("createdTimestamp", -1); // descending order (newest first)
        return instance.findAll(Filters.empty(), skip, limit, sort);
    }

    public long getTotalCount() {
        return this.getMCollection().countDocuments();
    }
}