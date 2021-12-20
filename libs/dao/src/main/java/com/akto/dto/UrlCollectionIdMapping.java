package com.akto.dto;

public class UrlCollectionIdMapping {

    String url;
    int collectionId;
    

    public UrlCollectionIdMapping() {
    }

    public UrlCollectionIdMapping(String url, int collectionId) {
        this.url = url;
        this.collectionId = collectionId;
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getCollectionId() {
        return this.collectionId;
    }

    public void setCollectionId(int collectionId) {
        this.collectionId = collectionId;
    }

    @Override
    public String toString() {
        return "{" +
            " url='" + getUrl() + "'" +
            ", collectionId='" + getCollectionId() + "'" +
            "}";
    }
    
}
