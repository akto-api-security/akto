package com.akto.dao;

import com.akto.dto.GuardrailPolicies;
import com.akto.dao.context.Context;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
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

        fieldNames = new String[]{"contextSource", "updatedTimestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"systemGuardrail", "createdTimestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public GuardrailPolicies findByNameAndContextSourceAndSystemGuardrail(String name, CONTEXT_SOURCE contextSource, boolean systemGuardrail) {
        Bson filter = Filters.and(
                Filters.eq("name", name),
                Filters.eq("contextSource", contextSource),
                Filters.eq("systemGuardrail", systemGuardrail)
        );
        return instance.findOne(filter);
    }

    /** One query to find existing system guardrails for given names and context sources (projection: name, contextSource). */
    public List<GuardrailPolicies> findSystemGuardrails(List<String> names, List<CONTEXT_SOURCE> contextSources) {
        Bson filter = Filters.and(
                Filters.eq("systemGuardrail", true),
                Filters.in("name", names),
                Filters.in("contextSource", contextSources)
        );
        Bson projection = Projections.include("name", "contextSource");
        return instance.findAll(filter, 0, names.size() * contextSources.size(), null, projection);
    }

    public List<GuardrailPolicies> findAllSortedByCreatedTimestamp(int skip, int limit) {
        Bson filter = getContextSourceFilter();
        BasicDBObject sort = new BasicDBObject();
        sort.put("createdTimestamp", -1); // descending order (newest first)
        return instance.findAll(filter, skip, limit, sort);
    }

    public List<GuardrailPolicies> fetchAllGuardrailPoliciesName() {
        Bson filter = getContextSourceFilter();
        return instance.findAll(filter, Projections.include("name"));
    }

    public long getTotalCount() {
        Bson filter = getContextSourceFilter();
        return this.getMCollection().countDocuments(filter);
    }

    private Bson getContextSourceFilter() {
        CONTEXT_SOURCE contextSource = Context.contextSource.get();

        if (contextSource == null || contextSource == CONTEXT_SOURCE.AGENTIC) {
            return Filters.or(
                Filters.eq("contextSource", CONTEXT_SOURCE.AGENTIC),
                Filters.exists("contextSource", false)
            );
        } else {
            return Filters.eq("contextSource", contextSource);
        }
    }
}