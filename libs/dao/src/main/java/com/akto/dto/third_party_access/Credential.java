package com.akto.dto.third_party_access;

import com.akto.dao.context.Context;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public abstract class Credential {
    public enum Type {
        GOOGLE, MYSQL, SALESFORCE, POSTGRESQL, POSTMAN
    }

    private Type type;
    private int lastUpdatedTs;
    private long expiryDuration;
    private String name;

    public Credential() {}

    public Credential(Type type, long expiryDuration, String name) {
        this.type = type;
        this.lastUpdatedTs = Context.now();
        this.expiryDuration = expiryDuration;
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public int getLastUpdatedTs() {
        return lastUpdatedTs;
    }

    public void setLastUpdatedTs(int lastUpdatedTs) {
        this.lastUpdatedTs = lastUpdatedTs;
    }

    public long getExpiryDuration() {
        return expiryDuration;
    }

    public void setExpiryDuration(long expiryDuration) {
        this.expiryDuration = expiryDuration;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
