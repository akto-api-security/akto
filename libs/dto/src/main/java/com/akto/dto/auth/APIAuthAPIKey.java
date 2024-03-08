package com.akto.dto.auth;

import com.akto.dao.context.Context;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class APIAuthAPIKey extends APIAuth {

    public enum Placement {
        HEADER, QUERY, COOKIE
    }

    String authKey;
    Placement placement;
    String keyName;

    public APIAuthAPIKey() {}

    public APIAuthAPIKey(String authKey, Placement placement, String keyName) {
        this.authKey = authKey;
        this.placement = placement;
        this.keyName = keyName;
        this.type = Type.APIKEY;
        this.id = Context.now();
    }

    @Override
    public void setType(Type type) {
        this.type = Type.APIKEY;
    }

    public String getAuthKey() {
        return this.authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public Placement getPlacement() {
        return this.placement;
    }

    public void setPlacement(Placement placement) {
        this.placement = placement;
    }

    public String getKeyName() {
        return this.keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }
}
