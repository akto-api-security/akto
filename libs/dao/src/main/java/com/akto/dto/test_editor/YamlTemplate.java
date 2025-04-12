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
    private int hash;
    public static final String HASH = "hash";
    private String content;
    public static final String CONTENT = "content";
    private Info info;
    public static final String INFO = "info";
    private boolean inactive;
    public static final String INACTIVE = "inactive";
    private String repositoryUrl;
    public static final String REPOSITORY_URL = "repositoryUrl";
    public static final String SETTINGS = "attributes";
    private TemplateSettings attributes;

    public YamlTemplate(String id, int createdAt, String author, int updatedAt, String content, Info info, TemplateSettings attributes) {
        this.id = id;
        this.createdAt = createdAt;
        this.author = author;
        this.updatedAt = updatedAt;
        this.content = content;
        this.info = info;
        this.hash = content.hashCode();
        this.attributes = attributes;
    }

    public YamlTemplate() {
    }

    public void setContent(String content) {
        this.hash = content.hashCode();
        this.content = content;
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

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
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
    
    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public int getHash() {
        return hash;
    }

    public void setHash(int hash) {
        this.hash = hash;
    }

    public boolean isInactive() {
        return inactive;
    }

    public TemplateSettings getAttributes() {
        return attributes;
    }

    public void setAttributes(TemplateSettings attributes) {
        this.attributes = attributes;
    }
}
