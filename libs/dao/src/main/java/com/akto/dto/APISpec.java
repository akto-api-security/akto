package com.akto.dto;

import com.akto.dao.context.Context;

import org.bson.codecs.pojo.annotations.BsonId;

public class APISpec {
    public enum Type {
        YAML, JSON
    }

    @BsonId
    int id;
    Type type;
    int userId;
    String filename;
    String content;

    public APISpec() {
    }

    public APISpec(Type type, int userId, String filename, String content) {
        this.type = type;
        this.userId = userId;
        this.filename = filename;
        this.id = Context.now();
        this.content = content;
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

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getContent() {
        return this.content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
