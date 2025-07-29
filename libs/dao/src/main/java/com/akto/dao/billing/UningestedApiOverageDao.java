package com.akto.dao.billing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.billing.UningesetedApiOverage;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Accumulators;
import com.mongodb.client.MongoCursor;
import com.mongodb.BasicDBObject;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class UningestedApiOverageDao extends AccountsContextDao<UningesetedApiOverage> {

    public static final UningestedApiOverageDao instance = new UningestedApiOverageDao();

    private UningestedApiOverageDao() {}

    @Override
    public String getCollName() {
        return "uningested_api_info";
    }

    @Override
    public Class<UningesetedApiOverage> getClassT() {
        return UningesetedApiOverage.class;
    }

    public void createIndicesIfAbsent() {
        // Create index on accountId for efficient queries
        getMCollection().createIndex(Indexes.ascending(UningesetedApiOverage.API_COLLECTION_ID));
        
        // Create compound index on accountId, apiCollectionId, url, method for deduplication
        getMCollection().createIndex(Indexes.ascending(
            UningesetedApiOverage.API_COLLECTION_ID,
            UningesetedApiOverage.URL_TYPE,
            UningesetedApiOverage.METHOD_AND_URL
        ));
    }

    public static Bson generateFilter() {
        return Filters.eq("_id", Context.accountId.get());
    }

    public static Bson generateFilter(int accountId) {
        Context.accountId.set(accountId);
        return Filters.empty();
    }

    public static Bson generateFilter(int accountId, int apiCollectionId) {
        Context.accountId.set(accountId);
        return Filters.and(
            Filters.eq(UningesetedApiOverage.API_COLLECTION_ID, apiCollectionId)
        );
    }

    public List<UningesetedApiOverage> findByAccountId(int accountId) {
        return findAll(generateFilter(accountId));
    }

    public List<UningesetedApiOverage> findByAccountIdAndCollection(int accountId, int apiCollectionId) {
        return findAll(generateFilter(accountId, apiCollectionId));
    }

    public boolean exists(int apiCollectionId, String urlType, String methodAndUrl) {
        Bson filter = Filters.and(
            Filters.eq(UningesetedApiOverage.API_COLLECTION_ID, apiCollectionId),
            Filters.eq(UningesetedApiOverage.METHOD_AND_URL, methodAndUrl),
            Filters.eq(UningesetedApiOverage.URL_TYPE, urlType)
        );
        return findOne(filter) != null;
    }

    public Map<Integer, Integer> getCountByCollection() {
        Map<Integer, Integer> countMap = new HashMap<>();
        
        List<Bson> pipeline = new ArrayList<>();
        pipeline.add(Aggregates.group(
            "$" + UningesetedApiOverage.API_COLLECTION_ID,
            Accumulators.sum("count", 1)
        ));
        
        MongoCursor<BasicDBObject> cursor = getMCollection().aggregate(pipeline, BasicDBObject.class).cursor();
        while (cursor.hasNext()) {
            try {
                BasicDBObject result = cursor.next();
                int apiCollectionId = result.getInt("_id");
                int count = result.getInt("count");
                countMap.put(apiCollectionId, count);
            } catch (Exception e) {
                // Log error silently since LoggerMaker is not available in this module
            }
        }
        
        return countMap;
    }
} 