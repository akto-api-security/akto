package com.akto.dto.testing.config;

import java.util.List;
import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonId;

public class TestSuite {
    @BsonId
    private int id;
    private String name;
    private List<String> subCategoryList;
    private String createdBy;
    private long lastUpdated;
    private long createdAt;

    public static final String FIELD_ID = "_id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_SUB_CATEGORY_LIST = "subCategoryList";
    public static final String FIELD_CREATED_BY = "createdBy";
    public static final String FIELD_LAST_UPDATED = "lastUpdated";
    public static final String FIELD_CREATED_AT = "createdAt";

    public TestSuite() {
    }

    public TestSuite(int id, String name, List<String> subCategoryList, String createdBy, long lastUpdated, long createdAt) {
        this.id = id;
        this.name = name;
        this.subCategoryList = subCategoryList;
        this.createdBy = createdBy;
        this.lastUpdated = lastUpdated;
        this.createdAt = createdAt;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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
                ", id=" + id +
                '}';
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;

        TestSuite testSuite = (TestSuite) obj;

        return id == testSuite.id &&
                lastUpdated == testSuite.lastUpdated &&
                createdAt == testSuite.createdAt &&
                Objects.equals(name, testSuite.name) &&
                Objects.equals(createdBy, testSuite.createdBy) &&
                Objects.equals(subCategoryList, testSuite.subCategoryList);
    }

}