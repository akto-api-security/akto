package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonId;

public class ApiCollection {
    
    @BsonId
    int id;

    String name;

    int startTs;

    public ApiCollection() {
    }

    public ApiCollection(int id, String name, int startTs) {
        this.id = id;
        this.name = name;
        this.startTs = startTs;
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

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", startTs='" + getStartTs() + "'" +
            "}";
    }
}
