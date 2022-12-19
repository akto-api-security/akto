package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.testing.EndpointLogicalGroup;
import com.akto.dto.testing.LogicalGroupTestingEndpoint;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.MongoException;
import org.bson.types.ObjectId;

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

    public EndpointLogicalGroup createLogicalGroup(String name, Conditions andConditions, Conditions orConditions, String user) {
        LogicalGroupTestingEndpoint testingEndpoint = new LogicalGroupTestingEndpoint(andConditions, orConditions);
        int createdTs = Context.now();
        EndpointLogicalGroup endpointLogicalGroup = new EndpointLogicalGroup(new ObjectId(), createdTs, createdTs, user,name, testingEndpoint);
        try {
            this.insertOne(endpointLogicalGroup);
            return endpointLogicalGroup;
        } catch (MongoException e) {
            getLogger().info("Error while inserting endpoint logical group for name :{}", name);
            return null;
        }
    }

    private EndpointLogicalGroupDao() {
    }
}
