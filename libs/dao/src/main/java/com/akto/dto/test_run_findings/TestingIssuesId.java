package com.akto.dto.test_run_findings;

import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.util.enums.GlobalEnums.TestSubCategory;

import java.util.Objects;

import static com.akto.util.enums.GlobalEnums.TestErrorSource;

public class TestingIssuesId {
    private ApiInfoKey apiInfoKey;
    private TestErrorSource testErrorSource;
    private TestSubCategory testSubCategory;

    public TestingIssuesId(ApiInfoKey apiInfoKey, TestErrorSource source, TestSubCategory category) {
        this.apiInfoKey = apiInfoKey;
        this.testErrorSource = source;
        this.testSubCategory = category;
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
                    && id.testErrorSource == this.testErrorSource;
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


}
