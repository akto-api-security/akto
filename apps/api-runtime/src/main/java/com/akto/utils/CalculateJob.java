package com.akto.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.function.FailableFunction;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonValue;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.conversions.Bson;
import com.akto.dao.ApiInfoDao;
import com.akto.dto.ApiInfo;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.WriteModel;

public class CalculateJob {

    // Constants.ID is "_id." (trailing dot), which is not the document _id path
    private static final String ID_FIELD = "_id";
    private static final LoggerMaker loggerMaker = new LoggerMaker(CalculateJob.class, LogDb.DASHBOARD);

    public static void apiInfoUpdateJob(FailableFunction<ApiInfo, UpdateOneModel<ApiInfo>, Exception> function) {
        int limit = 1000;
        int totalRead = 0;
        MongoCollection<ApiInfo> apiInfoCollection = ApiInfoDao.instance.getMCollection();
        MongoCollection<BsonDocument> rawCollection = apiInfoCollection.withDocumentClass(BsonDocument.class);
        Codec<ApiInfo> apiInfoCodec = apiInfoCollection.getCodecRegistry().get(ApiInfo.class);
        // Range-paginate on the raw stored _id. It must not be rebuilt from ApiInfoKey: the stored key order
        // (url, apiCollectionId, method) differs from the codec's, and embedded documents compare field by field.
        BsonValue lastId = null;
        boolean fetchMore;
        do {
            Bson filter = lastId == null ? new BsonDocument() : Filters.lt(ID_FIELD, lastId);
            List<BsonDocument> docs = rawCollection.find(filter)
                    .sort(Sorts.descending(ID_FIELD))
                    .limit(limit)
                    .into(new ArrayList<>());
            totalRead += docs.size();
            loggerMaker.infoAndAddToDb("Read " + totalRead + " api infos for calc job", LogDb.DASHBOARD);
            List<WriteModel<ApiInfo>> apiInfosUpdates = new ArrayList<>();
            for (BsonDocument doc : docs) {
                ApiInfo apiInfo = apiInfoCodec.decode(new BsonDocumentReader(doc), DecoderContext.builder().build());
                try {
                    UpdateOneModel<ApiInfo> update = function.apply(apiInfo);
                    if (update != null) {
                        apiInfosUpdates.add(update);
                    }
                } catch (Exception e) {
                    loggerMaker.errorAndAddToDb(e, "Error in calculating api info update for update job for " + apiInfo.getId().toString());
                }
            }

            if (!apiInfosUpdates.isEmpty()) {
                loggerMaker.infoAndAddToDb("Updating " + apiInfosUpdates.size() + " api infos for calc job", LogDb.DASHBOARD);
                apiInfoCollection.bulkWrite(apiInfosUpdates);
            }

            fetchMore = docs.size() == limit;
            if (fetchMore) {
                lastId = docs.get(docs.size() - 1).get(ID_FIELD);
            }

            loggerMaker.infoAndAddToDb("Finished " + totalRead + " api infos for calc job", LogDb.DASHBOARD);

        } while (fetchMore);
    }

}
