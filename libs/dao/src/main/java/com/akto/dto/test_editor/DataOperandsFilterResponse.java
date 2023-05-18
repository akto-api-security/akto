package com.akto.dto.test_editor;

import java.util.List;

import com.mongodb.BasicDBObject;

public class DataOperandsFilterResponse {
    
    private Boolean result;
    private List<String> matchedEntities;
    private List<BasicDBObject> contextEntities;

    public DataOperandsFilterResponse(Boolean result, List<String> matchedEntities, List<BasicDBObject> contextEntities) {
        this.result = result;
        this.matchedEntities = matchedEntities;
        this.contextEntities = contextEntities;
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

    public List<BasicDBObject> getContextEntities() {
        return contextEntities;
    }

    public void setContextEntities(List<BasicDBObject> contextEntities) {
        this.contextEntities = contextEntities;
    }

}
