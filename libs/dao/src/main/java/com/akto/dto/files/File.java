package com.akto.dto.files;

import com.akto.dao.context.Context;
import com.akto.dto.HttpResponseParams;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

public class File {

    @BsonId
    private ObjectId id;

    private HttpResponseParams.Source source;

    private int uploadTimestamp;

    private String compressedContent;

    public File(HttpResponseParams.Source source, String compressedContent) {
        this.source = source;
        this.compressedContent = compressedContent;
        this.uploadTimestamp = Context.now();
    }

    public File(ObjectId id, HttpResponseParams.Source source, int uploadTimestamp, String compressedContent) {
        this.id = id;
        this.source = source;
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

    public HttpResponseParams.Source getSource() {
        return source;
    }

    public void setSource(HttpResponseParams.Source source) {
        this.source = source;
    }
}
