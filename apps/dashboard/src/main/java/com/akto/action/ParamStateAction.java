package com.akto.action;

import com.akto.DaoInit;
import com.akto.dao.ParamTypeInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.ParamTypeInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.BasicDBObject;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

public class ParamStateAction extends UserAction {

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }

    private List<ParamTypeInfo> paramTypeInfoList = new ArrayList<>();

    public String fetchParamsStatus() {

        List<Bson> pipeline = new ArrayList<>();

        String computedJson = "{'$divide': ['$" + ParamTypeInfo.PUBLIC_COUNT + "', '$"+ParamTypeInfo.UNIQUE_COUNT+"']}";
        String computedFieldName = "computedValue";

        Bson projections = Projections.fields(
                Projections.include(
                    ParamTypeInfo.API_COLLECTION_ID, ParamTypeInfo.URL, ParamTypeInfo.METHOD,
                    ParamTypeInfo.IS_HEADER, ParamTypeInfo.IS_URL_PARAM, ParamTypeInfo.PARAM,
                    ParamTypeInfo.UNIQUE_COUNT, ParamTypeInfo.PUBLIC_COUNT
                ),
                Projections.computed(
                    computedFieldName,
                    Document.parse(computedJson)
                )
        );

        pipeline.add(Aggregates.project(projections));

        pipeline.add(Aggregates.match(Filters.lte(computedFieldName,0.01)));

        pipeline.add(Aggregates.limit(1000));

        MongoCursor<ParamTypeInfo> cursor = ParamTypeInfoDao.instance.getMCollection().aggregate(pipeline, ParamTypeInfo.class).cursor();

        while(cursor.hasNext()) {
            ParamTypeInfo paramTypeInfo = cursor.next();
            paramTypeInfoList.add(paramTypeInfo);
        }

        return SUCCESS.toUpperCase();
    }

    public List<ParamTypeInfo> getParamTypeInfoList() {
        return paramTypeInfoList;
    }

}
