package com.akto.dto.test_run_findings;

import com.akto.dto.testing.TestResult;
import org.bson.codecs.pojo.annotations.BsonProperty;

public class TestingErrorId {

    public enum TestErrorSource {
        AUTOMATED_TESTING,
        RUNTIME
    }

    public static final String ENDPOINT_PARAMETERS = "endpoint_parameters";
    @BsonProperty(value = ENDPOINT_PARAMETERS)
    public EndpointParameters endpointParameters;

    public static final String ISSUE_SOURCE = "issue_source";
    @BsonProperty(value = ISSUE_SOURCE)
    public TestErrorSource testErrorSource;

    public static final String TEST_CATEGORY = "test_category";
    @BsonProperty(value = TEST_CATEGORY)
    public TestResult.TestCategory testCategory;

}
