package com.akto.dto;


import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class Relationship {
    private ApiRelationInfo parent;
    private ApiRelationInfo child;
    private Set<String> userIds = new HashSet<>();
    private Map<String,Integer> countMap;

    public Relationship(ApiRelationInfo parent, ApiRelationInfo child, Set<String> userIds, Map<String, Integer> countMap) {
        this.parent = parent;
        this.child = child;
        this.userIds = userIds;
        this.countMap = countMap;
    }

    public Relationship() {}

    public static class ApiRelationInfo {
        private String url;
        private String method;
        private boolean isHeader;
        private String param;
        private int responseCode;

        public ApiRelationInfo() {
        }

        public ApiRelationInfo(String url, String method, boolean isHeader, String param, int responseCode) {
            this.url = url;
            this.method = method;
            this.isHeader = isHeader;
            this.param = param;
            this.responseCode = responseCode;
        }

        @Override
        public int hashCode() {
            return Objects.hash(url, method, responseCode, isHeader, param);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof ApiRelationInfo)) {
                return false;
            }
            ApiRelationInfo apiRelationInfo = (ApiRelationInfo) o;
            return url.equals(apiRelationInfo.url) &&
                    method.equals(apiRelationInfo.method) &&
                    responseCode ==apiRelationInfo.responseCode &&
                    isHeader ==apiRelationInfo.isHeader &&
                    param.equals(apiRelationInfo.param);
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public boolean isHeader() {
            return isHeader;
        }

        public void setHeader(boolean header) {
            isHeader = header;
        }

        public String getParam() {
            return param;
        }

        public void setParam(String param) {
            this.param = param;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public void setResponseCode(int responseCode) {
            this.responseCode = responseCode;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent,child);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Relationship)) {
            return false;
        }
        Relationship relationship = (Relationship) o;
        return parent.equals(relationship.parent) && child.equals(relationship.child);
    }

    public ApiRelationInfo getParent() {
        return parent;
    }

    public void setParent(ApiRelationInfo parent) {
        this.parent = parent;
    }

    public ApiRelationInfo getChild() {
        return child;
    }

    public void setChild(ApiRelationInfo child) {
        this.child = child;
    }

    public Set<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(Set<String> userIds) {
        this.userIds = userIds;
    }

    public Map<String, Integer> getCountMap() {
        return countMap;
    }

    public void setCountMap(Map<String, Integer> countMap) {
        this.countMap = countMap;
    }
}
