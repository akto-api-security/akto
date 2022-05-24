package com.akto.dao;

import com.akto.dto.TagConfig;

public class TagConfigsDao extends AccountsContextDao<TagConfig> {
    public static final TagConfigsDao instance = new TagConfigsDao();

    @Override
    public String getCollName() {
        return "tag_config";
    }

    @Override
    public Class<TagConfig> getClassT() {
        return TagConfig.class;
    }    
}
