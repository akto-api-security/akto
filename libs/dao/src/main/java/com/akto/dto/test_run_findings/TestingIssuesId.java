package com.akto.dto.test_run_findings;

import org.bson.codecs.pojo.annotations.BsonProperty;

import static com.akto.util.enums.GlobalEnums.*;
import com.akto.dto.ApiInfo.ApiInfoKey;

public class TestingIssuesId {
//    public static final String ENDPOINT_PARAMETERS = "endpoint_parameters";
//    @BsonProperty(value = ENDPOINT_PARAMETERS)

//    public static final String ISSUE_SOURCE = "issue_source";
//    @BsonProperty(value = ISSUE_SOURCE)

//    public static final String TEST_CATEGORY = "test_category";
//    @BsonProperty(value = TEST_CATEGORY)

    private final ApiInfoKey apiInfoKey;
    private final TestErrorSource testErrorSource;
    private final TestCategory testCategory;
    public TestingIssuesId(ApiInfoKey apiInfoKey, TestErrorSource source, TestCategory category) {
        this.apiInfoKey = apiInfoKey;
        this.testErrorSource = source;
        this.testCategory = category;
    }

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
