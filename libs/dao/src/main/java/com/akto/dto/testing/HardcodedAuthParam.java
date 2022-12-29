package com.akto.dto.testing;

import com.akto.dto.HttpRequestParams;
import com.akto.dto.OriginalHttpRequest;
import com.akto.util.JsonStringPayloadModifier;

import java.util.*;

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
    public boolean addAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        if (where.toString().equals("BODY")) {
            try {
                String resp = JsonStringPayloadModifier.jsonStringPayloadModifier(request.getBody(), key, value);
                request.setBody(resp);
            } catch(Exception e) {
                System.out.println("error adding auth param to body" + e.getMessage());
                return false;
            }
        }
        else {
            Map<String, List<String>> headers = request.getHeaders();
            String k = this.key.toLowerCase().trim();
            if (!headers.containsKey(k)) return false;
            headers.put(k, Collections.singletonList(this.value));
        }
        return true;
    }

    @Override
    public boolean removeAuthTokens(OriginalHttpRequest request) {
        if (this.key == null) return false;
        if (where.toString().equals("BODY")) {
            try {
                String resp = JsonStringPayloadModifier.jsonStringPayloadModifier(request.getBody(), key, null);
                request.setBody(resp);
            } catch(Exception e) {
                System.out.println("error adding auth param to body" + e.getMessage());
                return false;
            }
        }
        else {
            Map<String, List<String>> headers = request.getHeaders();
            String k = this.key.toLowerCase().trim();
            if (!headers.containsKey(k)) return false;
            headers.put(k, Collections.singletonList(null));
            // implement this in the 2nd class as well
        }
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

    public Boolean getShowHeader() {
        return showHeader;
    }

    public void setShowHeader(Boolean showHeader) {
        this.showHeader = showHeader;
    }
}
