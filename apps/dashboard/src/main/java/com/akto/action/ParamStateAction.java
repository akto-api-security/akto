package com.akto.action;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.EstimatedDocumentCountOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.ArrayList;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

public class ParamStateAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ParamStateAction.class);
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
                        SingleTypeInfo._UNIQUE_COUNT, SingleTypeInfo._PUBLIC_COUNT
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

        loggerMaker.infoAndAddToDb("Found " + privateSingleTypeInfo.size() + " private STIs", LoggerMaker.LogDb.DASHBOARD);

        if (privateSingleTypeInfo.isEmpty()) {
            Bson filter = Filters.or(
                    Filters.exists(SingleTypeInfo._UNIQUE_COUNT),
                    Filters.gt(SingleTypeInfo._UNIQUE_COUNT, 0)
            );
            SingleTypeInfo singleTypeInfo = SingleTypeInfoDao.instance.findOne(filter);
            loggerMaker.infoAndAddToDb("Did find STI with unique count url=" + singleTypeInfo.getUrl() + "count="+ singleTypeInfo.uniqueCount, LoggerMaker.LogDb.DASHBOARD);
        }

        return SUCCESS.toUpperCase();
    }

    public List<SingleTypeInfo> getPrivateSingleTypeInfo() {
        return privateSingleTypeInfo;
    }

}
