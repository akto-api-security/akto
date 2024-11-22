package com.akto.threat.protection.db;

import com.akto.dto.type.URLMethods.Method;

import java.util.UUID;

public class MaliciousEventModel {

    private String id;
    private String filterId;
    private String actor;
    private String ip;
    private String url;
    private Method method;
    private String data;
    private int binId;
    private int expiry;

    public MaliciousEventModel() {
    }

    public MaliciousEventModel(
            String filterId,
            String actor,
            String ip,
            String url,
            String method,
            String data,
            long requestTime) {
        int now = (int) (System.currentTimeMillis() / 1000L);
        this.id = UUID.randomUUID().toString();
        this.ip = ip;
        this.filterId = filterId;
        this.actor = actor;
        this.data = data;
        this.binId = (int) requestTime / 60;
        this.url = url;
        this.method = Method.fromString(method);

        // For now, we are hardcoding it to 3 hrs.
        // But later we will read it through FilterConfig
        this.expiry = now + (3 * 60 * 60);
    }

    public String getId() {
        return id;
    }

    public String getFilterId() {
        return filterId;
    }

    public String getActor() {
        return actor;
    }

    public String getData() {
        return data;
    }

    public int getBinId() {
        return binId;
    }

    public int getExpiry() {
        return expiry;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setFilterId(String filterId) {
        this.filterId = filterId;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void setBinId(int binId) {
        this.binId = binId;
    }

    public void setExpiry(int expiry) {
        this.expiry = expiry;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
