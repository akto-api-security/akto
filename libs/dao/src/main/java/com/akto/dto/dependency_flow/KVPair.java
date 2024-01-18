package com.akto.dto.dependency_flow;

import java.util.Objects;

public class KVPair {
    private String key;
    private Object value;
    private boolean isHeader;

    private boolean isUrlParam;

    public KVPair() {
    }

    public KVPair(String key, Object value, boolean isHeader, boolean isUrlParam) {
        this.key = key;
        this.value = value;
        this.isHeader = isHeader;
        this.isUrlParam = isUrlParam;
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

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public boolean isHeader() {
        return isHeader;
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
