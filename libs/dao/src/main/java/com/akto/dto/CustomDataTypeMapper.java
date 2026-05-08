package com.akto.dto;

import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.type.SingleTypeInfo;
import org.bson.types.ObjectId;

import java.util.List;

public class CustomDataTypeMapper {

    private String id;
    private String name;
    private boolean sensitiveAlways;
    private List<SingleTypeInfo.Position> sensitivePosition;
    private int creatorId;
    private int timestamp;
    private boolean active;
    Conditions keyConditions;
    Conditions valueConditions;
    Conditions.Operator operator;
    private IgnoreData ignoreData;

    private boolean redacted;

    public CustomDataTypeMapper() { }

    public CustomDataTypeMapper(CustomDataType customDataType) {
        this.id = customDataType.getId().toHexString();
        this.name = customDataType.getName();
        this.sensitiveAlways = customDataType.isSensitiveAlways();
        this.sensitivePosition = customDataType.getSensitivePosition();
        this.creatorId = customDataType.getCreatorId();
        this.timestamp = customDataType.getTimestamp();
        this.active = customDataType.isActive();
        this.keyConditions = customDataType.getKeyConditions();
        this.valueConditions = customDataType.getValueConditions();
        this.operator = customDataType.getOperator();
        this.ignoreData = customDataType.getIgnoreData();
        this.redacted = customDataType.isRedacted();
    }

    public static CustomDataType buildCustomDataType(CustomDataTypeMapper customDataTypeMapper, String id, Conditions keyConditions, Conditions valueConditions) {
        CustomDataType customDataType = new CustomDataType();
        customDataType.setId(new ObjectId(id));
        customDataType.setName(customDataTypeMapper.getName());
        customDataType.setSensitiveAlways(customDataTypeMapper.isSensitiveAlways());
        customDataType.setSensitivePosition(customDataTypeMapper.getSensitivePosition());
        customDataType.setCreatorId(customDataTypeMapper.getCreatorId());
        customDataType.setTimestamp(customDataTypeMapper.getTimestamp());
        customDataType.setActive(customDataTypeMapper.isActive());
        customDataType.setKeyConditions(keyConditions);
        customDataType.setValueConditions(valueConditions);
        customDataType.setOperator(customDataTypeMapper.getOperator());
        customDataType.setIgnoreData(customDataTypeMapper.getIgnoreData());
        customDataType.setRedacted(customDataTypeMapper.getRedacted());
        return customDataType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public void setSensitiveAlways(boolean sensitiveAlways) {
        this.sensitiveAlways = sensitiveAlways;
    }

    public List<SingleTypeInfo.Position> getSensitivePosition() {
        return sensitivePosition;
    }

    public void setSensitivePosition(List<SingleTypeInfo.Position> sensitivePosition) {
        this.sensitivePosition = sensitivePosition;
    }

    public int getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(int creatorId) {
        this.creatorId = creatorId;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Conditions getKeyConditions() {
        return keyConditions;
    }

    public void setKeyConditions(Conditions keyConditions) {
        this.keyConditions = keyConditions;
    }

    public Conditions getValueConditions() {
        return valueConditions;
    }

    public void setValueConditions(Conditions valueConditions) {
        this.valueConditions = valueConditions;
    }

    public Conditions.Operator getOperator() {
        return operator;
    }

    public void setOperator(Conditions.Operator operator) {
        this.operator = operator;
    }

    public IgnoreData getIgnoreData() {
        return ignoreData;
    }

    public void setIgnoreData(IgnoreData ignoreData) {
        this.ignoreData = ignoreData;
    }

    public Boolean getRedacted(){
        return this.redacted;
    }

    public void setRedacted(Boolean isRedacted){
        this.redacted = isRedacted;
    }
}
