package com.akto.dto;

import java.util.List;

import org.bson.types.ObjectId;

import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Conditions.Operator;

public class CustomAuthType {
    private ObjectId id;
    private String name;
    private List<String> keys;
    private Conditions.Operator operator;
    private boolean active;
    private int creatorId;
    private int timestamp;    // timestamp
    // createrId
    public CustomAuthType() {
    }
    public CustomAuthType(String name, List<String> keys, Operator operator, boolean active, int createrId) {
        this.name = name;
        this.keys = keys;
        this.operator = operator;
        this.active = active;
        this.creatorId = createrId;
        this.timestamp = Context.now();
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public List<String> getKeys() {
        return keys;
    }
    public void setKeys(List<String> keys) {
        this.keys = keys;
    }
    public Conditions.Operator getOperator() {
        return operator;
    }
    public void setOperator(Conditions.Operator operator) {
        this.operator = operator;
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
