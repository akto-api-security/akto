package com.akto.dto.auth;

import com.akto.dao.context.Context;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class APIAuthOAuth extends APIAuth {

    String oauthKey;

    public APIAuthOAuth() {}

    public APIAuthOAuth(String oauthKey) {
        this.oauthKey = oauthKey;
        this.type = Type.OAUTH2;
        this.id = Context.now();
    }

    public String getOauthKey() {
        return this.oauthKey;
    }

    public void setOauthKey(String oauthKey) {
        this.oauthKey = oauthKey;
    }

    @Override
    public void setType(Type type) {
        this.type = Type.OAUTH2;
    }
    
}
