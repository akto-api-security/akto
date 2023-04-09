package com.akto.dto.test_editor;

import java.util.List;

public class DataOperandsFilterResponse {
    
    private Boolean result;
    private List<String> matchedEntities;

    public DataOperandsFilterResponse(Boolean result, List<String> matchedEntities) {
        this.result = result;
        this.matchedEntities = matchedEntities;
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
    
}
