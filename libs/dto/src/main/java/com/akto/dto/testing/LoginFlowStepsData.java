package com.akto.dto.testing;

import java.util.ArrayList;
import java.util.Map;

public class LoginFlowStepsData {

    private Integer userID;

    private Map<String, Object> valuesMap;

    public LoginFlowStepsData() { }
    public LoginFlowStepsData(Integer userID, Map<String, Object> valuesMap) {
        this.userID = userID;
        this.valuesMap = valuesMap;
    }

    public Integer getUserID() {
        return this.userID;
    }

    public Map<String, Object> getValuesMap() {
        return this.valuesMap;
    }

    public void setUserID(Integer userID) {
        this.userID = userID;
    }

    public void setValuesMap(Map<String, Object> valuesMap) {
        this.valuesMap = valuesMap;
    }

}
