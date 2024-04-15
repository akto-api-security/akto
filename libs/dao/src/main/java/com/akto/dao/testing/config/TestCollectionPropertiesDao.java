package com.akto.dao.testing.config;

import com.akto.dao.AccountsContextDao;
import com.akto.dao.AuthMechanismsDao;
import com.akto.dao.CustomAuthTypeDao;
import com.akto.dao.MCollection;
import com.akto.dao.context.Context;
import com.akto.dto.CustomAuthType;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.config.TestCollectionProperty;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.CreateCollectionOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class TestCollectionPropertiesDao extends AccountsContextDao<TestCollectionProperty> {

    public static final TestCollectionPropertiesDao instance = new TestCollectionPropertiesDao();

    public void createIndicesIfAbsent() {
        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        String[] fieldNames = {TestCollectionProperty.API_COLLECTION_ID, TestCollectionProperty.NAME};
        MCollection.createUniqueIndex(getDBName(), getCollName(), fieldNames,false);
    }

    public static TestCollectionProperty createDefaultAuthToken() {
        List<CustomAuthType> authTypes = CustomAuthTypeDao.instance.findAll(CustomAuthType.ACTIVE,true);
        List<String> authTypesStr = new ArrayList<>();
        for(CustomAuthType authType: authTypes) {
            authTypesStr.add(authType.getName());
        }
        List<GlobalEnums.TestCategory> testCategoryList = Arrays.asList(GlobalEnums.TestCategory.NO_AUTH, GlobalEnums.TestCategory.BOLA);
        TestCollectionProperty tProp = new TestCollectionProperty(TestCollectionProperty.Id.AUTH_TOKEN.name(), "Default", 0, authTypesStr, testCategoryList, 0);

        return tProp;
    }

    public static TestCollectionProperty createDefaultAttackerToken() {
        AuthMechanism authMechanism = AuthMechanismsDao.instance.findOne(new BasicDBObject());
        if (authMechanism == null) return null;
        List<String> authMechanismStrs = Collections.singletonList("Configured attacker token");
        List<GlobalEnums.TestCategory> testCategoryList = Collections.singletonList(GlobalEnums.TestCategory.BOLA);
        TestCollectionProperty tProp = new TestCollectionProperty(TestCollectionProperty.Id.ATTACKER_ACCOUNT_ROLE.name(), "Default", 0, authMechanismStrs, testCategoryList, 0);
        return tProp;
    }

    public static List<TestCollectionProperty> fetchConfigs(int apiCollectionId) {

        if (apiCollectionId == 0) {
            List<TestCollectionProperty> ret = new ArrayList<>();
            ret.add(createDefaultAuthToken());
            TestCollectionProperty attackerTokenProp = createDefaultAttackerToken();
            if (attackerTokenProp != null) {
                ret.add(attackerTokenProp);
            }
            return ret;

        } else {
            return instance.findAll(TestCollectionProperty.API_COLLECTION_ID, apiCollectionId);
        }
    }

    private TestCollectionPropertiesDao() {}

    @Override
    public String getCollName() {
        return "test_collection_properties";
    }

    @Override
    public Class<TestCollectionProperty> getClassT() {
        return TestCollectionProperty.class;
    }
}
