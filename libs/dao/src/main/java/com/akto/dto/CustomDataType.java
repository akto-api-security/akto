package com.akto.dto;

import com.akto.dao.context.Context;
import com.akto.dto.data_types.Conditions;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.enums.GlobalEnums.Severity;

import io.swagger.v3.oas.models.media.StringSchema;
import org.bson.types.ObjectId;

import java.util.List;

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
    public static final String IGNORE_DATA = "ignoreData";
    private IgnoreData ignoreData;
    private boolean redacted;
    public static final String REDACTED = "redacted";
    private boolean sampleDataFixed;
    public static final String SAMPLE_DATA_FIXED = "sampleDataFixed";

    public static final String SKIP_DATA_TYPE_TEST_TEMPLATE_MAPPING = "skipDataTypeTestTemplateMapping";
    private boolean skipDataTypeTestTemplateMapping;
    private Severity dataTypePriority;
    private List<String> categoriesList;

    public static final String ICON_STRING = "iconString";
    private String iconString;

    public static final String  USER_MODIFIED_TIMESTAMP = "userModifiedTimestamp";
    private int userModifiedTimestamp;

    public CustomDataType() { }

    public CustomDataType(String name, boolean sensitiveAlways, List<SingleTypeInfo.Position> sensitivePosition, int creatorId, boolean active, Conditions keyConditions, Conditions valueConditions, Conditions.Operator operator, IgnoreData ignoreData, boolean redacted, boolean sampleDataFixed) {
        this.name = name;
        this.sensitiveAlways = sensitiveAlways;
        this.sensitivePosition = sensitivePosition;
        this.creatorId = creatorId;
        this.timestamp = Context.now();
        this.active = active;
        this.keyConditions = keyConditions;
        this.valueConditions = valueConditions;
        this.operator = operator;
        this.ignoreData = ignoreData;
        this.redacted = redacted;
        this.sampleDataFixed = sampleDataFixed;
    }

    public SingleTypeInfo.SubType toSubType() {
        return new SingleTypeInfo.SubType(
                this.name,this.sensitiveAlways, SingleTypeInfo.SuperType.CUSTOM,
                StringSchema.class, this.sensitivePosition
        );
    }

    public boolean validate(Object value, Object key) {
        try {
            return this.validateRaw(value, key);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRaw(Object value, Object key) throws Exception {
        return validateRawUtility(value, key, this.keyConditions, this.valueConditions, this.operator);
    }


    public static boolean validateRawUtility(Object value, Object key, Conditions keyConditions, Conditions valueConditions, Conditions.Operator operator) {
        if (keyConditions == null && valueConditions==null) return false;
        boolean keyResult = true;
        if (keyConditions != null) {
            keyResult = keyConditions.validate(key);
        }

        boolean valueResult = true;
        if (valueConditions != null) {
            valueResult = valueConditions.validate(value);
        }

        if (valueConditions ==null || keyConditions == null) {
            return keyResult && valueResult;
        } else {
            switch (operator) {
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

    public IgnoreData getIgnoreData() {
        return ignoreData;
    }

    public void setIgnoreData(IgnoreData ignoreData) {
        this.ignoreData = ignoreData;
    }

    @Override
    public String toString() {
        return "{" +
            " id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", sensitiveAlways='" + isSensitiveAlways() + "'" +
            ", sensitivePosition='" + getSensitivePosition() + "'" +
            ", creatorId='" + getCreatorId() + "'" +
            ", timestamp='" + getTimestamp() + "'" +
            ", active='" + isActive() + "'" +
            ", keyConditions='" + getKeyConditions() + "'" +
            ", valueConditions='" + getValueConditions() + "'" +
            ", operator='" + getOperator() + "'" +
            ", ignoreData='" + getIgnoreData() + "'" +
            ", redacted='" + isRedacted() + "'" +
            ", sampleDataFixed='" + isSampleDataFixed() + "'" +
            "}";
    }

    public boolean isRedacted() {
        return redacted;
    }

    public void setRedacted(boolean redacted) {
        this.redacted = redacted;
    }

    public boolean isSampleDataFixed() {
        return sampleDataFixed;
    }

    public void setSampleDataFixed(boolean sampleDataFixed) {
        this.sampleDataFixed = sampleDataFixed;
    }

    public boolean isSkipDataTypeTestTemplateMapping() {
        return skipDataTypeTestTemplateMapping;
    }

    public void setSkipDataTypeTestTemplateMapping(boolean skipDataTypeTestTemplateMapping) {
        this.skipDataTypeTestTemplateMapping = skipDataTypeTestTemplateMapping;
    }
    public Severity getDataTypePriority() {
        return dataTypePriority;
    }

    public void setDataTypePriority(Severity dataTypePriority) {
        this.dataTypePriority = dataTypePriority;
    }

    public List<String> getCategoriesList() {
        return categoriesList;
    }

    public void setCategoriesList(List<String> categoriesList) {
        this.categoriesList = categoriesList;
    }

    public String getIconString() {
        return iconString;
    }

    public void setIconString(String iconString) {
        this.iconString = iconString;
    }

    public int getUserModifiedTimestamp() {
        return userModifiedTimestamp;
    }

    public void setUserModifiedTimestamp(int userModifiedTimestamp) {
        this.userModifiedTimestamp = userModifiedTimestamp;
    }

    public boolean systemFieldsCompare(Object obj){
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        CustomDataType that = (CustomDataType) obj;

        return sensitiveAlways == that.sensitiveAlways &&
                (keyConditions != null ? keyConditions.equals(that.keyConditions) : that.keyConditions == null) &&
                (valueConditions != null ? valueConditions.equals(that.valueConditions) : that.valueConditions == null)
                &&
                (operator != null ? operator.equals(that.operator) : that.operator == null);
    }    
}
