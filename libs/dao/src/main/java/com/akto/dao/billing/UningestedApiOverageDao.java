package com.akto.dao.billing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.type.URLMethods;
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

public class UningestedApiOverageDao extends AccountsContextDao<UningestedApiOverage> {

    public static final UningestedApiOverageDao instance = new UningestedApiOverageDao();

    private UningestedApiOverageDao() {}

    @Override
    public String getCollName() {
        return "uningested_api_info";
    }

    @Override
    public Class<UningestedApiOverage> getClassT() {
        return UningestedApiOverage.class;
    }

    public void createIndicesIfAbsent() {
        // Create index on accountId for efficient queries
        getMCollection().createIndex(Indexes.ascending(UningestedApiOverage.API_COLLECTION_ID));
        
        // Create compound index on accountId, apiCollectionId, url, method for deduplication
        getMCollection().createIndex(Indexes.ascending(
            UningestedApiOverage.API_COLLECTION_ID,
            UningestedApiOverage.URL_TYPE,
            UningestedApiOverage.METHOD,
            UningestedApiOverage.URL
        ));
    }
    public boolean exists(int apiCollectionId, String urlType, URLMethods.Method method, String url) {
        Bson filter = Filters.and(
            Filters.eq(UningestedApiOverage.API_COLLECTION_ID, apiCollectionId),
            Filters.eq(UningestedApiOverage.METHOD, method),
            Filters.eq(UningestedApiOverage.URL, url),
            Filters.eq(UningestedApiOverage.URL_TYPE, urlType)
        );
        return findOne(filter) != null;
    }

    public Map<Integer, Integer> getCountByCollection() {
        Map<Integer, Integer> countMap = new HashMap<>();
        
        List<Bson> pipeline = new ArrayList<>();
        
        // Add a match stage to exclude OPTIONS methods
        pipeline.add(Aggregates.match(
            Filters.ne(UningestedApiOverage.METHOD, URLMethods.Method.OPTIONS)
        ));
        
        pipeline.add(Aggregates.group(
            "$" + UningestedApiOverage.API_COLLECTION_ID,
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