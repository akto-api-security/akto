package com.akto.dto.testing;

import com.akto.dto.OriginalHttpRequest;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LoginRequestAuthParam extends AuthParam {

    private Location where;

    private String key;
    private String value;
    public LoginRequestAuthParam() { }

    public LoginRequestAuthParam(Location where, String key, String value) {
        this.key = key;
        this.value = value;
        this.where = where;
    }

    @Override
    public boolean addAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        Map<String, List<String>> headers = request.getHeaders();
        String k = this.key.toLowerCase().trim();
        if (!headers.containsKey(k)) return false;
        headers.put(k, Collections.singletonList(this.value));
        return true;
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        Map<String, List<String>> headers = request.getHeaders();
        String k = this.key.toLowerCase().trim();
        if (!headers.containsKey(k)) return false;
        headers.put(k, Collections.singletonList(null));
        // implement this in the 2nd class as well
        return true;
    }

    @Override
    public boolean authTokenPresent(OriginalHttpRequest request) {
        if (this.key == null) return false;
        String k = this.key.toLowerCase().trim();
        Map<String, List<String>> headers = request.getHeaders();
        return headers.containsKey(k);
    }

    public Location getWhere() {
        return where;
    }

    public void setWhere(Location where) {
        this.where = where;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
