package com.akto.dao.billing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.type.URLMethods;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;

public class UningestedApiOverageDao extends AccountsContextDao<UningestedApiOverage> {

    public static final UningestedApiOverageDao instance = new UningestedApiOverageDao();
    
    // Maximum number of documents to keep in the collection
    public static final int MAX_DOCUMENTS = 5000;

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

    /**
     * Check if the collection has reached the maximum document limit
     * @return true if the collection has reached or exceeded MAX_DOCUMENTS
     */
    public boolean hasReachedLimit() {
        long currentCount = getMCollection().countDocuments();
        return currentCount >= MAX_DOCUMENTS;
    }
}