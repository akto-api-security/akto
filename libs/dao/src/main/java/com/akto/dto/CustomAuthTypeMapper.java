package com.akto.dto;

import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;
import org.bson.types.ObjectId;

import java.util.List;

public class CustomAuthTypeMapper {

    private String id;
    private String name;
    private List<String> headerKeys;
    private List<String> payloadKeys;
    public static final String ACTIVE = "active";
    private boolean active;
    private int creatorId;
    private int timestamp;

    public CustomAuthTypeMapper(CustomAuthType customAuthType) {
        this.id = customAuthType.getId().toHexString();
        this.name = customAuthType.getName();
        this.headerKeys = customAuthType.getHeaderKeys();
        this.payloadKeys = customAuthType.getPayloadKeys();
        this.active = customAuthType.getActive();
        this.creatorId = customAuthType.getCreatorId();
        this.timestamp = customAuthType.getTimestamp();
    }

    public static CustomAuthType buildCustomAuthType(CustomAuthTypeMapper customAuthTypeMapper, String id) {
        CustomAuthType customAuthType = new CustomAuthType();
        customAuthType.setId(new ObjectId(id));
        customAuthType.setName(customAuthTypeMapper.getName());
        customAuthType.setHeaderKeys(customAuthTypeMapper.getHeaderKeys());
        customAuthType.setPayloadKeys(customAuthTypeMapper.getPayloadKeys());
        customAuthType.setActive(customAuthTypeMapper.getActive());
        customAuthType.setCreatorId(customAuthTypeMapper.getCreatorId());
        customAuthType.setTimestamp(customAuthTypeMapper.getTimestamp());
        return customAuthType;
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
    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

}
