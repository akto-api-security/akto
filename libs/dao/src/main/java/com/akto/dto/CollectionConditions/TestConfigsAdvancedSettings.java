package com.akto.dto.CollectionConditions;

import java.util.List;

import org.apache.commons.lang3.builder.EqualsBuilder;

public class TestConfigsAdvancedSettings {
    
    private String operatorType;
    private List<ConditionsType> operationsGroupList;

    public TestConfigsAdvancedSettings(){}

    public TestConfigsAdvancedSettings(String operatorType, List<ConditionsType> operationsGroupList){
        this.operationsGroupList = operationsGroupList;
        this.operatorType = operatorType;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(String operatorType) {
        this.operatorType = operatorType;
    }
    
    public List<ConditionsType> getOperationsGroupList() {
        return operationsGroupList;
    }

    public void setOperationsGroupList(List<ConditionsType> operationsGroupList) {
        this.operationsGroupList = operationsGroupList;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

}
