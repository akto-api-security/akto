package com.akto.dto.bulk_updates;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class BulkUpdates {

    private Map<String, Object> filters;
    private ArrayList<String> updates;

    public BulkUpdates() {
    }

    public BulkUpdates(Map<String, Object> filters, ArrayList<String> updates) {
        this.filters = filters;
        this.updates = updates;
    }

    public Map<String,Object> getFilters() {
        return this.filters;
    }

    public void setFilters(Map<String,Object> filters) {
        this.filters = filters;
    }

    public ArrayList<String> getUpdates() {
        return this.updates;
    }

    public void setUpdates(ArrayList<String> updates) {
        this.updates = updates;
    }

}