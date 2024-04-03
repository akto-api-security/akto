package com.akto.open_api.parser;

import com.akto.dto.upload.FileUploadError;
import com.akto.dto.upload.SwaggerUploadLog;

import java.util.List;

public class ParserResult {
    private int totalCount;
    private List<FileUploadError> fileErrors;

    private List<SwaggerUploadLog> uploadLogs;

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public List<FileUploadError> getFileErrors() {
        return fileErrors;
    }

    public void setFileErrors(List<FileUploadError> fileErrors) {
        this.fileErrors = fileErrors;
    }

    public List<SwaggerUploadLog> getUploadLogs() {
        return uploadLogs;
    }

    public void setUploadLogs(List<SwaggerUploadLog> uploadLogs) {
        this.uploadLogs = uploadLogs;
    }
}
