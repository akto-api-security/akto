package com.akto.dto.test_run_findings;

import com.akto.dto.ApiInfo.ApiInfoKey;

import java.util.Objects;

import static com.akto.util.enums.GlobalEnums.TestCategory;
import static com.akto.util.enums.GlobalEnums.TestErrorSource;

public class TestingIssuesId {
    private ApiInfoKey apiInfoKey;
    private TestErrorSource testErrorSource;
    private TestCategory testCategory;
    public TestingIssuesId(ApiInfoKey apiInfoKey, TestErrorSource source, TestCategory category) {
        this.apiInfoKey = apiInfoKey;
        this.testErrorSource = source;
        this.testCategory = category;
    }

    public TestingIssuesId(){}

    @Override
    public boolean equals(Object o) {
        if (o == this) {return true;}
        else if (o instanceof TestingIssuesId) {
            TestingIssuesId id = (TestingIssuesId) o;
            return id.apiInfoKey.equals(this.apiInfoKey)
                    && id.testCategory == this.testCategory
                    && id.testErrorSource == this.testErrorSource;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiInfoKey, testErrorSource, testCategory);
    }

    public void setApiInfoKey(ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public void setTestErrorSource(TestErrorSource testErrorSource) {
        this.testErrorSource = testErrorSource;
    }

    public void setTestCategory(TestCategory testCategory) {
        this.testCategory = testCategory;
    }

    public ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public TestErrorSource getTestErrorSource() {
        return testErrorSource;
    }

    public TestCategory getTestCategory() {
        return testCategory;
    }


}
