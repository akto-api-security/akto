package com.akto.dto.settings;

import org.bson.codecs.pojo.annotations.BsonIgnore;

import java.util.regex.Pattern;

public class DefaultPayload {

    public static final String ID = "_id";
    private String id;

    public static final String PATTERN = "pattern";
    private String pattern;

    public String author;
    private int createdTs;

    public static final String UPDATED_TS = "updatedTs";
    private int updatedTs;

    public static final String SCANNED_EXISTING_DATA = "scannedExistingData";
    private boolean scannedExistingData;

    @BsonIgnore
    private Pattern regexPattern;
    public DefaultPayload() {}
    public DefaultPayload(String id, String pattern, String author, int createdTs, int updatedTs, boolean scannedExistingData) {
        this.id = id;
        this.pattern = pattern;
        this.author = author;
        this.createdTs = createdTs;
        this.updatedTs = updatedTs;
        this.regexPattern = Pattern.compile(pattern);
        this.scannedExistingData = scannedExistingData;
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
        this.regexPattern = Pattern.compile(pattern);
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

    public Pattern getRegexPattern() {
        return regexPattern;
    }

    public boolean getScannedExistingData() {
        return scannedExistingData;
    }

    public void setScannedExistingData(boolean scannedExistingData) {
        this.scannedExistingData = scannedExistingData;
    }

    @Override
    public String toString() {
        return "DefaultPayload{" +
                "id='" + id + '\'' +
                ", pattern='" + pattern + '\'' +
                ", author='" + author + '\'' +
                ", createdTs=" + createdTs +
                ", updatedTs=" + updatedTs +
                ", scannedExistingData=" + scannedExistingData +
                '}';
    }
}
