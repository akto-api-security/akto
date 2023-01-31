package com.akto.dto.testing;

import org.bson.types.ObjectId;

public class EndpointLogicalGroup {
    public static final String GROUP_NAME_SUFFIX = "_endpoint-logical-group";
    private ObjectId id;
    private int createdTs;
    private int updatedTs;
    private String createdBy;
    private String groupName;
    private TestingEndpoints testingEndpoints;

    public EndpointLogicalGroup() {}
    public EndpointLogicalGroup(ObjectId id, int createdTs,int updatedTs, String createdBy, String groupName, TestingEndpoints testingEndpoints) {
        this.id = id;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
        this.createdBy = createdBy;
        this.groupName = groupName;
        this.testingEndpoints = testingEndpoints;
    }
    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public static final String GROUP_NAME = "groupName";

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public TestingEndpoints getTestingEndpoints() {
        return testingEndpoints;
    }

    public void setTestingEndpoints(TestingEndpoints testingEndpoints) {
        this.testingEndpoints = testingEndpoints;
    }

    public int getUpdatedTs() {
        return updatedTs;
    }

    public void setUpdatedTs(int updatedTs) {
        this.updatedTs = updatedTs;
    }
}
