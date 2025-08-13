package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiSequences;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;


import java.util.List;
import java.util.ArrayList;

public class ApiSequencesDao extends AccountsContextDao<ApiSequences> {

    public static final ApiSequencesDao instance = new ApiSequencesDao();

    private ApiSequencesDao() {}

    @Override
    public String getCollName() {
        return "api_sequences";
    }

    @Override
    public Class<ApiSequences> getClassT() {
        return ApiSequences.class;
    }

    public String getFilterKeyString() {
        return ApiSequences.ID;
    }

    public void createIndicesIfAbsent() {
        boolean exists = false;
        String dbName = Context.accountId.get()+"";
        MongoDatabase db = clients[0].getDatabase(dbName);
        for (String col: db.listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            db.createCollection(getCollName());
        }

        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiSequences.API_COLLECTION_ID }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiSequences.CREATED_AT }, false);
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), new String[] { ApiSequences.LAST_UPDATED_AT }, false);
    }



    public List<ApiSequences> findByApiCollectionId(int apiCollectionId) {

        try {
            List<ApiSequences> result = ApiSequencesDao.instance.findAll(Filters.eq(ApiSequences.API_COLLECTION_ID, apiCollectionId));
            return result;
        } catch (Exception e) {
            System.err.println("Error in findByApiCollectionId: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }


}
