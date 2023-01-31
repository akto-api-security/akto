package com.akto.action;

import java.util.List;
import java.util.ArrayList;

import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;

public class MiddlewareConfigAction extends UserAction {

    private String source;
    private BasicDBObject response = new BasicDBObject();
    public String getMiddlewareConfig() {

        List<String> blackList = new ArrayList<>();
        List<String> whiteList = new ArrayList<>();
        response.put("blackList", blackList);
        response.put("whiteList", whiteList);

        return Action.SUCCESS.toUpperCase();
    }

    public BasicDBObject getResponse() {
        return this.response;
    }

    public void setSource(String source) {
        this.source = source;
    }
    
}
