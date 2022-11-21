package com.akto.dto;

import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.type.SingleTypeInfo;
import io.swagger.v3.oas.models.media.StringSchema;
import org.bson.types.ObjectId;

import java.util.List;
import java.util.Objects;

public class CustomDataType {
    private ObjectId id;
    public static final String NAME = "name";
    private String name;
    public static final String SENSITIVE_ALWAYS = "sensitiveAlways";
    private boolean sensitiveAlways;
    public static final String SENSITIVE_POSITION = "sensitivePosition";
    private List<SingleTypeInfo.Position> sensitivePosition;
    private int creatorId;
    public static final String TIMESTAMP = "timestamp";
    private int timestamp;
    public static final String ACTIVE = "active";
    private boolean active;

    public static final String KEY_CONDITIONS = "keyConditions";
    Conditions keyConditions;
    public static final String VALUE_CONDITIONS = "valueConditions";
    Conditions valueConditions;
    public static final String OPERATOR = "operator";
    Conditions.Operator operator;

    public CustomDataType() { }

    public CustomDataType(String name, boolean sensitiveAlways, List<SingleTypeInfo.Position> sensitivePosition, int creatorId, boolean active, Conditions keyConditions, Conditions valueConditions, Conditions.Operator operator) {
        this.name = name;
        this.sensitiveAlways = sensitiveAlways;
        this.sensitivePosition = sensitivePosition;
        this.creatorId = creatorId;
        this.timestamp = Context.now();
        this.active = active;
        this.keyConditions = keyConditions;
        this.valueConditions = valueConditions;
        this.operator = operator;
    }

    public SingleTypeInfo.SubType toSubType() {
        return new SingleTypeInfo.SubType(
                this.name,this.sensitiveAlways, SingleTypeInfo.SuperType.CUSTOM,
                StringSchema.class, this.sensitivePosition
        );
    }

    public boolean validate(Object value, Object key) {
        if (this.keyConditions == null && this.valueConditions==null) return false;
        boolean keyResult = true;
        if (this.keyConditions != null) {
            keyResult = this.keyConditions.validate(key);
        }

        boolean valueResult = true;
        if (this.valueConditions != null) {
            valueResult = this.valueConditions.validate(value);
        }

        if (this.valueConditions ==null || this.keyConditions == null) {
            return keyResult && valueResult;
        } else {
            switch (this.operator) {
                case AND:
                    return keyResult && valueResult;
                case OR:
                    return keyResult || valueResult;
                default:
                    // TODO:
                    return false;
            }
        }

    }


    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
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
}
