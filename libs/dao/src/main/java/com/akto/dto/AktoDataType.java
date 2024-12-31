package com.akto.dto;

import java.util.List;

import com.akto.dto.data_types.Conditions;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.util.enums.GlobalEnums.Severity;

public class AktoDataType {
    private String name;

    public static final String NAME = "name";
    private boolean sensitiveAlways;
    private List<SingleTypeInfo.Position> sensitivePosition;
    private int timestamp;
    private IgnoreData ignoreData;
    private boolean redacted;
    public static final String SAMPLE_DATA_FIXED = "sampleDataFixed";
    private boolean sampleDataFixed;

    public static final String CATEGORIES_LIST = "categoriesList";
    public static final String TAGS_LIST = "tagsLists";
    private List<String> categoriesList;

    public static final String DATA_TYPE_PRIORITY = "dataTypePriority";
    private Severity dataTypePriority;

    public static final String KEY_CONDITIONS = "keyConditions";
    Conditions keyConditions;
    public static final String VALUE_CONDITIONS = "valueConditions";
    Conditions valueConditions;
    public static final String OPERATOR = "operator";
    Conditions.Operator operator;

    public static final String _INACTIVE = "inactive";
    private boolean inactive;

    public AktoDataType() {
    }
    public AktoDataType(String name, boolean sensitiveAlways, List<SingleTypeInfo.Position> sensitivePosition,int timestamp, IgnoreData ignoreData, boolean redacted, boolean sampleDataFixed) {
        this.name = name;
        this.sensitiveAlways = sensitiveAlways;
        this.sensitivePosition = sensitivePosition;
        this.ignoreData = ignoreData;
        this.redacted = redacted;
        this.sampleDataFixed = sampleDataFixed;
    }

    public AktoDataType(String name, boolean sensitiveAlways, List<SingleTypeInfo.Position> sensitivePosition,int timestamp, IgnoreData ignoreData, boolean redacted, boolean sampleDataFixed, List<String> categoriesList, Severity severity) {
        this.name = name;
        this.sensitiveAlways = sensitiveAlways;
        this.sensitivePosition = sensitivePosition;
        this.ignoreData = ignoreData;
        this.redacted = redacted;
        this.sampleDataFixed = sampleDataFixed;
        this.categoriesList = categoriesList;
        this.dataTypePriority = severity;
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
    public IgnoreData getIgnoreData() {
        return ignoreData;
    }
    public void setIgnoreData(IgnoreData ignoreData) {
        this.ignoreData = ignoreData;
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

    public boolean validate(Object value, Object key) {
        try {
            return this.validateRaw(value, key);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateRaw(Object value, Object key) throws Exception {
        return CustomDataType.validateRawUtility(value, key, this.keyConditions, this.valueConditions, this.operator);
    }

    /*
     * Default is inactive:false
     */
    public boolean getInactive() {
        return inactive;
    }

    public void setInactive(boolean inactive) {
        this.inactive = inactive;
    }

    // To send a field in frontend.
    public boolean getActive() {
        return !inactive;
    }

}
