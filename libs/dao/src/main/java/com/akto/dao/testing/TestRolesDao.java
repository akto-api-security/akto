package com.akto.dao.testing;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.RawApi;
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
        TestRoles role = new TestRoles(new ObjectId(), roleName, endpointLogicalGroupId, new ArrayList<>(),userName,createdTs, createdTs, null);

        this.insertOne(role);
        this.getLogger().info("Created test role with name :{}, and logical group id : {}", roleName, endpointLogicalGroupId.toHexString());
        return role;
    }

    public AuthMechanism fetchAttackerToken(int apiCollectionId, RawApi rawApi) {
        TestRoles testRoles = TestRolesDao.instance.findOne(TestRoles.NAME, "ATTACKER_TOKEN_ALL");
        if (testRoles != null && testRoles.getAuthWithCondList().size() > 0) {
            List<AuthWithCond> authWithCondList = testRoles.getAuthWithCondList();
            AuthWithCond firstAuth = authWithCondList.get(0);
            AuthMechanism defaultAuthMechanism = firstAuth.getAuthMechanism();
            if(firstAuth.getRecordedLoginFlowInput()!=null){
                defaultAuthMechanism.setRecordedLoginFlowInput(firstAuth.getRecordedLoginFlowInput());
            }
            if (rawApi == null) {
                return defaultAuthMechanism;
            } else {
                try {
                    Map<String, List<String>> reqHeaders = rawApi.getRequest().getHeaders();
                    for (AuthWithCond authWithCond: authWithCondList)  {
                        Map<String, String> headerKVPairs = authWithCond.getHeaderKVPairs();
                        if (headerKVPairs == null) continue;

                        boolean allHeadersMatched = true;
                        for(String hKey: headerKVPairs.keySet()) {
                            String hVal = authWithCond.getHeaderKVPairs().get(hKey);
                            if (reqHeaders.containsKey(hKey.toLowerCase())) {
                                if (!reqHeaders.get(hKey.toLowerCase()).contains(hVal)) {
                                    allHeadersMatched = false;
                                    break;
                                }
                            }
                        }

                        if (allHeadersMatched) {
                            defaultAuthMechanism = authWithCond.getAuthMechanism();
                            if(authWithCond.getRecordedLoginFlowInput()!=null){
                                defaultAuthMechanism.setRecordedLoginFlowInput(authWithCond.getRecordedLoginFlowInput());
                            }
                            return defaultAuthMechanism;
                        }
                    }
                } catch (Exception e) {
                    return defaultAuthMechanism;
                }
            }

            return defaultAuthMechanism;
        }

        return null;
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
