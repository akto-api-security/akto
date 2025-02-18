package com.akto.dto.dependency_flow;

import java.util.Objects;

public class KVPair {
    private String key;
    private boolean isHeader;
    private boolean isUrlParam;

    private String value;
    private KVType type;

    public enum KVType {
        STRING, INTEGER, JSON, BOOLEAN
    }

    public KVPair() {
    }

    public KVPair(String key, String value, boolean isHeader, boolean isUrlParam, KVType type) {
        this.key = key;
        this.value = value;
        this.isHeader = isHeader;
        this.isUrlParam = isUrlParam;
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KVPair kvPair = (KVPair) o;
        return isHeader == kvPair.isHeader && isUrlParam == kvPair.isUrlParam && key.equals(kvPair.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, isHeader, isUrlParam);
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }
    public boolean isHeader() {
        return isHeader;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public KVType getType() {
        return type;
    }

    public void setType(KVType type) {
        this.type = type;
    }

    public void setHeader(boolean header) {
        isHeader = header;
    }

    public boolean isUrlParam() {
        return isUrlParam;
    }

    public void setUrlParam(boolean urlParam) {
        isUrlParam = urlParam;
    }
}
