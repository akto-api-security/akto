package com.akto.dao;

import com.akto.dto.UrlCollectionIdMapping;

public class UrlCollectionIdMappingsDao extends AccountsContextDao<UrlCollectionIdMapping> {

    public static final UrlCollectionIdMappingsDao instance = new UrlCollectionIdMappingsDao();

    private UrlCollectionIdMappingsDao() {}

    @Override
    public String getCollName() {
        return "url_collection_id_mappings";
    }

    @Override
    public Class<UrlCollectionIdMapping> getClassT() {
        return UrlCollectionIdMapping.class;
    }
    
}
