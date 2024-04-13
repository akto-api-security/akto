package com.akto.dto.upload;

import java.util.List;

public class SwaggerFileUpload extends FileUpload{

    private String swaggerFileId;
    private String collectionName;

    private List<FileUploadError> errors;

    public String getSwaggerFileId() {
        return swaggerFileId;
    }

    public void setSwaggerFileId(String swaggerFileId) {
        this.swaggerFileId = swaggerFileId;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }

    public List<FileUploadError> getErrors() {
        return errors;
    }

    public void setErrors(List<FileUploadError> errors) {
        this.errors = errors;
    }
}
