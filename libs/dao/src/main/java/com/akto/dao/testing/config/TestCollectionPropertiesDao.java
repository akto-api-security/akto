package com.akto.dao.testing.config;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dao.testing.TestRolesDao;
import com.akto.dto.ApiInfo;
import com.akto.dto.CustomAuthType;
import com.akto.dto.testing.AuthMechanism;
import com.akto.dto.testing.TestRoles;
import com.akto.dto.testing.TestingRun;
import com.akto.dto.testing.config.TestCollectionProperty;
import com.akto.util.enums.GlobalEnums;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.akto.dto.CustomAuthType.API_COLLECTION_IDS;
import static com.akto.dto.CustomAuthType.TYPE_OF_TOKENS;

public class TestCollectionPropertiesDao extends AccountsContextDao<TestCollectionProperty> {

    public static final TestCollectionPropertiesDao instance = new TestCollectionPropertiesDao();

    public void createIndicesIfAbsent() {
        String dbName = Context.accountId.get()+"";
        createCollectionIfAbsent(dbName, getCollName(), new CreateCollectionOptions());

        String[] fieldNames = {TestCollectionProperty.API_COLLECTION_ID, TestCollectionProperty.NAME};
        MCollection.createUniqueIndex(getDBName(), getCollName(), fieldNames,false);
    }

    public static TestCollectionProperty createAuthToken(int apiCollectionId, CustomAuthType.TypeOfToken typeOfToken) {
        Bson fActive = Filters.eq(CustomAuthType.ACTIVE,true);
        Bson fApiCollId = Filters.in(API_COLLECTION_IDS, apiCollectionId, 0);
        Bson fTypeOfToken = Filters.in(CustomAuthType.TYPE_OF_TOKENS, typeOfToken);

//        if (apiCollectionId == 0) {
            fApiCollId = Filters.or(Filters.exists(API_COLLECTION_IDS, false), fApiCollId);
//        }

        if (typeOfToken == CustomAuthType.TypeOfToken.AUTH) {
            fTypeOfToken = Filters.or(Filters.exists(TYPE_OF_TOKENS, false), fTypeOfToken);
        }

        List<CustomAuthType> authTypes = CustomAuthTypeDao.instance.findAll(Filters.and(fActive, fApiCollId, fTypeOfToken));
        if (authTypes == null || authTypes.isEmpty()) return null;

        boolean allDefault = true;

        List<String> authTypesStr = new ArrayList<>();
        for(CustomAuthType authType: authTypes) {
            authTypesStr.add(authType.getName());
            if (authType.getApiCollectionIds() != null && authType.getApiCollectionIds().contains(apiCollectionId)) {
                allDefault = false;
            }
        }
        List<GlobalEnums.TestCategory> testCategoryList = Arrays.asList(GlobalEnums.TestCategory.NO_AUTH, GlobalEnums.TestCategory.BOLA);

        if (allDefault) apiCollectionId = 0;

        TestCollectionProperty tProp = new TestCollectionProperty(TestCollectionProperty.Id.AUTH_TOKEN.name(), "Default", 0, authTypesStr, testCategoryList, apiCollectionId);

        return tProp;
    }

    public static TestRoles fetchTestToleForProp(int apiCollectionId, TestCollectionProperty.Id propId){
        String name = propId.name();
        Bson fApiCollId = Filters.in(API_COLLECTION_IDS, apiCollectionId, 0);
        Bson fName = Filters.regex(TestRoles.NAME, ".*"+name+".*");

//        if (apiCollectionId == 0) {
            fApiCollId = Filters.or(Filters.exists(API_COLLECTION_IDS, false), fApiCollId);
//        }

        return TestRolesDao.instance.findOne(Filters.and(fApiCollId, fName));
    }

    public static TestCollectionProperty createTestRole(int apiCollectionId, TestCollectionProperty.Id propId) {
        TestRoles role = fetchTestToleForProp(apiCollectionId, propId);
        if (role == null) return null;
        List<String> roleNames = Collections.singletonList(role.getName());
        List<GlobalEnums.TestCategory> testCategoryList = propId.getImpactingCategories();

        if (role.getApiCollectionIds() == null || !role.getApiCollectionIds().contains(apiCollectionId)) apiCollectionId = 0;
        TestCollectionProperty tProp = new TestCollectionProperty(propId.name(), "Default", 0, roleNames, testCategoryList, apiCollectionId);
        return tProp;
    }
    public static TestCollectionProperty createEndpointNames(int apiCollectionId, TestCollectionProperty.Id propId) {
        int collId = 0;
        switch (propId) {
            case LOGIN_ENDPOINT:
                collId = 111_111_128;
                break;
            case SIGNUP_ENDPOINT:
                collId = 111_111_129;
                break;
            case PASSWORD_RESET_ENDPOINT:
                collId = 111_111_130;
                break;
            default:
                throw new IllegalStateException("Invalid config requested for: " + propId.name());
        }

        Bson filterQ = Filters.eq(ApiInfo.COLLECTION_IDS, collId);

        if (apiCollectionId == 0) {
            filterQ = Filters.and(filterQ, Filters.eq(ApiInfo.COLLECTION_IDS, apiCollectionId));
        }

        boolean exists = ApiInfoDao.instance.findOne(filterQ) != null;
        List<String> valuesList = Collections.singletonList(collId+"");
        TestCollectionProperty tProp = new TestCollectionProperty(propId.name(), "Default", 0, valuesList, propId.getImpactingCategories(), apiCollectionId);

        return tProp;
    }

    public static List<TestCollectionProperty> fetchConfigs(int apiCollectionId) {
        List<TestCollectionProperty> ret = new ArrayList<>();
        for(TestCollectionProperty.Id propId: TestCollectionProperty.Id.values()) {
            TestCollectionProperty prop = null;
            switch (propId) {
                case AUTH_TOKEN:
                    prop = createAuthToken(apiCollectionId, CustomAuthType.TypeOfToken.AUTH);
                    break;
                case LOCKED_ACCOUNT_SYSTEM_ROLE:
                case ATTACKER_TOKEN:
                case LOGGED_OUT_SYSTEM_ROLE:
                case ADMIN:
                case MEMBER:
                case LOGIN_2FA_INCOMPLETE_SYSTEM_ROLE:
                    prop = createTestRole(apiCollectionId, propId);
                    break;
//                case SESSION_TOKEN_HEADER_KEY:
//                    prop = createAuthToken(apiCollectionId, CustomAuthType.TypeOfToken.SESSION);
//                    break;
//                case CSRF_TOKEN_HEADER:
//                    prop = createAuthToken(apiCollectionId, CustomAuthType.TypeOfToken.CSRF);
//                    break;
                case PASSWORD_RESET_ENDPOINT:
                case SIGNUP_ENDPOINT:
                case LOGIN_ENDPOINT:
                    prop = createEndpointNames(apiCollectionId, propId);
                    break;
            }

            if (prop != null) {
                ret.add(prop);
            }
        }
        return ret;
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
