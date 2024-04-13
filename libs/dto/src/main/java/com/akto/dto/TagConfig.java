package com.akto.dto;

import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;

import org.bson.types.ObjectId;

public class TagConfig {
    private ObjectId id;

    public static final String NAME = "name";
    private String name;

    private int creatorId;

    public static final String TIMESTAMP = "timestamp";
    private int timestamp;

    public static final String ACTIVE = "active";
    private boolean active;

    public static final String KEY_CONDITIONS = "keyConditions";
    Conditions keyConditions;


    public TagConfig() {
    }

    public TagConfig(String name, int creatorId, boolean active, Conditions keyConditions) {
        this.name = name;
        this.creatorId = creatorId;
        this.timestamp = Context.now();
        this.active = active;
        this.keyConditions = keyConditions;
    }

    public ObjectId getId() {
        return this.id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCreatorId() {
        return this.creatorId;
    }

    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isActive() {
        return this.active;
    }

    public boolean getActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Conditions getKeyConditions() {
        return this.keyConditions;
    }

    public void setKeyConditions(Conditions keyConditions) {
        this.keyConditions = keyConditions;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", creatorId='" + getCreatorId() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            ", active='" + isActive() + "'" +
            ", keyConditions='" + getKeyConditions() + "'" +
            "}";
    }

}
