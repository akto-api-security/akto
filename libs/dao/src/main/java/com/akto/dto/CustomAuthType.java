package com.akto.dto;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import com.akto.dao.context.Context;

public class CustomAuthType {

    public enum TypeOfToken {
        AUTH, CSRF, SESSION
    }

    private ObjectId id;
    @BsonIgnore
    private String hexId;
    public static final String NAME = "name";
    private String name;
    private List<String> headerKeys;
    private List<String> payloadKeys;
    public static final String ACTIVE = "active";
    private boolean active;
    private int creatorId;
    private int timestamp;
    private List<Integer> apiCollectionIds;
    public static final String API_COLLECTION_IDS = "apiCollectionIds";


    public List<TypeOfToken> typeOfTokens;
    public static final String TYPE_OF_TOKENS = "typeOfTokens";

    public CustomAuthType() {
    }
    public CustomAuthType(String name, List<String> headerKeys, List<String> payloadKeys, boolean active, int creatorId, List<Integer> apiCollectionIds, List<TypeOfToken> typeOfTokens) {
        this.name = name;
        this.headerKeys = headerKeys;
        this.payloadKeys = payloadKeys;
        this.active = active;
        this.creatorId = creatorId;
        this.timestamp = Context.now();
        this.apiCollectionIds = apiCollectionIds;
        this.typeOfTokens = typeOfTokens;
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
    public String getHexId() {
        return this.id.toHexString();
    }
    public void setHexId(String hexId) {
        this.hexId = hexId;
    }
    public List<Integer> getApiCollectionIds() {
        return apiCollectionIds;
    }
    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }
    public List<TypeOfToken> getTypeOfTokens() {
        return typeOfTokens;
    }

    public void setTypeOfTokens(List<TypeOfToken> typeOfTokens) {
        this.typeOfTokens = typeOfTokens;
    }

}
