package com.akto.dto;

import java.util.List;

import org.bson.types.ObjectId;

import com.akto.dao.context.Context;

public class CustomAuthType {
    private ObjectId id;
    public static final String NAME = "name";
    private String name;
    private List<String> headerKeys;
    private List<String> payloadKeys;
    public static final String ACTIVE = "active";
    private boolean active;
    private int creatorId;
    private int timestamp;
    
    public CustomAuthType() {
    }
    public CustomAuthType(String name, List<String> headerKeys, List<String> payloadKeys, boolean active, int creatorId) {
        this.name = name;
        this.headerKeys = headerKeys;
        this.payloadKeys = payloadKeys;
        this.active = active;
        this.creatorId = creatorId;
        this.timestamp = Context.now();
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public List<String> getHeaderKeys() {
        return headerKeys;
    }
    public void setHeaderKeys(List<String> headerKeys) {
        this.headerKeys = headerKeys;
    }
    public List<String> getPayloadKeys() {
        return payloadKeys;
    }
    public void setPayloadKeys(List<String> payloadKeys) {
        this.payloadKeys = payloadKeys;
    }
    public boolean isActive() {
        return active;
    }
    public boolean getActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
    public String generateName(){
        return String.join("_",this.name);
    }
    public int getCreatorId() {
        return creatorId;
    }
    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
    }
    public int getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
    public ObjectId getId() {
        return id;
    }
    public void setId(ObjectId id) {
        this.id = id;
    }
}
