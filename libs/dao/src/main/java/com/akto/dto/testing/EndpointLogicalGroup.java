package com.akto.dto.testing;

import java.util.Objects;
import java.util.Random;

public class EndpointLogicalGroup {
    public static final String GROUP_NAME_SUFFIX = "_endpoint-logical-group";
    private Integer id;
    private int createdTs;
    private int updatedTs;
    private String createdBy;
    private String groupName;
    private TestingEndpoints testingEndpoints;
    private String groupType;
    private int endpointsRefreshTs;

    public EndpointLogicalGroup() {}
    public EndpointLogicalGroup(int createdTs,int updatedTs, String createdBy, String groupName, TestingEndpoints testingEndpoints, String groupType, int endpointsRefreshTs) {
        this.id = generateID(groupName);
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
        this.createdBy = createdBy;
        this.groupName = groupName;
        this.testingEndpoints = testingEndpoints;
        this.groupType = groupType;
        this.endpointsRefreshTs = endpointsRefreshTs;
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
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

    public String getGroupType() {
        return groupType;
    }

    public void setGroupType(String groupType) {
        this.groupType = groupType;
    }

    public int getEndpointRefreshTs() {
        return endpointsRefreshTs;
    }

    public void setEndpointRefreshTs(int endpointsRefreshTs) {
        this.endpointsRefreshTs = endpointsRefreshTs;
    }

    private int generateID(String name) {
        String SALTCHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < 6) {
            int index = (int) (rnd.nextFloat() * SALTCHARS.length());
            salt.append(SALTCHARS.charAt(index));
        }
        String saltStr = salt.toString();

        String hashGen = name + saltStr;

        return Objects.hash(hashGen);
    }
}
