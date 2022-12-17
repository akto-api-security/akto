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

        MongoCursor<Document> cursor = instance.getMCollection().listIndexes().cursor();
        int counter = 0;
        while (cursor.hasNext()) {
            counter++;
            cursor.next();
        }

        if (counter == 1) {//Only _id as index available
            String[] fieldNames = {TestRoles.NAME};
            instance.getMCollection().createIndex(Indexes.ascending(fieldNames), new IndexOptions().unique(true));
        }
    }

    public TestRoles createTestRole (String roleName, ObjectId endpointLogicalGroupId, String userName) {
        TestRoles role = new TestRoles(roleName, endpointLogicalGroupId, null,userName,Context.now());

        this.insertOne(role);
        this.getLogger().info("Created test role with name :{}, and logical group id : {}", roleName, endpointLogicalGroupId.toHexString());
        return role;
    }
}
