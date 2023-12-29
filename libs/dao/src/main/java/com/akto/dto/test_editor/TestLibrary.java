package com.akto.dto.test_editor;

public class TestLibrary {

    String repositoryUrl;
    public static final String REPOSITORY_URL = "repositoryUrl";
    String author;
    int timestamp;

    public TestLibrary(String repositoryUrl, String author, int timestamp) {
        this.repositoryUrl = repositoryUrl;
        this.author = author;
        this.timestamp = timestamp;
    }

    public TestLibrary() {
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

}
