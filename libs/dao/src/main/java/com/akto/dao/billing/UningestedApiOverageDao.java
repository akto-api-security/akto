package com.akto.dao.billing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.billing.UningestedApiOverage;
import com.akto.dto.type.URLMethods;
import com.mongodb.client.model.Indexes;
import org.bson.conversions.Bson;
import com.mongodb.client.model.Filters;

import java.util.List;

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
            Filters.eq(UningestedApiOverage.API_COLLECTION_ID, apiCollectionId)
        );
    }

    public List<UningestedApiOverage> findByAccountId(int accountId) {
        return findAll(generateFilter(accountId));
    }

    public List<UningestedApiOverage> findByAccountIdAndCollection(int accountId, int apiCollectionId) {
        return findAll(generateFilter(accountId, apiCollectionId));
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
} 