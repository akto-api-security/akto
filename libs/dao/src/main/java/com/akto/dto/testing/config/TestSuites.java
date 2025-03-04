package com.akto.dto.testing.config;

import java.util.List;
import java.util.Objects;


public class TestSuites {

    private String name;
    private List<String> subCategoryList;
    private String createdBy;
    private long lastUpdated;
    private long createdAt;

    public static final String FIELD_NAME = "name";
    public static final String FIELD_SUB_CATEGORY_LIST = "subCategoryList";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_LAST_UPDATED = "lastUpdated";
    public static final String FIELD_CREATED_AT = "createdAt";

    public TestSuites() {
    }

    public TestSuites(String name, List<String> subCategoryList, String createdBy, long lastUpdated, long createdAt) {
        this.name = name;
        this.subCategoryList = subCategoryList;
        this.createdBy = createdBy;
        this.lastUpdated = lastUpdated;
        this.createdAt = createdAt;
    }

    public long getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
    public long getLastUpdated() {
        return lastUpdated;
    }
    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    public String getCreatedBy() {
        return createdBy;
    }
    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    
    public List<String> getSubCategoryList() {
        return subCategoryList;
    }
    public void setSubCategoryList(List<String> subCategoryList) {
        this.subCategoryList = subCategoryList;
    }

    @Override
    public String toString() {
        return "TestSuite{" +
                "name='" + name + '\'' +
                ", subCategoryList=" + subCategoryList +
                ", createdBy='" + createdBy + '\'' +
                ", lastUpdated=" + lastUpdated +
                ", createdAt=" + createdAt +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        TestSuites testSuite = (TestSuites) obj;

        return lastUpdated == testSuite.lastUpdated &&
                createdAt == testSuite.createdAt &&
                Objects.equals(name, testSuite.name) &&
                Objects.equals(createdBy, testSuite.createdBy) &&
                Objects.equals(subCategoryList, testSuite.subCategoryList);
    }

}