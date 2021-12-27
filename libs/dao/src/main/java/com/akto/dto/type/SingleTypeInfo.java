package com.akto.dto.type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


import org.apache.commons.lang3.StringUtils;

public class SingleTypeInfo {
    public enum SuperType {
        BOOLEAN, INTEGER, FLOAT, STRING, NULL, OTHER
    }

    public enum SubType {
        TRUE(SuperType.BOOLEAN, false), 
        FALSE(SuperType.BOOLEAN, false), 
        INTEGER_32(SuperType.INTEGER, false), 
        INTEGER_64(SuperType.INTEGER, false), 
        FLOAT(SuperType.FLOAT, false), 
        NULL(SuperType.NULL, false), 
        OTHER(SuperType.OTHER, false),
        EMAIL(SuperType.STRING, true), 
        URL(SuperType.STRING, false), 
        ADDRESS(SuperType.STRING, true), 
        SSN(SuperType.STRING, true), 
        CREDIT_CARD(SuperType.STRING, true), 
        PHONE_NUMBER(SuperType.STRING, true), 
        UUID(SuperType.STRING, false), 
        GENERIC(SuperType.STRING, false),
        DICT(SuperType.OTHER, false);

        SuperType superType;
        boolean isSensitive;

        private SubType(SuperType superType, boolean isSensitive) {
            this.superType = superType;
            this.isSensitive = isSensitive;
        }

        public static List<String> getSensitiveTypes() {
            List<String> ret = new ArrayList<>();
            for (SubType subType: SubType.values()) {
                if (subType.isSensitive) {
                    ret.add(subType.name());
                }
            }
            return ret;
        }
    }

    public static class ParamId {
        String url;
        String method;
        int responseCode;
        boolean isHeader;
        String param;
        SubType subType;
        int apiCollectionId;

        public ParamId(String url, String method, int responseCode, boolean isHeader, String param, SubType subType, int apiCollectionId) {
            this.url = url;
            this.method = method;
            this.responseCode = responseCode;
            this.isHeader = isHeader;
            this.param = param;
            this.subType = subType;  
            this.apiCollectionId = apiCollectionId; 
        }

        public ParamId() {
        }
    }

    String url;
    String method;
    int responseCode;
    boolean isHeader;
    String param;
    SubType subType;
    Set<Object> examples = new HashSet<>();
    Set<String> userIds = new HashSet<>();
    int count;
    int timestamp;
    int duration;
    int apiCollectionId;

    public SingleTypeInfo() {
    }

    public SingleTypeInfo(ParamId paramId, Set<Object> examples, Set<String> userIds, int count, int timestamp, int duration) {
        this.url = paramId.url;
        this.method = paramId.method;
        this.responseCode = paramId.responseCode;
        this.isHeader = paramId.isHeader;
        this.param = paramId.param;
        this.subType = paramId.subType;    
        this.apiCollectionId = paramId.apiCollectionId;
        this.examples = examples;
        this.userIds = userIds;
        this.count = count;
        this.timestamp = timestamp;
        this.duration = duration;
        
    }

    public String composeKey() {
        return StringUtils.joinWith("@", url, method, responseCode, isHeader, param, subType, apiCollectionId);
    }

    public void incr(Object object) {
        this.count++;
    }
    
    public SingleTypeInfo copy() {
        Set<Object> copyExamples = new HashSet<>();
        copyExamples.addAll(this.examples);

        Set<String> copyUserIds = new HashSet<>();
        copyUserIds.addAll(this.userIds);

        ParamId paramId = new ParamId();
        paramId.url = url;
        paramId.method = method;
        paramId.responseCode = responseCode;
        paramId.isHeader = isHeader;
        paramId.param = param;
        paramId.subType = subType;
        paramId.apiCollectionId = apiCollectionId;

        return new SingleTypeInfo(paramId, copyExamples, copyUserIds, this.count, this.timestamp, this.duration);
    }

    public String getUrl() {
        return this.url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return this.method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public int getResponseCode() {
        return this.responseCode;
    }

    public void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    public boolean isIsHeader() {
        return this.isHeader;
    }

    public boolean getIsHeader() {
        return this.isHeader;
    }

    public void setIsHeader(boolean isHeader) {
        this.isHeader = isHeader;
    }

    public String getParam() {
        return this.param;
    }

    public void setParam(String param) {
        this.param = param;
    }

    public SubType getSubType() {
        return this.subType;
    }

    public void setSubType(SubType subType) {
        this.subType = subType;
    }

    public Set<Object> getExamples() {
        return this.examples;
    }

    public void setExamples(Set<Object> examples) {
        this.examples = examples;
    }

    public Set<String> getUserIds() {
        return this.userIds;
    }

    public void setUserIds(Set<String> userIds) {
        this.userIds = userIds;
    }

    public int getCount() {
        return this.count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public int getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public int getDuration() {
        return this.duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }
    
    public int getApiCollectionId() {
        return this.apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof SingleTypeInfo)) {
            return false;
        }
        SingleTypeInfo singleTypeInfo = (SingleTypeInfo) o;
        return 
            url.equals(singleTypeInfo.url) && 
            method.equals(singleTypeInfo.method) && 
            responseCode == singleTypeInfo.responseCode && 
            isHeader == singleTypeInfo.isHeader && 
            param.equals(singleTypeInfo.param) && 
            subType == singleTypeInfo.subType && 
            apiCollectionId == singleTypeInfo.apiCollectionId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, responseCode, isHeader, param, subType, apiCollectionId);
    }

    @Override
    public String toString() {
        return "{" +
            " url='" + getUrl() + "'" +
            ", method='" + getMethod() + "'" +
            ", responseCode='" + getResponseCode() + "'" +
            ", isHeader='" + isIsHeader() + "'" +
            ", param='" + getParam() + "'" +
            ", subType='" + getSubType() + "'" +
            ", apiCollectionId='" + getApiCollectionId() + "'" +
            ", examples='" + getExamples() + "'" +
            ", userIds='" + getUserIds() + "'" +
            ", count='" + getCount() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            ", duration='" + getDuration() + "'" +
            "}";
    }
}
