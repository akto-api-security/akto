package com.akto.dto.type;

import java.util.*;

import com.akto.dto.AktoDataType;
import com.akto.dto.CustomDataType;

public class AccountDataTypesInfo {

    private Map<String, CustomDataType> customDataTypeMap;
    private List<CustomDataType> customDataTypesSortedBySensitivity;

    private Set<String> redactedDataTypes;

    private Map<String, AktoDataType> aktoDataTypeMap = new HashMap<>();
    public AccountDataTypesInfo() {
        this.customDataTypeMap = new HashMap<>();
        this.customDataTypesSortedBySensitivity = new ArrayList<>();
        this.aktoDataTypeMap = new HashMap<>();
        this.redactedDataTypes = new HashSet<>();
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
                ", redactedDataTypes='" + getRedactedDataTypes() + "'" +
                "}";
    }

    public Map<String, AktoDataType> getAktoDataTypeMap() {
        return aktoDataTypeMap;
    }

    public void setAktoDataTypeMap(Map<String, AktoDataType> aktoDataTypeMap) {
        this.aktoDataTypeMap = aktoDataTypeMap;
    }

    public Set<String> getRedactedDataTypes() {
        return redactedDataTypes;
    }

    public void setRedactedDataTypes(Set<String> redactedDataTypes) {
        this.redactedDataTypes = redactedDataTypes;
    }
}
