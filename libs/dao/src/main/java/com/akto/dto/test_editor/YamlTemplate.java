package com.akto.dto.test_editor;

public class YamlTemplate {

    private String id;
    private int createdAt;
    public static final String CREATED_AT = "createdAt";
    private String author;
    public static final String AUTHOR = "author";
    private int updatedAt;
    public static final String UPDATED_AT = "updatedAt";
    private String content;
    public static final String CONTENT = "content";
    private Info info;
    public static final String INFO = "info";
    private String sha;
    public static final String SHA = "sha";


    public YamlTemplate(String id, int createdAt, String author, int updatedAt, String content, Info info, String sha) {
        this.id = id;
        this.createdAt = createdAt;
        this.author = author;
        this.updatedAt = updatedAt;
        this.content = content;
        this.info = info;
        this.sha = sha;
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
}
