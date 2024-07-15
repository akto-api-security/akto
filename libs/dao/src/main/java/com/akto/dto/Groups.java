package com.akto.dto;

import java.util.List;
import java.util.Set;

public class Groups {
    public static final String COLLECTION_IDS = "collectionIds";
    private List<Integer> collectionIds;
    public static final String NAME = "name";
    private String name;
    public static final String USER_IDS = "userIds";
    // List to Set
    private Set<Integer> userIds;
    public static final String CREATED_BY = "createdBy";
    private String createdBy;
    // last modified
    // modified by

    public Groups() {}

    public Groups(List<Integer> collectionIds, Set<Integer> userIds, String createdBy) {
        this.collectionIds = collectionIds;
        this.name = createdBy;
        this.userIds = userIds;
        this.createdBy = createdBy;
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Integer> getUserIds() {
        return userIds;
    }

    public void setUserIds(Set<Integer> userIds) {
        this.userIds = userIds;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    @Override
    public String toString() {
        return "Groups {" +
                "collectionIds=" + collectionIds +
                ", name='" + name + '\'' +
                ", userIds=" + userIds +
                ", createdBy='" + createdBy + '\'' +
                '}';
    }
}
