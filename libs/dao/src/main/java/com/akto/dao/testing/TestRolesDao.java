package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.testing.TestRoles;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;

public class TestRolesDao extends AccountsContextDao<TestRoles> {
    @Override
    public String getCollName() {
        return MongoDBEnums.Collection.TEST_ROLES.getCollectionName();
    }
    public static final TestRolesDao instance = new TestRolesDao();
    private TestRolesDao(){}
    @Override
    public Class<TestRoles> getClassT() {
        return TestRoles.class;
    }
    public void createIndicesIfAbsent() {

        boolean exists = false;
        for (String col: clients[0].getDatabase(Context.accountId.get()+"").listCollectionNames()){
            if (getCollName().equalsIgnoreCase(col)){
                exists = true;
                break;
            }
        };

        if (!exists) {
            clients[0].getDatabase(Context.accountId.get()+"").createCollection(getCollName());
        }

        String[] fieldNames = {TestRoles.NAME};
        instance.getMCollection().createIndex(Indexes.ascending(fieldNames), new IndexOptions().unique(true));

    }

    public TestRoles createTestRole (String roleName, ObjectId endpointLogicalGroupId, String userName) {
        int createdTs = Context.now();
        TestRoles role = new TestRoles(new ObjectId(), roleName, endpointLogicalGroupId, new ArrayList<>(), null,userName,createdTs, createdTs);

        this.insertOne(role);
        this.getLogger().info("Created test role with name :{}, and logical group id : {}", roleName, endpointLogicalGroupId.toHexString());
        return role;
    }
}
