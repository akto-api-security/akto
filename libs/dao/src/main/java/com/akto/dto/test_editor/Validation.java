package com.akto.dto.test_editor;

public class Validation {
 
    private String status;

    private String respMatch;

    private String respContains;

    public Validation(String status, String respMatch, String respContains) {
        this.status = status;
        this.respMatch = respMatch;
        this.respContains = respContains;
    }

    public Validation() { }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRespMatch() {
        return respMatch;
    }

    public void setRespMatch(String respMatch) {
        this.respMatch = respMatch;
    }

    public String getRespContains() {
        return respContains;
    }

    public void setRespContains(String respContains) {
        this.respContains = respContains;
    }
    
}
