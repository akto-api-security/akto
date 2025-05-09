package com.akto.dto.files;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.upload.FileUpload;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

public class File {

    @BsonId
    private ObjectId id;

    private String type;

    private int uploadTimestamp;

    private String compressedContent;

    public File() {
    }

    public File(String fileType, String compressedContent) {
        this.type = fileType;
        this.compressedContent = compressedContent;
        this.uploadTimestamp = Context.now();
    }

    public File(ObjectId id, String uploadType, int uploadTimestamp, String compressedContent) {
        this.id = id;
        this.type = uploadType;
        this.uploadTimestamp = uploadTimestamp;
        this.compressedContent = compressedContent;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public int getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(int uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public String getCompressedContent() {
        return compressedContent;
    }

    public void setCompressedContent(String compressedContent) {
        this.compressedContent = compressedContent;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
