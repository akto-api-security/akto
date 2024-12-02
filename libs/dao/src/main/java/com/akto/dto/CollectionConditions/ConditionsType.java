package com.akto.dto.CollectionConditions;

import java.util.Set;

public class ConditionsType {

    private String key;
    private String value;
    private Set<String> urlsList;


    public ConditionsType () {}

    public ConditionsType (String key, String value, Set<String> urlsList) {
        this.key = key;
        this.value = value;
        this.urlsList = urlsList ;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Set<String> getUrlsList() {
        return urlsList;
    }

    public void setUrlsList(Set<String> urlsList) {
        this.urlsList = urlsList;
    }
}
