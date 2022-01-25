package com.akto.dto.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.traffic.TrafficInfo;

public class URLMethods {

    public enum Method {
        GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE, OTHER
    }
    
    Map<Method, RequestTemplate> methodToRequestTemplate;
    

    public URLMethods() {
    }

    public URLMethods(Map<Method,RequestTemplate> methodToRequestTemplate) {
        this.methodToRequestTemplate = methodToRequestTemplate;
    }

    public Map<Method,RequestTemplate> getMethodToRequestTemplate() {
        return this.methodToRequestTemplate;
    }

    public void setMethodToRequestTemplate(Map<Method,RequestTemplate> methodToRequestTemplate) {
        this.methodToRequestTemplate = methodToRequestTemplate;
    }

    public URLMethods copy() {
        URLMethods ret = new URLMethods(new HashMap<>());
        for(Map.Entry<Method, RequestTemplate> entry: methodToRequestTemplate.entrySet()) {
            ret.methodToRequestTemplate.put(entry.getKey(), entry.getValue().copy());
        }

        return ret;
    }

    @Override
    public String toString() {
        return "{" +
            " methodToRequestTemplate='" + getMethodToRequestTemplate() + "'" +
            "}";
    }

    public List<SingleTypeInfo> getAllTypeInfo() {
        List<SingleTypeInfo> ret = new ArrayList<>();
        
        for(RequestTemplate requestTemplate: methodToRequestTemplate.values()) {
            ret.addAll(requestTemplate.getAllTypeInfo());
        }

        return ret;
    }

    public List<TrafficInfo> removeAllTrafficInfo(int apiCollectionId, String url) {
        List<TrafficInfo> ret = new ArrayList<>();
        for(Map.Entry<Method, RequestTemplate> entry: methodToRequestTemplate.entrySet()) {
            ret.addAll(entry.getValue().removeAllTrafficInfo(apiCollectionId, url, entry.getKey(), -1));
        }
        return ret;
    }
}
