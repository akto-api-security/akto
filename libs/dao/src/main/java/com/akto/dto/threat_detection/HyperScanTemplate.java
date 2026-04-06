package com.akto.dto.threat_detection;

public class HyperScanTemplate {

    private String id;
    private int createdAt;
    public static final String CREATED_AT = "createdAt";
    private int updatedAt;
    public static final String UPDATED_AT = "updatedAt";
    private String fileContent;
    public static final String FILE_CONTENT = "fileContent";
    private String author;
    public static final String AUTHOR = "author";
    private boolean inactive;
    public static final String INACTIVE = "inactive";
    private String source;
    public static final String SOURCE = "source";

    public HyperScanTemplate() {
    }

    public HyperScanTemplate(String id, int createdAt, int updatedAt, String fileContent) {
        this.id = id;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.fileContent = fileContent;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }

    public int getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(int updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getFileContent() {
        return fileContent;
    }

    public void setFileContent(String fileContent) {
        this.fileContent = fileContent;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public boolean getInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    public boolean isInactive() {
        return inactive;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
