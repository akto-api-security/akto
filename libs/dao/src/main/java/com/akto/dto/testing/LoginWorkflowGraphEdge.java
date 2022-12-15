package com.akto.dto.testing;

public class LoginWorkflowGraphEdge {
    private String source;

    private String target;

    private String id;

    public LoginWorkflowGraphEdge() {
    }

    public LoginWorkflowGraphEdge(String source, String target, String id) {
        this.source = source;
        this.target = target;
        this.id = id;
    }

    public String getSource() {
        return this.source;
    }

    public String getTarget() {
        return this.target;
    }

    public String getId() {
        return this.id;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public void setId(String id) {
        this.id = id;
    }


    @Override
    public String toString() {
        return "{" +
                "\"source\":\"" + getSource() + "\"" +
                ",\"target\":\"" + getTarget() + "\"" +
                ",\"id\":\"" + getId() + "\"" +
                "}";
    }
}
