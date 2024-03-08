package com.akto.dto.upload;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;


public class FileUpload {

    public FileUpload() {
    }

    public FileUpload(UploadType uploadType, UploadStatus uploadStatus) {
        this.uploadType = uploadType;
        this.uploadStatus = uploadStatus;
        this.ingestionComplete = false;
    }
    @BsonId
    private ObjectId id;

    private UploadStatus uploadStatus;

    public enum UploadStatus{
        IN_PROGRESS,
        SUCCEEDED,
        FAILED
    }

    private UploadType uploadType;

    public enum UploadType{
        POSTMAN_WORKSPACE,
        POSTMAN_FILE,
        POSTMAN_COLLECTION,
        SWAGGER_FILE,
    }

    protected boolean ingestionComplete;

    protected String fatalError;

    protected int count;

    private boolean markedForDeletion;

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public UploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(UploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public UploadType getUploadType() {
        return uploadType;
    }

    public void setUploadType(UploadType uploadType) {
        this.uploadType = uploadType;
    }


    public String getFatalError() {
        return fatalError;
    }

    public void setFatalError(String fatalError) {
        this.fatalError = fatalError;
    }


    public boolean getIngestionComplete() {
        return ingestionComplete;
    }

    public void setIngestionComplete(boolean ingestionComplete) {
        this.ingestionComplete = ingestionComplete;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public boolean getMarkedForDeletion() {
        return markedForDeletion;
    }

    public void setMarkedForDeletion(boolean markedForDeletion) {
        this.markedForDeletion = markedForDeletion;
    }
}
