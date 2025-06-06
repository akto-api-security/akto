package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.context.Context;
import com.akto.dto.RawApi;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        TestRoles role = new TestRoles(new ObjectId(), roleName, endpointLogicalGroupId, new ArrayList<>(),userName,createdTs, createdTs, null, userName);

        this.insertOne(role);
        this.getLogger().info("Created test role with name :{}, and logical group id : {}", roleName, endpointLogicalGroupId.toHexString());
        return role;
    }

    private TestRoles findAttackerRole() {
        return TestRolesDao.instance.findOne(TestRoles.NAME, "ATTACKER_TOKEN_ALL");
    }

    public AuthMechanism fetchAttackerToken(RawApi rawApi) {
        TestRoles testRoles = findAttackerRole();
        if (testRoles == null) {
            return null;
        }
        return testRoles.findMatchingAuthMechanism(rawApi);
    }

    public BasicDBObject fetchAttackerTokenDoc(int apiCollectionId) {
        throw new IllegalStateException("Not implemented");
    }
}
