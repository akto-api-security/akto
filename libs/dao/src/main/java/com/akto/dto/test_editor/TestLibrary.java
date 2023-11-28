package com.akto.dto.test_editor;

public class TestLibrary {

    Repository repository;
    String author;
    int timestamp;

    public TestLibrary(Repository repository, String author, int timestamp) {
        this.repository = repository;
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

    public Repository getRepository() {
        return repository;
    }

    public void setRepository(Repository repository) {
        this.repository = repository;
    }

}
