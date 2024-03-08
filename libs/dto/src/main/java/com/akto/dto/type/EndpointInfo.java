package com.akto.dto.type;

import java.util.Map;

public class EndpointInfo {
    Map<String, Map<String, RequestTemplate>> allEndpoints;

    public EndpointInfo() {
    }

    public EndpointInfo(Map<String,Map<String,RequestTemplate>> allEndpoints) {
        this.allEndpoints = allEndpoints;
    }

    public Map<String,Map<String,RequestTemplate>> getAllEndpoints() {
        return this.allEndpoints;
    }

    public void setAllEndpoints(Map<String,Map<String,RequestTemplate>> allEndpoints) {
        this.allEndpoints = allEndpoints;
    }

    @Override
    public String toString() {
        return "{" +
            " allEndpoints='" + getAllEndpoints() + "'" +
            "}";
    }
}
