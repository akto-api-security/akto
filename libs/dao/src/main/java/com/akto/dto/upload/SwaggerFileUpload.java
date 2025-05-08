package com.akto.dto.upload;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SwaggerFileUpload extends FileUpload{

    private String swaggerFileId;
    private String collectionName;
    private int collectionId;
    private int uploadTs;

    private List<FileUploadError> errors;

}
