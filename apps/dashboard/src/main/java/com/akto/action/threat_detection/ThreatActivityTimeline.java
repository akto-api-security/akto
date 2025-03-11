package com.akto.action.threat_detection;

import java.util.List;

public class ThreatActivityTimeline {
    
    private int ts;
    private List<SubCategoryWiseData> subCategoryWiseData;

    public ThreatActivityTimeline(int ts, List<SubCategoryWiseData> subCategoryWiseData) {
        this.ts = ts;
        this.subCategoryWiseData = subCategoryWiseData;
    }

    public int getTs() {
        return ts;
    }

    public void setTs(int ts) {
        this.ts = ts;
    }

    public List<SubCategoryWiseData> getSubCategoryWiseData() {
        return subCategoryWiseData;
    }

    public void setSubCategoryWiseData(List<SubCategoryWiseData> subCategoryWiseData) {
        this.subCategoryWiseData = subCategoryWiseData;
    }

}
