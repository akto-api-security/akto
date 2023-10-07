package com.akto.github;

public class GithubFile {
    private String name;
    private String path;
    private String content;
    private String sha;

    public GithubFile(String name, String path, String content, String sha) {
        this.name = name;
        this.path = path;
        this.content = content;
        this.sha = sha;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }
}
