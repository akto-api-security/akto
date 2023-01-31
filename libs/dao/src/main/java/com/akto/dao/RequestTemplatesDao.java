package com.akto.dao;

import com.akto.dto.type.RequestTemplate;
import com.mongodb.BasicDBObject;

import java.util.List;

public class RequestTemplatesDao extends AccountsContextDao<RequestTemplate> {

    public static final RequestTemplatesDao instance = new RequestTemplatesDao();

    private RequestTemplatesDao() {}

    @Override
    public String getCollName() {
        return "request_templates";
    }

    @Override
    public Class<RequestTemplate> getClassT() {
        return RequestTemplate.class;
    }

    public List<RequestTemplate> fetchAll() {
        return this.findAll(new BasicDBObject());
    }
    
}
