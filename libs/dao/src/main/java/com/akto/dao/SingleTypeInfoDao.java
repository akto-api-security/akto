package com.akto.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
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
