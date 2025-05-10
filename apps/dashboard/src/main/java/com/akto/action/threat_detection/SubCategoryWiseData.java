package com.akto.action.threat_detection;

public class SubCategoryWiseData {
    
    private String subcategory;
    private int activityCount;

    public SubCategoryWiseData(String subcategory, int activityCount) {
        this.subcategory = subcategory;
        this.activityCount = activityCount;
    }

    public String getSubcategory() {
        return subcategory;
    }
    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }
    public int getActivityCount() {
        return activityCount;
    }
    public void setActivityCount(int activityCount) {
        this.activityCount = activityCount;
    }

}
