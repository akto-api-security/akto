package com.akto.dto.settings;

public class DefaultPayload {

    public static final String ID = "_id";
    public String id;

    public static final String PATTERN = "pattern";
    public String pattern;

    public String author;
    public int createdTs;

    public static final String UPDATED_TS = "updatedTs";
    public int updatedTs;

    public DefaultPayload() {}
    public DefaultPayload(String id, String pattern, String author, int createdTs, int updatedTs) {
        this.id = id;
        this.pattern = pattern;
        this.author = author;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getCreatedTs() {
        return createdTs;
    }

    public void setCreatedTs(int createdTs) {
        this.createdTs = createdTs;
    }

    public int getUpdatedTs() {
        return updatedTs;
    }

    public void setUpdatedTs(int updatedTs) {
        this.updatedTs = updatedTs;
    }

    @Override
    public String toString() {
        return "DefaultPayload{" +
                "id='" + id + '\'' +
                ", pattern='" + pattern + '\'' +
                ", author='" + author + '\'' +
                ", createdTs=" + createdTs +
                ", updatedTs=" + updatedTs +
                '}';
    }
}
