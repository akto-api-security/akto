package com.akto.dto.upload;

import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class FileUploadLog {
    public FileUploadLog() {
    }

    @BsonId
    protected ObjectId id;

    protected String uploadId;

    public enum UploadLogStatus {
        SUCCESS,
        ERROR
    }

    protected List<FileUploadError> errors;

    protected String url;

    private String aktoFormat;

    public String getAktoFormat() {
        return aktoFormat;
    }

    public void setAktoFormat(String aktoFormat) {
        this.aktoFormat = aktoFormat;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getUploadId() {
        return uploadId;
    }

    public void setUploadId(String uploadId) {
        this.uploadId = uploadId;
    }

    public List<FileUploadError> getErrors() {
        return errors;
    }

    public void setErrors(List<FileUploadError> errors) {
        this.errors = errors;
    }

    public void addError(FileUploadError error){
        if (errors == null){
            errors = new ArrayList<>();
        }
        errors.add(error);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
