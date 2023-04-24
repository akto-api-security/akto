package com.akto.dto.test_editor;

import java.util.List;

public class DataOperandsFilterResponse {
    
    private Boolean result;
    private List<String> matchedEntities;
    private List<String> matchedValues;

    public DataOperandsFilterResponse(Boolean result, List<String> matchedEntities, List<String> matchedValues) {
        this.result = result;
        this.matchedEntities = matchedEntities;
        this.matchedValues = matchedValues;
    }

    public DataOperandsFilterResponse() { }

    public Boolean getResult() {
        return result;
    }

    public void setResult(Boolean result) {
        this.result = result;
    }

    public List<String> getMatchedEntities() {
        return matchedEntities;
    }

    public void setMatchedEntities(List<String> matchedEntities) {
        this.matchedEntities = matchedEntities;
    }

    public List<String> getMatchedValues() {
        return matchedValues;
    }

    public void setMatchedValues(List<String> matchedValues) {
        this.matchedValues = matchedValues;
    }
    
}
