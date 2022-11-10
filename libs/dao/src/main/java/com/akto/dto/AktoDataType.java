package com.akto.dto;

import java.util.List;

import com.akto.dto.type.SingleTypeInfo;

public class AktoDataType {
    private String name;
    private boolean sensitiveAlways;
    private List<SingleTypeInfo.Position> sensitivePosition;
    private int timestamp;
    public AktoDataType() {
    }
    public AktoDataType(String name, boolean sensitiveAlways, List<SingleTypeInfo.Position> sensitivePosition,int timestamp) {
        this.name = name;
        this.sensitiveAlways = sensitiveAlways;
        this.sensitivePosition = sensitivePosition;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public boolean isSensitiveAlways() {
        return sensitiveAlways;
    }
    public boolean getSensitiveAlways() {
        return sensitiveAlways;
    }
    public void setSensitiveAlways(boolean sensitiveAlways) {
        this.sensitiveAlways = sensitiveAlways;
    }
    public List<SingleTypeInfo.Position> getSensitivePosition() {
        return sensitivePosition;
    }
    public void setSensitivePosition(List<SingleTypeInfo.Position> sensitivePosition) {
        this.sensitivePosition = sensitivePosition;
    }
    public int getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }
}
