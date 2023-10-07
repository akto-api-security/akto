package com.akto.dto;   

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
public class APISpec {
    public enum Type {
        YAML, JSON
    }

    Type type;
    int userId;
    String filename;
    String content;
    int apiCollectionId;
    List<Integer> collectionIds;

    public APISpec() {
    }

    public APISpec(Type type, int userId, String filename, String content, int apiCollectionId) {
        this.type = type;
        this.userId = userId;
        this.filename = filename;
        this.content = content;
        this.apiCollectionId = apiCollectionId;
        this.collectionIds = Arrays.asList(apiCollectionId);
    }

    public Type getType() {
        return this.type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getUserId() {
        return this.userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
        if(this.collectionIds == null) {
            this.collectionIds = new ArrayList<>();
        }
        if(!this.collectionIds.contains(apiCollectionId)){
            this.collectionIds.add(apiCollectionId);
        }
    }

    public List<Integer> getCollectionIds() {
        return collectionIds;
    }

    public void setCollectionIds(List<Integer> collectionIds) {
        this.collectionIds = collectionIds;
    }

}
