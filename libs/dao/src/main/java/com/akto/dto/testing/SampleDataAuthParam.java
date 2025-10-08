package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;
import com.akto.util.TokenPayloadModifier;

public class SampleDataAuthParam extends AuthParam {


    public SampleDataAuthParam() {
    }
    public SampleDataAuthParam(Location where, String key, String value, Boolean showHeader) {
        this.where = where;
        this.key = key;
        this.value = value;
        this.showHeader = showHeader;
    }

    private Location where;
    private String key;
    private String value;
    private Boolean showHeader;

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


    public Location getWhere() {
        return this.where;
    }

    public void setWhere(Location where) {
        this.where = where;
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean isShowHeader() {
        return this.showHeader;
    }

    public Boolean getShowHeader() {
        return this.showHeader;
    }

    public void setShowHeader(Boolean showHeader) {
        this.showHeader = showHeader;
    }


    @Override
    public String toString() {
        return "{" +
            " where='" + getWhere() + "'" +
            ", key='" + getKey() + "'" +
            ", value='" + getValue() + "'" +
            ", showHeader='" + isShowHeader() + "'" +
            "}";
    }


}
