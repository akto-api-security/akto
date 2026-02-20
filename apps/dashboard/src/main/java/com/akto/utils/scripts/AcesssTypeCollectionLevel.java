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
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import com.mongodb.BasicDBObject;

public class AcesssTypeCollectionLevel {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ApiCollectionsAction.class, LogDb.DASHBOARD);

    public static void doResetCollectionAccessTypesOptimized() {

        loggerMaker.infoAndAddToDb(
                "Starting optimized resetCollectionAccessTypes for account: " + Context.accountId.get(),
                LogDb.DASHBOARD
        );

        List<Bson> pipeline = new ArrayList<>();

        // Match: exclude documents with null apiCollectionId
        pipeline.add(Aggregates.match(Filters.and(
                Filters.exists("_id.apiCollectionId"),
                Filters.ne("_id.apiCollectionId", null)
        )));

        // Group by apiCollectionId (from _id compound key)
        Document apiAccessTypesOrEmpty = new Document("$ifNull", Arrays.asList("$" + ApiInfo.API_ACCESS_TYPES, Collections.emptyList()));

        pipeline.add(Aggregates.group(
                "$_id.apiCollectionId",

                // hasPublic: any endpoint has PUBLIC in apiAccessTypes
                Accumulators.max("hasPublic",
                        new Document("$cond", Arrays.asList(
                                new Document("$in", Arrays.asList("PUBLIC", apiAccessTypesOrEmpty)),
                                1,
                                0
                        ))
                ),

                // hasPrivateOrThirdParty: any endpoint has PRIVATE or THIRD_PARTY
                Accumulators.max("hasPrivateOrThirdParty",
                        new Document("$cond", Arrays.asList(
                                new Document("$gt", Arrays.asList(
                                        new Document("$size",
                                                new Document("$setIntersection", Arrays.asList(
                                                        apiAccessTypesOrEmpty,
                                                        Arrays.asList("PRIVATE", "THIRD_PARTY")
                                                ))
                                        ),
                                        0
                                )),
                                1,
                                0
                        ))
                )
        ));

        List<WriteModel<ApiCollection>> bulkUpdates = new ArrayList<>();

        try (MongoCursor<BasicDBObject> cursor =
                ApiInfoDao.instance.getMCollection()
                        .aggregate(pipeline, BasicDBObject.class)
                        .allowDiskUse(true)
                        .cursor()) {

            while (cursor.hasNext()) {

                BasicDBObject result = cursor.next();

                Object idObj = result.get("_id");
                Integer collectionId = idObj instanceof Number ? ((Number) idObj).intValue() : null;
                if (collectionId == null) continue;

                Integer hasPublic = getInt(result, "hasPublic");
                Integer hasPrivateOrThirdParty = getInt(result, "hasPrivateOrThirdParty");

                String finalAccessType = null;

                if (hasPublic != null && hasPublic == 1) {
                    finalAccessType = ApiCollection.AccessType.INTERNAL.getDisplayName();
                } else if (hasPrivateOrThirdParty != null && hasPrivateOrThirdParty == 1) {
                    finalAccessType = ApiCollection.AccessType.THIRD_PARTY.getDisplayName();
                }

                if (finalAccessType != null) {
                    bulkUpdates.add(
                            new UpdateOneModel<ApiCollection>(
                                    Filters.eq("_id", collectionId),
                                    Updates.set(ApiCollection.ACCESS_TYPE, finalAccessType)
                            )
                    );
                }
            }
        }

        if (!bulkUpdates.isEmpty()) {
            ApiCollectionsDao.instance.getMCollection().bulkWrite(bulkUpdates);
        }

        loggerMaker.infoAndAddToDb(
                "Completed optimized resetCollectionAccessTypes. Updated: " + bulkUpdates.size(),
                LogDb.DASHBOARD
        );
    }

    private static Integer getInt(BasicDBObject doc, String key) {
        Object val = doc.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return null;
    }

}
