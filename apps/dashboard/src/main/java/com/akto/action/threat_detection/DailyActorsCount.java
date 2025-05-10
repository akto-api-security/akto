package com.akto.action.threat_detection;

public class DailyActorsCount {
    private int ts;
    private int totalActors;
    private int criticalActors;

    public DailyActorsCount(int ts, int totalActors, int criticalActors) {
        this.ts = ts;
        this.totalActors = totalActors;
        this.criticalActors = criticalActors;
    }

    public int getTs() {
        return ts;
    }
    public void setTs(int ts) {
        this.ts = ts;
    }
    public int getTotalActors() {
        return totalActors;
    }
    public void setTotalActors(int totalActors) {
        this.totalActors = totalActors;
    }
    public int getCriticalActors() {
        return criticalActors;
    }
    public void setCriticalActors(int criticalActors) {
        this.criticalActors = criticalActors;
    }
  
}
