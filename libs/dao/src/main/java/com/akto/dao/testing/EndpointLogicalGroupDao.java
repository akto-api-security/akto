package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.util.enums.MongoDBEnums;

public class EndpointLogicalGroupDao extends AccountsContextDao<EndpointLogicalGroup> {
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.ENDPOINT_LOGICAL_GROUP.getCollectionName();
    }
    @Override
    public Class<EndpointLogicalGroup> getClassT() {
        return EndpointLogicalGroup.class;
    }
    public static final EndpointLogicalGroupDao instance = new EndpointLogicalGroupDao();
    private EndpointLogicalGroupDao(){}
}
