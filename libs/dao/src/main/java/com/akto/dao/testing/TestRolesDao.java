package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dto.testing.TestRoles;
import com.akto.util.enums.MongoDBEnums;
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

    public TestRoles createTestRole (String roleName, ObjectId endpointLogicalGroupId) {
        ObjectId id = new ObjectId();
        TestRoles role = new TestRoles();
        role.setEndpointLogicalGroupId(endpointLogicalGroupId);
        role.setId(id);
        role.setName(roleName);

        this.insertOne(role);
        this.getLogger().info("Created test role with name :{}, and logical group id : {}", roleName, endpointLogicalGroupId.toHexString());
        return role;
    }
}
