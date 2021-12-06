package com.akto.dto.third_party_access;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class GoogleCredential extends Credential {
    private String accessToken, refreshToken;

    public GoogleCredential() {
        super();
    }

    public GoogleCredential(
        String accessToken,
        String refreshToken,
        long expiryDuration,
        String name
    ) {
        super(Type.GOOGLE, expiryDuration, name);
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
