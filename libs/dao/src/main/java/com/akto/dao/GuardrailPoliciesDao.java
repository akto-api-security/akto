package com.akto.dao;

import com.akto.dto.GuardrailPolicies;
import com.akto.dao.context.Context;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

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
        Bson filter = getContextSourceFilter();
        BasicDBObject sort = new BasicDBObject();
        sort.put("createdTimestamp", -1); // descending order (newest first)
        return instance.findAll(filter, skip, limit, sort);
    }

    public long getTotalCount() {
        Bson filter = getContextSourceFilter();
        return this.getMCollection().countDocuments(filter);
    }

    private Bson getContextSourceFilter() {
        // Get current context source, default to ENDPOINT if not set
        CONTEXT_SOURCE contextSource = Context.contextSource.get();
        if (contextSource == null) {
            contextSource = CONTEXT_SOURCE.ENDPOINT;
        }

        // Build filter: contextSource matches OR contextSource doesn't exist (defaults to ENDPOINT)
        if (contextSource == CONTEXT_SOURCE.ENDPOINT) {
            // For ENDPOINT, include records where contextSource is ENDPOINT OR doesn't exist
            return Filters.or(
                Filters.eq("contextSource", contextSource),
                Filters.exists("contextSource", false)
            );
        } else {
            // For other contexts, only include records with exact contextSource match
            return Filters.eq("contextSource", contextSource);
        }
    }
}