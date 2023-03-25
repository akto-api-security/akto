package com.akto.dto;

import java.util.Set;

public class FetchApiCollectionResponse {
    
    private int id;
    private String name;
    private String displayName;
    private int startTs;
    private Set<String> urls;
    private String hostName;
    private int vxlanId;
    private Boolean isLogicalGroup;
    private int urlsCount;
    private String createdBy;

    public FetchApiCollectionResponse() {

    }

    public FetchApiCollectionResponse(int id, String name, String displayName, int startTs, Set<String> urls, String hostName, int vxlanId, Boolean isLogicalGroup, int urlsCount, String createdBy) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.startTs = startTs;
        this.urls = urls;
        this.hostName = hostName;
        this.vxlanId = vxlanId;
        this.isLogicalGroup = isLogicalGroup;
        this.urlsCount = urlsCount;
        this.createdBy = createdBy;
    }

    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public int getStartTs() {
        return this.startTs;
    }

    public void setStartTs(int startTs) {
        this.startTs = startTs;
    }

    public Set<String> getUrls() {
        return this.urls;
    }

    public void setUrls(Set<String> urls) {
        this.urls = urls;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getVxlanId() {
        return vxlanId;
    }

    public void setVxlanId(int vxlanId) {
        this.vxlanId = vxlanId;
    }

    public Boolean getIsLogicalGroup() {
        return isLogicalGroup;
    }

    public void setIsLogicalGroup(Boolean isLogicalGroup) {
        this.isLogicalGroup = isLogicalGroup;
    }

    public int getUrlsCount() {
        return urlsCount;
    }

    public void setUrlsCount(int urlsCount) {
        this.urlsCount = urlsCount;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

}
