package com.akto.dto.test_run_findings;

import org.bson.codecs.pojo.annotations.BsonProperty;

import static com.akto.util.enums.GlobalEnums.*;

public class TestingIssuesId {

    public EndpointParameters getEndpointParameters() {
        return endpointParameters;
    }

    public TestErrorSource getTestErrorSource() {
        return testErrorSource;
    }

    public TestCategory getTestCategory() {
        return testCategory;
    }


    TestingIssuesId(EndpointParameters endpointParameters, TestErrorSource source, TestCategory category) {
        this.endpointParameters = endpointParameters;
        this.testErrorSource = source;
        this.testCategory = category;
    }

    public static final String ENDPOINT_PARAMETERS = "endpoint_parameters";
    @BsonProperty(value = ENDPOINT_PARAMETERS)
    private final EndpointParameters endpointParameters;

    public static final String ISSUE_SOURCE = "issue_source";
    @BsonProperty(value = ISSUE_SOURCE)
    private final TestErrorSource testErrorSource;

    public static final String TEST_CATEGORY = "test_category";
    @BsonProperty(value = TEST_CATEGORY)
    private final TestCategory testCategory;

}
