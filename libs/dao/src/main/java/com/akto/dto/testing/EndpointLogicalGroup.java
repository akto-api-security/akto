package com.akto.dto.testing;

import com.akto.dto.ApiInfo;
import org.bson.types.ObjectId;

import java.util.List;

public class EndpointLogicalGroup {
    private ObjectId id;
    private int createdTs;
    private String createdBy;
    private String groupName;
    private TestingEndpoints testingEndpoints;

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
}
