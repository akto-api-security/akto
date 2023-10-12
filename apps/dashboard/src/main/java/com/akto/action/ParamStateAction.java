package com.akto.action;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.type.SingleTypeInfo;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

public class ParamStateAction extends UserAction {

    @Override
    public String execute() {
        return SUCCESS.toUpperCase();
    }

    private List<SingleTypeInfo> privateSingleTypeInfo = new ArrayList<>();

    public String fetchParamsStatus() {

        List<Bson> pipeline = new ArrayList<>();

        String computedJson = "{'$divide': ['$" + SingleTypeInfo._PUBLIC_COUNT + "', '$"+SingleTypeInfo._UNIQUE_COUNT+"']}";
        String computedFieldName = "computedValue";

        pipeline.add(Aggregates.match(Filters.gt(SingleTypeInfo._UNIQUE_COUNT,0)));

        Bson projections = Projections.fields(
                Projections.include(
                        SingleTypeInfo._API_COLLECTION_ID, SingleTypeInfo._URL, SingleTypeInfo._METHOD,
                        SingleTypeInfo._IS_HEADER, SingleTypeInfo._IS_URL_PARAM, SingleTypeInfo._PARAM,
                        SingleTypeInfo._UNIQUE_COUNT, SingleTypeInfo._PUBLIC_COUNT, SingleTypeInfo._COLLECTION_IDS
                ),
                Projections.computed(
                    computedFieldName,
                    Document.parse(computedJson)
                )
        );

        pipeline.add(Aggregates.project(projections));

        pipeline.add(Aggregates.match(Filters.lte(computedFieldName,SingleTypeInfo.THRESHOLD)));

        pipeline.add(Aggregates.limit(3000));

        MongoCursor<SingleTypeInfo> cursor = SingleTypeInfoDao.instance.getMCollection().aggregate(pipeline, SingleTypeInfo.class).cursor();

        while(cursor.hasNext()) {
            SingleTypeInfo singleTypeInfo = cursor.next();
            privateSingleTypeInfo.add(singleTypeInfo);
        }

        return SUCCESS.toUpperCase();
    }

    public List<SingleTypeInfo> getPrivateSingleTypeInfo() {
        return privateSingleTypeInfo;
    }

}
