package com.akto.dto.upload;

public class PostmanUploadLog extends FileUploadLog {
    public PostmanUploadLog() {
    }

    private String postmanWorkspaceId;
    private String postmanCollectionId;

    public String getPostmanWorkspaceId() {
        return postmanWorkspaceId;
    }

    public String getPostmanCollectionId() {
        return postmanCollectionId;
    }

    public void setPostmanWorkspaceId(String postmanWorkspaceId) {
        this.postmanWorkspaceId = postmanWorkspaceId;
    }

    public void setPostmanCollectionId(String postmanCollectionId) {
        this.postmanCollectionId = postmanCollectionId;
    }
}
