package com.akto.action;

import com.mongodb.BasicDBObject;
import org.apache.struts2.ServletActionContext;

import static com.opensymphony.xwork2.Action.SUCCESS;

public class JobStatusAction {

    private String jobId;
    private final BasicDBObject dependency_graph = new BasicDBObject();
    public String getDependencyGraph () {
        dependency_graph.put("GRAPH", "Your graph ID: " + jobId);
//        dependency_graph.put("PENDING", "Building graph ID: " + jobId);
        return SUCCESS.toUpperCase();
    }

    public BasicDBObject getDependency_graph() {
        return dependency_graph;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}
