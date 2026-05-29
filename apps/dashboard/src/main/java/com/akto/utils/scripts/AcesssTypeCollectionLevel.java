package com.akto.utils.scripts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;

import com.akto.action.ApiCollectionsAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Field;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

public class AcesssTypeCollectionLevel {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionsAction.class, LogDb.DASHBOARD);

    public static void doResetCollectionAccessTypes() {
        loggerMaker.infoAndAddToDb(
                "Starting resetCollectionAccessTypes for account: " + Context.accountId.get(),
                LogDb.DASHBOARD
        );

        Document apiAccessTypesOrEmpty = new Document(
                "$ifNull",
                Arrays.asList("$" + ApiInfo.API_ACCESS_TYPES, Collections.emptyList())
        );

        List<Bson> pipeline = new ArrayList<>();

        pipeline.add(Aggregates.match(Filters.and(
                Filters.exists("_id.apiCollectionId"),
                Filters.ne("_id.apiCollectionId", null)
        )));

        pipeline.add(Aggregates.group(
                "$_id.apiCollectionId",
                Accumulators.max("hasPublic",
                        new Document("$cond", Arrays.asList(
                                new Document("$in", Arrays.asList("PUBLIC", apiAccessTypesOrEmpty)),
                                1,
                                0
                        ))),
                Accumulators.max("hasPrivateOrThirdParty",
                        new Document("$cond", Arrays.asList(
                                new Document("$gt", Arrays.asList(
                                        new Document("$size",
                                                new Document("$setIntersection", Arrays.asList(
                                                        apiAccessTypesOrEmpty,
                                                        Arrays.asList("PRIVATE", "THIRD_PARTY")
                                                ))),
                                        0
                                )),
                                1,
                                0
                        )))
        ));

        pipeline.add(Aggregates.addFields(new Field<>("accessType",
                new Document("$cond", Arrays.asList(
                        new Document("$eq", Arrays.asList("$hasPublic", 1)),
                        ApiCollection.AccessType.INTERNAL.getDisplayName(),
                        new Document("$cond", Arrays.asList(
                                new Document("$eq", Arrays.asList("$hasPrivateOrThirdParty", 1)),
                                ApiCollection.AccessType.THIRD_PARTY.getDisplayName(),
                                "$$REMOVE"
                        ))
                ))
        )));

        pipeline.add(Aggregates.match(Filters.exists("accessType")));

        List<WriteModel<ApiCollection>> bulkUpdates = new ArrayList<>();

        try (MongoCursor<BasicDBObject> cursor = ApiInfoDao.instance.getMCollection()
                .aggregate(pipeline, BasicDBObject.class)
                .allowDiskUse(true)
                .cursor()) {

            while (cursor.hasNext()) {
                BasicDBObject result = cursor.next();

                Object idObj = result.get("_id");
                Integer collectionId = idObj instanceof Number ? ((Number) idObj).intValue() : null;
                if (collectionId == null) continue;

                String accessType = result.getString("accessType");
                if (accessType == null) continue;

                bulkUpdates.add(new UpdateOneModel<>(
                        Filters.eq("_id", collectionId),
                        Updates.set(ApiCollection.ACCESS_TYPE, accessType)
                ));
            }
        }

        if (!bulkUpdates.isEmpty()) {
            ApiCollectionsDao.instance.getMCollection().bulkWrite(bulkUpdates, new BulkWriteOptions().ordered(false));
        }

        loggerMaker.infoAndAddToDb(
                "Completed resetCollectionAccessTypes. Updated: " + bulkUpdates.size(),
                LogDb.DASHBOARD
        );
    }

}
