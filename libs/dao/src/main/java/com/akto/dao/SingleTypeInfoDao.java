package com.akto.dao;

import java.util.ArrayList;
import java.util.List;

import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.client.DistinctIterable;
import com.mongodb.client.MongoCursor;

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
    public List<String> getUniqueValues() {
        DistinctIterable<String> r = getMCollection().distinct("url",String.class);
        List<String> result = new ArrayList<>();
        MongoCursor<String> cursor = r.cursor();
        while (cursor.hasNext()) {
            result.add(cursor.next());
        }
        return result;
    }
    
}
