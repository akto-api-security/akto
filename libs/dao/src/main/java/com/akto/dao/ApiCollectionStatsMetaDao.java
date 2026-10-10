package com.akto.dao;

import com.akto.dto.ApiCollectionStatsMeta;

public class ApiCollectionStatsMetaDao extends AccountsContextDao<ApiCollectionStatsMeta> {

    public static final ApiCollectionStatsMetaDao instance = new ApiCollectionStatsMetaDao();

    @Override
    public String getCollName() {
        return "api_collection_stats_meta";
    }

    @Override
    public Class<ApiCollectionStatsMeta> getClassT() {
        return ApiCollectionStatsMeta.class;
    }
}
