package com.akto.dto.test_editor;

public class Strategy {
    
    private String runOnce;
    private boolean insertVulnApi;

    public Strategy(String runOnce, boolean insertVulnApi) {
        this.runOnce = runOnce;
        this.insertVulnApi = insertVulnApi;
    }

    public Strategy() {
    }

    public String getRunOnce() {
        return runOnce;
    }

    public void setRunOnce(String runOnce) {
        this.runOnce = runOnce;
    }

    public boolean getInsertVulnApi() {
        return insertVulnApi;
    }
    
    public void setInsertVulnApi(boolean insertVulnApi) {
        this.insertVulnApi = insertVulnApi;
    }
    
}
