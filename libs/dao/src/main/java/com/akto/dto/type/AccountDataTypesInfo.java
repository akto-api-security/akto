package com.akto.dto.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.akto.dto.CustomDataType;

public class AccountDataTypesInfo {

    private Map<String, CustomDataType> customDataTypeMap = new HashMap<>();
    private List<CustomDataType> customDataTypesSortedBySensitivity = new ArrayList<>();

    public AccountDataTypesInfo() {
        this.customDataTypeMap = new HashMap<>();
        this.customDataTypesSortedBySensitivity = new ArrayList<>();
    }

    public AccountDataTypesInfo(Map<String,CustomDataType> customDataTypeMap, List<CustomDataType> customDataTypesSortedBySensitivity) {
        this.customDataTypeMap = customDataTypeMap;
        this.customDataTypesSortedBySensitivity = customDataTypesSortedBySensitivity;
    }

    public Map<String,CustomDataType> getCustomDataTypeMap() {
        return this.customDataTypeMap;
    }

    public void setCustomDataTypeMap(Map<String,CustomDataType> customDataTypeMap) {
        this.customDataTypeMap = customDataTypeMap;
    }

    public List<CustomDataType> getCustomDataTypesSortedBySensitivity() {
        return this.customDataTypesSortedBySensitivity;
    }

    public void setCustomDataTypesSortedBySensitivity(List<CustomDataType> customDataTypesSortedBySensitivity) {
        this.customDataTypesSortedBySensitivity = customDataTypesSortedBySensitivity;
    }

    @Override
    public String toString() {
        return "{" +
                " customDataTypeMap='" + getCustomDataTypeMap() + "'" +
                ", customDataTypesSortedBySensitivity='" + getCustomDataTypesSortedBySensitivity() + "'" +
                "}";
    }
}
