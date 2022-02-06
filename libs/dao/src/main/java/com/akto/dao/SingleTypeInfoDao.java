package com.akto.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.akto.DaoInit;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

import org.bson.Document;
import org.bson.conversions.Bson;

public class SingleTypeInfoDao extends AccountsContextDao<SingleTypeInfo> {

    public static final SingleTypeInfoDao instance = new SingleTypeInfoDao();

    private SingleTypeInfoDao() {}

    @Override
    public String getCollName() {
        return "single_type_info";
    }

    @Override
    public Class<SingleTypeInfo> getClassT() {
        return SingleTypeInfo.class;
    }

    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }
        
        MongoCursor<Document> cursor = instance.getMCollection().listIndexes().cursor();
        int counter = 0;
        while (cursor.hasNext()) {
            counter++;
            cursor.next();
        }

        if (counter == 1) {
            String[] fieldNames = {"url", "method", "responseCode", "isHeader", "param", "subType", "apiCollectionId"};
            SingleTypeInfoDao.instance.getMCollection().createIndex(Indexes.ascending(fieldNames));    
        }
    }

    public List<SingleTypeInfo> fetchAll() {
        return this.findAll(new BasicDBObject());
    }

    public Set<String> getUniqueEndpoints(int apiCollectionId) {
        Bson filter = Filters.eq("apiCollectionId", apiCollectionId);
        return instance.findDistinctFields("url", String.class, filter);
    }

    public Set<String> getSensitiveEndpoints(int apiCollectionId) {
        List<String> v = new ArrayList<>();
        for (SingleTypeInfo.SubType subType: SingleTypeInfo.SubType.values()) {
            if (subType.isSensitive) {
                v.add(subType.name());
            }
        }

        Bson filter = Filters.and(
                Filters.eq("apiCollectionId",apiCollectionId),
                Filters.in("subType", v)
        );

        return instance.findDistinctFields("url", String.class, filter);
    }
    
}
