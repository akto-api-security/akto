package com.akto.dto;

import java.util.HashSet;
import java.util.Set;

public class DefaultResponse {
    
    private String host;
    private HashSet<String> defaultPayloads;
    public DefaultResponse(String host, Set<String> defaultPayloads) {
        this.host = host;
        this.defaultPayloads = (HashSet<String>) defaultPayloads;
    }
    public DefaultResponse() {
    }
    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public Set<String> getDefaultPayloads() {
        return defaultPayloads;
    }
    public void setDefaultPayloads(Set<String> defaultPayloads) {
        this.defaultPayloads = (HashSet<String>) defaultPayloads;
    }
}
