package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.ParamTypeInfo;
import com.akto.dto.type.URLMethods;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;

public class ParamTypeInfoDao extends AccountsContextDao<ParamTypeInfo>{

    public static final ParamTypeInfoDao instance  = new ParamTypeInfoDao();

    @Override
    public String getCollName() {
        return "param_type_info";
    }

    @Override
    public Class<ParamTypeInfo> getClassT() {
        return ParamTypeInfo.class;
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
            String[] fieldNames = {
                    ParamTypeInfo.API_COLLECTION_ID,
                    ParamTypeInfo.URL,
                    ParamTypeInfo.METHOD,
                    ParamTypeInfo.RESPONSE_CODE,
                    ParamTypeInfo.IS_HEADER,
                    ParamTypeInfo.IS_URL_PARAM,
                    ParamTypeInfo.PARAM
            };
            instance.getMCollection().createIndex(Indexes.ascending(fieldNames));
        }
    }


    public List<ApiInfo.ApiInfoKey> fetchEndpointsInCollection() {
        List<Bson> pipeline = new ArrayList<>();
        BasicDBObject groupedId =
                new BasicDBObject("apiCollectionId", "$apiCollectionId")
                        .append("url", "$url")
                        .append("method", "$method");

        Bson projections = Projections.fields(
                Projections.include("apiCollectionId", "url", "method")
        );

        pipeline.add(Aggregates.project(projections));
        pipeline.add(Aggregates.group(groupedId));

        MongoCursor<BasicDBObject> endpointsCursor = instance.getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();

        List<ApiInfo.ApiInfoKey> endpoints = new ArrayList<>();
        while(endpointsCursor.hasNext()) {
            BasicDBObject v = endpointsCursor.next();
            try {
                BasicDBObject vv = (BasicDBObject) v.get("_id");
                ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(
                        (int) vv.get("apiCollectionId"),
                        (String) vv.get("url"),
                        URLMethods.Method.valueOf((String) vv.get("method"))
                );
                endpoints.add(apiInfoKey);
            } catch (Exception e) {
                e.printStackTrace();

            }
        }

        return endpoints;
    }

    public static Bson createFilters(ParamTypeInfo info) {
        return Filters.and(
                Filters.eq(ParamTypeInfo.API_COLLECTION_ID, info.getApiCollectionId()),
                Filters.eq(ParamTypeInfo.URL, info.getUrl()),
                Filters.eq(ParamTypeInfo.METHOD, info.getMethod()),
                Filters.eq(ParamTypeInfo.RESPONSE_CODE, info.getResponseCode()),
                Filters.eq(ParamTypeInfo.IS_HEADER, info.isHeader),
                Filters.eq(ParamTypeInfo.IS_URL_PARAM, info.isUrlParam()),
                Filters.eq(ParamTypeInfo.PARAM, info.getParam())
        );
    }
}
