package com.akto.dao;

import java.util.List;

import com.akto.dto.type.EndpointInfo;
import com.mongodb.BasicDBObject;

public class EndpointInfoDao extends AccountsContextDao<EndpointInfo> {

    public static final EndpointInfoDao instance = new EndpointInfoDao();

    private EndpointInfoDao() {}

    @Override
    public String getCollName() {
        return "endpoints_info";
    }

    @Override
    public Class<EndpointInfo> getClassT() {
        return EndpointInfo.class;
    }

    public List<EndpointInfo> fetchAllEndpoints() {
        return this.findAll(new BasicDBObject());
    }
    
}
