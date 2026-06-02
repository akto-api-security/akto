package com.akto.dto;

import java.util.Map;

public class OAuthState {

    public static final String NONCE = "nonce";
    public static final String EXPIRES_AT = "expiresAt";
    public static final int EXPIRY_SECONDS = 30 * 60;

    private String nonce;
    private Map<String, String> data;
    private int expiresAt;

    public OAuthState() {}

    public OAuthState(String nonce, Map<String, String> data, int expiresAt) {
        this.nonce = nonce;
        this.data = data;
        this.expiresAt = expiresAt;
    }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public Map<String, String> getData() { return data; }
    public void setData(Map<String, String> data) { this.data = data; }

    public int getExpiresAt() { return expiresAt; }
    public void setExpiresAt(int expiresAt) { this.expiresAt = expiresAt; }
}
