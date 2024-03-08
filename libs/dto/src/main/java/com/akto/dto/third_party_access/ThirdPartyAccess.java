package com.akto.dto.third_party_access;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.types.ObjectId;

@BsonDiscriminator
public class ThirdPartyAccess {
    private ObjectId id;
    private int timestamp, owner, status;
    Credential credential;

    public ThirdPartyAccess() {}

    public ThirdPartyAccess(int timestamp, int owner, int status, Credential credential) {
        this.timestamp = timestamp;
        this.owner = owner;
        this.status = status;
        this.credential = credential;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getOwner() {
        return owner;
    }

    public void setOwner(int owner) {
        this.owner = owner;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Credential getCredential() {
        return credential;
    }

    public void setCredential(Credential credential) {
        this.credential = credential;
    }

    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }
}
