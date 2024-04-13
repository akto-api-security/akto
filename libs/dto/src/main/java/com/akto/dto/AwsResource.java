package com.akto.dto;

public class AwsResource {
    // private AwsResourceType resourceType;
    private String resourceName;
    private String resourceId;

    public AwsResource() {
    }

    public AwsResource(String resourceName, String resourceId) {
        this.resourceName = resourceName;
        this.resourceId = resourceId;
        // this.resourceType = awsResourceType;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public String getResourceName() {
        return this.resourceName;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getResourceId() {
        return this.resourceId;
    }

    // public void setAwsResourceType(AwsResourceType resourceType) {
    // this.resourceType = resourceType;
    // }

    // public AwsResourceType getAwsResourceType() {
    // return this.resourceType;
    // }
}
