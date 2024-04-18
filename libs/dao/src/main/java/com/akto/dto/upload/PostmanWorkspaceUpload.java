package com.akto.dto.upload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostmanWorkspaceUpload extends FileUpload {

    public PostmanWorkspaceUpload(){

    }

    public PostmanWorkspaceUpload(UploadType uploadType, UploadStatus uploadStatus) {
        super(uploadType, uploadStatus);
    }
    private String postmanWorkspaceId;
    private Map<String, String> postmanCollectionIds;
    private Map<String, String> collectionIdToFileIdMap;

    private Map<String, List<FileUploadError>> collectionErrors;

    public void addCollection(String collectionId, String collectionName, String fileId){
        if(postmanCollectionIds == null){
            postmanCollectionIds =  new HashMap<>();
        }
        if(collectionIdToFileIdMap == null){
            collectionIdToFileIdMap =  new HashMap<>();
        }
        postmanCollectionIds.put(collectionId, collectionName);
        collectionIdToFileIdMap.put(collectionId, fileId);
    }

    public String getPostmanWorkspaceId() {
        return postmanWorkspaceId;
    }

    public void setPostmanWorkspaceId(String postmanWorkspaceId) {
        this.postmanWorkspaceId = postmanWorkspaceId;
    }

    public Map<String, String> getPostmanCollectionIds() {
        return postmanCollectionIds;
    }

    public void setPostmanCollectionIds(Map<String, String> postmanCollectionIds) {
        this.postmanCollectionIds = postmanCollectionIds;
    }

    public Map<String, String> getCollectionIdToFileIdMap() {
        return collectionIdToFileIdMap;
    }

    public void setCollectionIdToFileIdMap(Map<String, String> collectionIdToFileIdMap) {
        this.collectionIdToFileIdMap = collectionIdToFileIdMap;
    }

    public Map<String, List<FileUploadError>> getCollectionErrors() {
        return collectionErrors;
    }

    public void setCollectionErrors(Map<String, List<FileUploadError>> collectionErrors) {
        this.collectionErrors = collectionErrors;
    }

    public void addError(String collectionId, List<FileUploadError> error){
        if(collectionErrors == null){
            collectionErrors = new java.util.HashMap<>();
        }
        if(!collectionErrors.containsKey(collectionId)){
            collectionErrors.put(collectionId, new ArrayList<>());
        }
        collectionErrors.get(collectionId).addAll(error);
    }
}
