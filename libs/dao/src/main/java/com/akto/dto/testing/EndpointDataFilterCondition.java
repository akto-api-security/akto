package com.akto.dto.testing;

import java.util.ArrayList;

public class EndpointDataFilterCondition {
    
    private String key;
    private String operator;
    private ArrayList<String> values;

    public EndpointDataFilterCondition() {}

    public EndpointDataFilterCondition(String key, ArrayList<String> values, String operator) {

        this.key = key;
        this.operator = operator;
        this.values = values;
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public ArrayList<String> getValues() {
        return this.values;
    }

    public void setValues(ArrayList<String> values) {
        this.values = values;
    }

    public String getOperator() {
        return this.operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

}
