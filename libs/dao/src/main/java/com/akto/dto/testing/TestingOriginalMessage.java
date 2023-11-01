package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import org.bson.types.ObjectId;

public class TestingOriginalMessage {
    private String originalMessage;

    private ObjectId testingRunResultSummaryId;

    public static final String TESTING_RUN_RESULT_SUMMARY_ID = "testingRunResultSummaryId";
    private ApiInfo.ApiInfoKey apiInfoKey;

    public static final String API_INFO_KEY = "apiInfoKey";

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

    public ObjectId getTestingRunResultSummaryId() {
        return testingRunResultSummaryId;
    }

    public void setTestingRunResultSummaryId(ObjectId testingRunResultSummaryId) {
        this.testingRunResultSummaryId = testingRunResultSummaryId;
    }

    public ApiInfo.ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }
}
