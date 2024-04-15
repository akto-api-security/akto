package com.akto.dto.testing.config;

import java.util.List;
import java.util.Map;

public class TestCollectionConfig {

    int apiCollectionId;
    public static final String API_COLLECTION_ID = "apiCollectionId";

    Map<TestCollectionProperty.Id, TestCollectionProperty> testCollectionProperties;
    public static final String TEST_COLLECTION_PROPERTIES = "testCollectionProperties";

    public TestCollectionConfig(Map<TestCollectionProperty.Id, TestCollectionProperty> testCollectionProperties, int apiCollectionId) {
        this.testCollectionProperties = testCollectionProperties;
        this.apiCollectionId = apiCollectionId;
    }

    public Map<TestCollectionProperty.Id, TestCollectionProperty> getTestCollectionProperties() {
        return testCollectionProperties;
    }

    public void setTestCollectionProperties(Map<TestCollectionProperty.Id, TestCollectionProperty> testCollectionProperties) {
        this.testCollectionProperties = testCollectionProperties;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
}
