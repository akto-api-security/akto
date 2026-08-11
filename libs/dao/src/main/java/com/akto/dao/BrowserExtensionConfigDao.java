package com.akto.dao;

import com.akto.dto.BrowserExtensionConfig;
import com.mongodb.BasicDBObject;

import java.util.List;

public class BrowserExtensionConfigDao extends AccountsContextDao<BrowserExtensionConfig> {

    public static final String COLLECTION_NAME = "browser_extension_configs";
    public static final BrowserExtensionConfigDao instance = new BrowserExtensionConfigDao();

    @Override
    public String getCollName() {
        return COLLECTION_NAME;
    }

    @Override
    public Class<BrowserExtensionConfig> getClassT() {
        return BrowserExtensionConfig.class;
    }

    public List<BrowserExtensionConfig> findAllConfigs() {
        return instance.findAll(new BasicDBObject());
    }
}
