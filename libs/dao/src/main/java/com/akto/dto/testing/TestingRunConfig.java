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
    private TestCategory testCategory;
    private ObjectId authMechanismId;

    public TestingRunConfig() {}

    public TestCategory getTestCategory() {
        return testCategory;
    }

    public void setTestCategory(TestCategory testCategory) {
        this.testCategory = testCategory;
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
}
