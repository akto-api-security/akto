package com.akto.dao;

import com.akto.dto.type.URLTemplate;

public class URLTemplateDao extends AccountsContextDao<URLTemplate> {

    @Override
    public String getCollName() {
        return "url_templates";
    }

    @Override
    public Class<URLTemplate> getClassT() {
        return URLTemplate.class;
    }
    
}
