package com.akto.dto.auth;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public abstract class APIAuth {
    public enum Type {
        BASIC, OAUTH2, APIKEY
    }

    int id;
    Type type;

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    abstract public void setType(Type type);

    public Type getType() {
        return this.type;
    }
}