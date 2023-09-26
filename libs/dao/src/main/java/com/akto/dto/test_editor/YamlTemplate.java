package com.akto.dto.test_editor;

import com.akto.util.enums.GlobalEnums;

public class YamlTemplate {

    private String id;
    private int createdAt;
    public static final String CREATED_AT = "createdAt";
    private String author;
    public static final String AUTHOR = "author";
    private GlobalEnums.YamlTemplateSource source;
    public static final String SOURCE = "source";
    private int updatedAt;
    public static final String UPDATED_AT = "updatedAt";
    private String content;
    public static final String CONTENT = "content";
    private Info info;
    public static final String INFO = "info";
    private String sha;
    public static final String SHA = "sha";
    private String fileName;
    public static final String FILE_NAME = "fileName";
    private boolean inactive;
    public static final String INACTIVE = "inactive";

    public YamlTemplate(String id, int createdAt, String author, int updatedAt, String content, Info info, String sha, String fileName) {
        this.id = id;
        this.createdAt = createdAt;
        this.author = author;
        this.updatedAt = updatedAt;
        this.content = content;
        this.info = info;
        this.sha = sha;
        this.fileName = fileName;
    }

    public YamlTemplate() {
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

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(int updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }
    
    public boolean getInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    public GlobalEnums.YamlTemplateSource getSource() {
        if (source == null) {
            source = GlobalEnums.YamlTemplateSource.AKTO_TEMPLATES;
        }
        return source;
    }

    public void setSource(GlobalEnums.YamlTemplateSource source) {
        this.source = source;
    }
}
