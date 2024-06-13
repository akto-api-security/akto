package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.SensitiveSampleData;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.sources.AuthWithCond;
import com.akto.util.enums.MongoDBEnums;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.apache.commons.lang3.NotImplementedException;
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
        TestRoles role = new TestRoles(new ObjectId(), roleName, endpointLogicalGroupId, new ArrayList<>(),userName,createdTs, createdTs, null);

        this.insertOne(role);
        this.getLogger().info("Created test role with name :{}, and logical group id : {}", roleName, endpointLogicalGroupId.toHexString());
        return role;
    }

    public AuthMechanism fetchAttackerToken(int apiCollectionId) {
        TestRoles testRoles = TestRolesDao.instance.findOne(TestRoles.NAME, "ATTACKER_TOKEN_ALL");
        if (testRoles != null && testRoles.getAuthWithCondList().size() > 0) {
            return testRoles.getAuthWithCondList().get(0).getAuthMechanism();
        } else {
            // return AuthMechanismsDao.instance.findOne(new BasicDBObject());
            return null;
        }
    }

    public AuthMechanism fetchAttackerToken(int apiCollectionId, TestRoles testRoles) {
        if (testRoles != null && testRoles.getAuthWithCondList().size() > 0) {
            return testRoles.getAuthWithCondList().get(0).getAuthMechanism();
        } else {
            // return AuthMechanismsDao.instance.findOne(new BasicDBObject());
            return null;
        }
    }

    public BasicDBObject fetchAttackerTokenDoc(int apiCollectionId) {
        MongoCursor<BasicDBObject> cursor = MCollection.getMCollection(getDBName(), getCollName(), BasicDBObject.class).find(new BasicDBObject(TestRoles.NAME, "ATTACKER_TOKEN_ALL")).cursor();
        if(cursor.hasNext()) {
            BasicDBObject testRole = cursor.next();
            Object authWithCondList = testRole.get(TestRoles.AUTH_WITH_COND_LIST);
            if (authWithCondList instanceof BasicDBList) {
                BasicDBList list = (BasicDBList) authWithCondList;
                if (list.size() > 0) {
                    Object authWithCond = list.get(0);
                    if (authWithCond instanceof BasicDBObject) {
                        BasicDBObject authWithCondObj = (BasicDBObject) authWithCond;
                        Object auth = authWithCondObj.get("authMechanism");
                        if (auth instanceof BasicDBObject) {
                            return (BasicDBObject) auth;
                        }
                    }
                }
            }
        }


        //AuthMechanismsDao.instance.findOneDocument(new BasicDBObject());
        return null;
    }
}
