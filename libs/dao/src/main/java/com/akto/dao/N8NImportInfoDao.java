package com.akto.dao;

import com.akto.dto.N8NImportInfo;
import com.akto.dao.context.Context;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.List;

public class N8NImportInfoDao extends AccountsContextDao<N8NImportInfo> {
    public static final String COLLECTION_NAME = "ai_agent_discovery_connectors";
    public static final N8NImportInfoDao instance = new N8NImportInfoDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<N8NImportInfo> getClassT() {
        return N8NImportInfo.class;
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

        String[] fieldNames = {"type"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"createdTimestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"updatedTimestamp"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"status"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);

        fieldNames = new String[]{"n8nUrl"};
        MCollection.createIndexIfAbsent(getDBName(), getCollName(), fieldNames, false);
    }

    public List<N8NImportInfo> findAllSortedByCreatedTimestamp(int pageNumber, int pageSize) {
        BasicDBObject sort = new BasicDBObject();
        sort.put("createdTimestamp", -1); // descending order
        int skip = (pageNumber - 1) * pageSize;
        return this.getMCollection().find(new BasicDBObject())
            .sort(sort)
            .skip(skip)
            .limit(pageSize)
            .into(new ArrayList<>());
    }

    public List<N8NImportInfo> findByStatus(String status, int pageNumber, int pageSize) {
        BasicDBObject query = new BasicDBObject("status", status);
        BasicDBObject sort = new BasicDBObject();
        sort.put("createdTimestamp", -1);
        int skip = (pageNumber - 1) * pageSize;
        return this.getMCollection().find(query)
            .sort(sort)
            .skip(skip)
            .limit(pageSize)
            .into(new ArrayList<>());
    }

    public List<N8NImportInfo> findByType(String type, int pageNumber, int pageSize) {
        BasicDBObject query = new BasicDBObject("type", type);
        BasicDBObject sort = new BasicDBObject();
        sort.put("createdTimestamp", -1);
        int skip = (pageNumber - 1) * pageSize;
        return this.getMCollection().find(query)
            .sort(sort)
            .skip(skip)
            .limit(pageSize)
            .into(new ArrayList<>());
    }

    public List<N8NImportInfo> findByTypeAndStatus(String type, String status, int pageNumber, int pageSize) {
        BasicDBObject query = new BasicDBObject();
        query.put("type", type);
        query.put("status", status);
        BasicDBObject sort = new BasicDBObject();
        sort.put("createdTimestamp", -1);
        int skip = (pageNumber - 1) * pageSize;
        return this.getMCollection().find(query)
            .sort(sort)
            .skip(skip)
            .limit(pageSize)
            .into(new ArrayList<>());
    }
}
