package com.akto.dto.test_run_findings;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.testing.sources.TestSourceConfig;
import com.akto.util.enums.GlobalEnums.TestSubCategory;
import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.Objects;

import static com.akto.util.enums.GlobalEnums.TestErrorSource;

public class TestingIssuesId {
    public static final String API_KEY_INFO = "apiInfoKey";
    private ApiInfoKey apiInfoKey;
    private TestErrorSource testErrorSource;
    public static final String TEST_SUB_CATEGORY = "testSubCategory";
    private TestSubCategory testSubCategory;
    public static final String TEST_CATEGORY_FROM_SOURCE_CONFIG = "testCategoryFromSourceConfig";
    private String testCategoryFromSourceConfig;
    @BsonIgnore
    private TestSourceConfig testSourceConfig;

    public TestingIssuesId(ApiInfoKey apiInfoKey, TestErrorSource source, TestSubCategory category) {
        this.apiInfoKey = apiInfoKey;
        this.testErrorSource = source;
        this.testSubCategory = category;
    }

    public TestingIssuesId(ApiInfoKey apiInfoKey, TestErrorSource source, TestSubCategory category, String testCategoryFromSourceConfig) {
        this.apiInfoKey = apiInfoKey;
        this.testErrorSource = source;
        this.testSubCategory = category;
        this.testCategoryFromSourceConfig = testCategoryFromSourceConfig;
    }

    public TestingIssuesId() {
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        } else if (o instanceof TestingIssuesId) {
            TestingIssuesId id = (TestingIssuesId) o;
            return id.apiInfoKey.equals(this.apiInfoKey)
                    && id.testSubCategory == this.testSubCategory
                    && id.testErrorSource == this.testErrorSource
                    && Objects.equals(id.testCategoryFromSourceConfig, this.testCategoryFromSourceConfig);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiInfoKey, testErrorSource, testSubCategory);
    }

    @Override
    public String toString() {
        return "{ApiInfoKey : " + this.apiInfoKey.toString() + ", testSubCategory : " + testSubCategory.name()
                + ", testErrorSource : " + testErrorSource.name();
    }

    public void setApiInfoKey(ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public void setTestErrorSource(TestErrorSource testErrorSource) {
        this.testErrorSource = testErrorSource;
    }

    public void setTestSubCategory(TestSubCategory testSubCategory) {
        this.testSubCategory = testSubCategory;
    }

    public ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public TestErrorSource getTestErrorSource() {
        return testErrorSource;
    }

    public TestSubCategory getTestSubCategory() {
        return testSubCategory;
    }


    public String getTestCategoryFromSourceConfig() {
        return testCategoryFromSourceConfig;
    }

    public void setTestCategoryFromSourceConfig(String testCategoryFromSourceConfig) {
        this.testCategoryFromSourceConfig = testCategoryFromSourceConfig;
    }

    public TestSourceConfig getTestSourceConfig() {
        return testSourceConfig;
    }

    public void setTestSourceConfig(TestSourceConfig testSourceConfig) {
        this.testSourceConfig = testSourceConfig;
    }
}
