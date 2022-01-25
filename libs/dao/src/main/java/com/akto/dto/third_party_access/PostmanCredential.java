package com.akto.dto.third_party_access;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class PostmanCredential extends Credential {
    private String workspaceId;
    private String apiKey;

    public PostmanCredential() {}

    public PostmanCredential(String name, String workspaceId, String apiKey) {
        super(Type.POSTMAN,-1, name);
        this.workspaceId = workspaceId;
        this.apiKey = apiKey;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
