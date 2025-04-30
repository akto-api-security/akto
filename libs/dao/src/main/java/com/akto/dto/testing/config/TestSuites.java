package com.akto.dto.testing.config;

import java.util.List;
import java.util.Objects;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;


public class TestSuites {

    public static final String ID = "_id";
    private ObjectId id;

    public static final String NAME = "name";
    private String name;

    public static final String SUB_CATEGORY_LIST = "subCategoryList";
    private List<String> subCategoryList;

    public static final String CREATED_BY = "createdBy";
    private String createdBy;

    public static final String LAST_UPDATED = "lastUpdated";
    private long lastUpdated;

    public static final String CREATED_AT = "createdAt";
    private long createdAt;

    @BsonIgnore
    private String hexId;

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

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getHexId() {
        if (hexId == null) {
            return (this.id != null) ? this.id.toHexString() : null;
        }
        return this.hexId;
    }    

    public void setHexId(String hexId) {
        this.hexId = hexId;
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