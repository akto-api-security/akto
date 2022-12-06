package com.akto.dto;

import com.akto.dto.data_types.Conditions;
import com.akto.dto.data_types.Conditions.Operator;

public class CustomAuthType {
    private String name;
    private String[] keys;
    private Conditions.Operator operator;
    public CustomAuthType() {
    }
    public CustomAuthType(String name, String[] keys, Operator operator) {
        this.name = name;
        this.keys = keys;
        this.operator = operator;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String[] getKeys() {
        return keys;
    }
    public void setKeys(String[] keys) {
        this.keys = keys;
    }
    public Conditions.Operator getOperator() {
        return operator;
    }
    public void setOperator(Conditions.Operator operator) {
        this.operator = operator;
    }
    public String generateName(){
        return String.join("_",this.name);
    }
}
