package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;

import static com.akto.util.enums.GlobalEnums.*;

public class TestingRunConfig {

    @BsonId
    private int id;
    private Map<Integer, List<ApiInfo.ApiInfoKey>> collectionWiseApiInfoKey;
    private List<String> testSubCategoryList;
    private ObjectId authMechanismId;

    private String overriddenTestAppUrl;
    public TestingRunConfig() {}
    public TestingRunConfig(int id, Map<Integer, List<ApiInfo.ApiInfoKey>> collectionWiseApiInfoKey,
                            List<String> testSubCategoryList,
                            ObjectId authMechanismId, String overriddenTestAppUrl) {
        this.id = id;
        this.collectionWiseApiInfoKey = collectionWiseApiInfoKey;
        this.testSubCategoryList = testSubCategoryList;
        this.authMechanismId = authMechanismId;
        this.overriddenTestAppUrl = overriddenTestAppUrl;
    }

    public List<String> getTestSubCategoryList() {
        return testSubCategoryList;
    }

    public void setTestSubCategoryList(List<String> testSubCategoryList) {
        this.testSubCategoryList = testSubCategoryList;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Map<Integer, List<ApiInfo.ApiInfoKey>> getCollectionWiseApiInfoKey() {
        return collectionWiseApiInfoKey;
    }

    public void setCollectionWiseApiInfoKey(Map<Integer, List<ApiInfo.ApiInfoKey>> collectionWiseApiInfoKey) {
        this.collectionWiseApiInfoKey = collectionWiseApiInfoKey;
    }

    public ObjectId getAuthMechanismId() {
        return authMechanismId;
    }

    public void setAuthMechanismId(ObjectId authMechanismId) {
        this.authMechanismId = authMechanismId;
    }

    public String getOverriddenTestAppUrl() {
        return overriddenTestAppUrl;
    }

    public void setOverriddenTestAppUrl(String overriddenTestAppUrl) {
        this.overriddenTestAppUrl = overriddenTestAppUrl;
    }
}
