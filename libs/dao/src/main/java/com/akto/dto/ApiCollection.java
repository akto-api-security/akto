package com.akto.dto;

import java.util.Set;

import org.bson.codecs.pojo.annotations.BsonId;

public class ApiCollection {
    
    @BsonId
    int id;
    String name;
    int startTs;
    Set<String> urls;
    boolean hostWise;

    public ApiCollection() {
    }

    public ApiCollection(int id, String name, int startTs, Set<String> urls) {
        this.id = id;
        this.name = name;
        this.startTs = startTs;
        this.urls = urls;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getStartTs() {
        return this.startTs;
    }

    public void setStartTs(int startTs) {
        this.startTs = startTs;
    }

    public Set<String> getUrls() {
        return this.urls;
    }

    public void setUrls(Set<String> urls) {
        this.urls = urls;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", startTs='" + getStartTs() + "'" +
            ", urls='" + getUrls() + "'" +
            "}";
    }

    public boolean isHostWise() {
        return hostWise;
    }

    public void setHostWise(boolean hostWise) {
        this.hostWise = hostWise;
    }
}
