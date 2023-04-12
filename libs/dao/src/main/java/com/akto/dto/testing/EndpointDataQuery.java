package com.akto.dto.testing;

import java.util.ArrayList;

public class EndpointDataQuery {

    private ArrayList<EndpointDataFilterCondition> filterConditions;
    private ArrayList<EndpointDataSortCondition> sortConditions;

    public EndpointDataQuery() {}

    public EndpointDataQuery(ArrayList<EndpointDataFilterCondition> filterConditions, 
        ArrayList<EndpointDataSortCondition> sortConditions) {

        this.filterConditions = filterConditions;
        this.sortConditions = sortConditions;
    }

    public ArrayList<EndpointDataFilterCondition> getFilterConditions() {
        return this.filterConditions;
    }

    public void setFilterConditions(ArrayList<EndpointDataFilterCondition> filterConditions) {
        this.filterConditions = filterConditions;
    }

    public ArrayList<EndpointDataSortCondition> getSortConditions() {
        return this.sortConditions;
    }

    public void setSortConditions(ArrayList<EndpointDataSortCondition> sortConditions) {
        this.sortConditions = sortConditions;
    }

}
