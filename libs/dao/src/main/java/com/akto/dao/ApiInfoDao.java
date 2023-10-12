package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ApiInfoDao extends AccountsContextDao<ApiInfo>{

    public static ApiInfoDao instance = new ApiInfoDao();

    public static final List<Bson> legacyIndices = Arrays.asList(
                Indexes.ascending(new String[] {Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.API_COLLECTION_ID}),
                Indexes.ascending(new String[] {Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.API_COLLECTION_ID, Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.URL}));

    public void createIndicesIfAbsent(boolean createLegacyIndices) {

        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        List<Bson> indices = new ArrayList<>(Arrays.asList(
                Indexes.ascending(new String[]{SingleTypeInfo._COLLECTION_IDS}),
                Indexes.ascending(new String[]{Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.URL}),
                Indexes.ascending(new String[]{SingleTypeInfo._COLLECTION_IDS, Constants.ID + Constants.DOT + ApiInfo.ApiInfoKey.URL})
        ));

        if (createLegacyIndices) {
            indices.addAll(legacyIndices);
        }
        createIndices(indices);
    }

    @Override
    public String getCollName() {
        return "api_info";
    }

    @Override
    public Class<ApiInfo> getClassT() {
        return ApiInfo.class;
    }

    public static Bson getFilter(ApiInfo.ApiInfoKey apiInfoKey) {
        return getFilter(apiInfoKey.getUrl(), apiInfoKey.getMethod().name(), apiInfoKey.getApiCollectionId());
    }

    public static Bson getFilter(String url, String method, int apiCollectionId) {
        return Filters.and(
                Filters.eq("_id.url", url),
                Filters.eq("_id.method", method),
                Filters.in(SingleTypeInfo._COLLECTION_IDS, Arrays.asList(apiCollectionId))
        );
    }

}
