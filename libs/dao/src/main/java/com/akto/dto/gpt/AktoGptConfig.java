package com.akto.dto.gpt;

import org.bson.codecs.pojo.annotations.BsonId;

public class AktoGptConfig {

    @BsonId
    private int id;


    private AktoGptConfigState state;

    public AktoGptConfig() {
    }

    public AktoGptConfig(int id, AktoGptConfigState state) {
        this.id = id;
        this.state = state;
    }

    public int getId() {
        return id;
    }

    public void setId(int collectionId) {
        this.id = collectionId;
    }

    public AktoGptConfigState getState() {
        return state;
    }

    public void setState(AktoGptConfigState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "AktoGptConfig [collectionId=" + id + ", state=" + state + "]";
    }
}

