package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import com.akto.dto.CollectionConditions.TestConfigsAdvancedSettings;

import lombok.Getter;
import lombok.Setter;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Map;


public class TestingRunConfig {

    @BsonId
    private int id;
    private Map<Integer, List<ApiInfo.ApiInfoKey>> collectionWiseApiInfoKey;
    private List<String> testSubCategoryList;
    private ObjectId authMechanismId;
    @BsonIgnore
    String strAuthMechanismId;

    private String testRoleId;
    private String overriddenTestAppUrl;

    private List<TestConfigsAdvancedSettings> configsAdvancedSettings;
    private boolean cleanUp;

    private List<String> testSuiteIds;

    public static final String AUTO_TICKETING_DETAILS = "autoTicketingDetails";

    private AutoTicketingDetails autoTicketingDetails;

    @BsonIgnore
    @Getter
    @Setter
    private String conversationId;

    public TestingRunConfig() {}

    public TestingRunConfig(int id, Map<Integer, List<ApiInfo.ApiInfoKey>> collectionWiseApiInfoKey,
            List<String> testSubCategoryList, ObjectId authMechanismId, String overriddenTestAppUrl,
            String testRoleId) {
        this(id, collectionWiseApiInfoKey, testSubCategoryList, authMechanismId, overriddenTestAppUrl, testRoleId, false, null);
    }

    public TestingRunConfig(int id, Map<Integer, List<ApiInfo.ApiInfoKey>> collectionWiseApiInfoKey,
        List<String> testSubCategoryList, ObjectId authMechanismId, String overriddenTestAppUrl, String testRoleId,
        boolean cleanUp, AutoTicketingDetails autoTicketingDetails) {
        this.id = id;
        this.collectionWiseApiInfoKey = collectionWiseApiInfoKey;
        this.testSubCategoryList = testSubCategoryList;
        this.authMechanismId = authMechanismId;
        this.overriddenTestAppUrl = overriddenTestAppUrl;
        this.testRoleId = testRoleId;
        this.cleanUp = cleanUp;
        this.autoTicketingDetails = autoTicketingDetails;
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

    public void rebaseOn(TestingRunConfig that) {
        if (that == null) return;

        if (this == that) return;

        if (this.collectionWiseApiInfoKey == null) {
            this.collectionWiseApiInfoKey = that.collectionWiseApiInfoKey;
        }

        if (this.testSubCategoryList == null) {
            this.testSubCategoryList = that.testSubCategoryList;
        }

        if (this.authMechanismId == null) {
            this.authMechanismId = that.authMechanismId;
        }

        if (this.overriddenTestAppUrl == null) {
            this.overriddenTestAppUrl = that.overriddenTestAppUrl;
        }

        if(this.testRoleId == null) {
            this.testRoleId = that.testRoleId;
        }

        this.cleanUp = that.cleanUp;
    }
    
    public String getTestRoleId() {
        return testRoleId;
    }

    public void setTestRoleId(String testRoleId) {
        this.testRoleId = testRoleId;
    }

    public String getStrAuthMechanismId() {
        if (strAuthMechanismId == null && this.authMechanismId!=null) {
            return this.authMechanismId.toHexString();
        }
        return strAuthMechanismId;
    }
    public void setStrAuthMechanismId(String strAuthMechanismId) {
        this.strAuthMechanismId = strAuthMechanismId;
    }

    public List<TestConfigsAdvancedSettings> getConfigsAdvancedSettings() {
        return configsAdvancedSettings;
    }
    public void setConfigsAdvancedSettings(List<TestConfigsAdvancedSettings> configsAdvancedSettings) {
        this.configsAdvancedSettings = configsAdvancedSettings;
    }

    public boolean getCleanUp() {
        return this.cleanUp;
    }

    public void setCleanUp(boolean cleanUp) {
        this.cleanUp = cleanUp;
    }

    public List<String> getTestSuiteIds() {
        return testSuiteIds;
    }

    public void setTestSuiteIds(List<String> testSuiteIds) {
        this.testSuiteIds = testSuiteIds;
    }

    public AutoTicketingDetails getAutoTicketingDetails() {
        return autoTicketingDetails;
    }

    public void setAutoTicketingDetails(AutoTicketingDetails autoTicketingDetails) {
        this.autoTicketingDetails = autoTicketingDetails;
    }
}
