package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.util.TokenPayloadModifier;

public class HardcodedAuthParam extends AuthParam {
    private Location where;
    private String key;
    private String value;
    private Boolean showHeader;


    public HardcodedAuthParam() { }

    public HardcodedAuthParam(Location where, String key, String value, Boolean showHeader) {
        this.where = where;
        this.key = key;
        this.value = value;
        this.showHeader = showHeader;
    }

    @Override
    boolean addAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        return TokenPayloadModifier.tokenPayloadModifier(request, this.key, this.value, this.where);
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        return TokenPayloadModifier.tokenPayloadModifier(request, this.key, null, this.where);
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        return Utils.isRequestKeyPresent(this.key, request, where);
    }

    @Override
    public Location getWhere() {
        return where;
    }

    public void setWhere(Location where) {
        this.where = where;
    }

    @Override
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public Boolean getShowHeader() {
        return showHeader;
    }

    public void setShowHeader(Boolean showHeader) {
        this.showHeader = showHeader;
    }
}
