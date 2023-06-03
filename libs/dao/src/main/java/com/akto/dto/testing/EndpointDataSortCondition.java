package com.akto.dto.testing;

public class EndpointDataSortCondition {
    
    private String key;
    private int sortOrder;

    public EndpointDataSortCondition() {}

    public EndpointDataSortCondition(String key, int sortOrder) {

        this.key = key;
        this.sortOrder = sortOrder;
    }

    public String getKey() {
        return this.key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public int getSortOrder() {
        return this.sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

}
