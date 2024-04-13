package com.akto.action;

import com.akto.utils.DependencyBucketS3Util;
import com.opensymphony.xwork2.ActionSupport;

import java.util.HashMap;
import java.util.Map;

public class DependencyGraphStatusAction extends ActionSupport {
    private String jobId;
    private final Map<String, String> dependency_graph_status = new HashMap<>();
    private final DependencyBucketS3Util s3Util = new DependencyBucketS3Util();

    public String dependencyGraphStatus() {
        String error = s3Util.getErrorMessages(jobId);
        if(error != null && !error.isEmpty()) {
            addActionError(error);
            s3Util.close();
            return ERROR.toUpperCase();
        } else {
            String apiResultJson = s3Util.getApiResultJson(jobId);

            if(apiResultJson != null && !apiResultJson.isEmpty()) {
                dependency_graph_status.put("dependencyGraph", apiResultJson);
            } else {
                addActionError("NOT_FOUND");
                s3Util.close();
                return ERROR.toUpperCase();
            }
        }

        s3Util.close();

        return SUCCESS.toUpperCase();
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Map<String, String> getDependency_graph_status() {
        return dependency_graph_status;
    }
}
