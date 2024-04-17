package com.akto.dto.testing.config;

import com.akto.util.enums.GlobalEnums;

import java.util.List;

public class TestCollectionProperty {

    public enum Status {
        PENDING, DONE
    }
    public enum Type {
        CUSTOM_AUTH, TEST_YAML_KEYWORD, ROLE
    }
    String name;
    String lastUpdatedUser;
    int lastUpdatedEpoch;
    List<String> values;
    List<GlobalEnums.TestCategory> impactingCategories;
    int apiCollectionId;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLastUpdatedUser() {
        return lastUpdatedUser;
    }

    public void setLastUpdatedUser(String lastUpdatedUser) {
        this.lastUpdatedUser = lastUpdatedUser;
    }

    public int getLastUpdatedEpoch() {
        return lastUpdatedEpoch;
    }

    public void setLastUpdatedEpoch(int lastUpdatedEpoch) {
        this.lastUpdatedEpoch = lastUpdatedEpoch;
    }

    public List<String> getValues() {
        return values;
    }

    public void setValues(List<String> values) {
        this.values = values;
    }

    public List<GlobalEnums.TestCategory> getImpactingCategories() {
        return impactingCategories;
    }

    public void setImpactingCategories(List<GlobalEnums.TestCategory> impactingCategories) {
        this.impactingCategories = impactingCategories;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }
}
