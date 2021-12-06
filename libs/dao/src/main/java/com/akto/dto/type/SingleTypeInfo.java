package com.akto.dto.type;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


import org.apache.commons.lang3.StringUtils;

public class SingleTypeInfo {
    public enum SuperType {
        BOOLEAN, INTEGER, FLOAT, STRING, NULL, OTHER
    }

    public enum SubType {
        TRUE(SuperType.BOOLEAN), 
        FALSE(SuperType.BOOLEAN), 
        INTEGER_32(SuperType.INTEGER), 
        INTEGER_64(SuperType.INTEGER), 
        FLOAT(SuperType.FLOAT), 
        NULL(SuperType.NULL), 
        OTHER(SuperType.OTHER),
        EMAIL(SuperType.STRING), 
        URL(SuperType.STRING), 
        ADDRESS(SuperType.STRING), 
        SSN(SuperType.STRING), 
        CREDIT_CARD(SuperType.STRING), 
        PHONE_NUMBER(SuperType.STRING), 
        UUID(SuperType.STRING), 
        GENERIC(SuperType.STRING),
        DICT(SuperType.OTHER);

        SuperType superType;

        private SubType(SuperType superType) {
            this.superType = superType;
        }
    }

    public static class ParamId {
        String url;
        String method;
        int responseCode;
        boolean isHeader;
        String param;
        SubType subType;

        public ParamId(String url, String method, int responseCode, boolean isHeader, String param, SubType subType) {
            this.url = url;
            this.method = method;
            this.responseCode = responseCode;
            this.isHeader = isHeader;
            this.param = param;
            this.subType = subType;    
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

    public SingleTypeInfo() {
    }

    public SingleTypeInfo(ParamId paramId, Set<Object> examples, Set<String> userIds, int count, int timestamp, int duration) {
        this.url = paramId.url;
        this.method = paramId.method;
        this.responseCode = paramId.responseCode;
        this.isHeader = paramId.isHeader;
        this.param = paramId.param;
        this.subType = paramId.subType;    
        this.examples = examples;
        this.userIds = userIds;
        this.count = count;
        this.timestamp = timestamp;
        this.duration = duration;
    }

    public String composeKey() {
        return StringUtils.joinWith("@", url, method, responseCode, isHeader, param, subType);
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
            subType == singleTypeInfo.subType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, method, responseCode, isHeader, param, subType);
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
            ", examples='" + getExamples() + "'" +
            ", userIds='" + getUserIds() + "'" +
            ", count='" + getCount() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            ", duration='" + getDuration() + "'" +
            "}";
    }
}
