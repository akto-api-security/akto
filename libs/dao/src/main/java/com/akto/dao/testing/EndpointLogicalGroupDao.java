package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.LogicalGroupTestingEndpoint;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.MongoException;
import org.bson.types.ObjectId;

import java.util.List;

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

    public EndpointLogicalGroup createUsingRegex(String name, String regex, String user,
                                                 List<ApiInfo.ApiInfoKey> includedApiInfoKey, List<ApiInfo.ApiInfoKey> excludedApiInfoKey) {
        LogicalGroupTestingEndpoint testingEndpoint = new LogicalGroupTestingEndpoint(regex, includedApiInfoKey, excludedApiInfoKey);
        EndpointLogicalGroup endpointLogicalGroup = new EndpointLogicalGroup(new ObjectId(), Context.now(), user,name, testingEndpoint);
        try {
            this.insertOne(endpointLogicalGroup);
            return endpointLogicalGroup;
        } catch (MongoException e) {
            getLogger().info("Error while inserting endpoint logical group for name :{}, regex: {}", name, regex);
            return null;
        }
    }

    private EndpointLogicalGroupDao() {
    }
}
