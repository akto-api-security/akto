package com.akto.action;

import com.mongodb.BasicDBObject;

import static com.opensymphony.xwork2.Action.SUCCESS;

public class HealthAction {

    private final BasicDBObject graph_health = new BasicDBObject();
    public String health() {
        graph_health.put("success", true);

        return SUCCESS.toUpperCase();
    }

    public BasicDBObject getGraph_health() {
        return graph_health;
    }
}
