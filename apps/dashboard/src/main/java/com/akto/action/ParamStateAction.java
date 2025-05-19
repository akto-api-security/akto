package com.akto.action;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.conversions.Bson;

public class ParamStateAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ParamStateAction.class, LogDb.DASHBOARD);
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
        try {
            List<Integer> collectionIds = UsersCollectionsList.getCollectionsIdForUser(Context.userId.get(), Context.accountId.get());
            if(collectionIds != null) {
                pipeline.add(Aggregates.match(Filters.in(SingleTypeInfo._COLLECTION_IDS, collectionIds)));
            }
        } catch(Exception e){
        }

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

        loggerMaker.debugAndAddToDb("Found " + privateSingleTypeInfo.size() + " private STIs", LoggerMaker.LogDb.DASHBOARD);

        if (privateSingleTypeInfo.isEmpty()) {
            Bson filter = Filters.or(
                    Filters.exists(SingleTypeInfo._UNIQUE_COUNT),
                    Filters.gt(SingleTypeInfo._UNIQUE_COUNT, 0)
            );
            SingleTypeInfo singleTypeInfo = SingleTypeInfoDao.instance.findOne(filter);
            loggerMaker.debugAndAddToDb("Did find STI with unique count url=" + singleTypeInfo.getUrl() + "count="+ singleTypeInfo.uniqueCount, LoggerMaker.LogDb.DASHBOARD);
        }

        return SUCCESS.toUpperCase();
    }

    public List<SingleTypeInfo> getPrivateSingleTypeInfo() {
        return privateSingleTypeInfo;
    }

}
