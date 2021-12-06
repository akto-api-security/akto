package com.akto.dto.third_party_access;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class ThirdPartyAccess {
    private int timestamp, owner, id, status;
    Credential credential;

    public ThirdPartyAccess() {}

    public ThirdPartyAccess(int timestamp, int owner, int id, int status, Credential credential) {
        this.timestamp = timestamp;
        this.owner = owner;
        this.id = id;
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

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
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
}
