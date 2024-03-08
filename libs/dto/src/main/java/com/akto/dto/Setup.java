package com.akto.dto;

public class Setup {
    
    private String dashboardMode;

    public Setup() {
    }

    public Setup(String dashboardMode) {
        this.dashboardMode = dashboardMode;
    }

    public String getDashboardMode() {
        return dashboardMode;
    }

    public void setDashboardMode(String dashboardMode) {
        this.dashboardMode = dashboardMode;
    }
}
