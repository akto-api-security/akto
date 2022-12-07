package com.akto.dto.testing;

public class AuthParamData {

    private AuthParam.Location where;
    private String key;

    private String value;

    private String valueLocation;

    public AuthParamData() { }
    public AuthParamData(AuthParam.Location where, String key, String value, String valueLocation) {
        this.where = where;
        this.key = key;
        this.value = value;
        this.valueLocation = valueLocation;
    }

    public AuthParam.Location getWhere() {return this.where;}

    public String getKey() {
        return this.key;
    }

    public String getValue() {
        return this.value;
    }

    public String getValueLocation() {
        return this.valueLocation;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public void setValueLocation(String valueLocation) {
        this.valueLocation = valueLocation;
    }

    public void setWhere(AuthParam.Location where) {this.where = where;}

}


