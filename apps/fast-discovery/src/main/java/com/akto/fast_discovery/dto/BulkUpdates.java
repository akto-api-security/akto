package com.akto.fast_discovery.dto;

import java.util.List;
import java.util.Map;

/**
 * BulkUpdates - DTO for database-abstractor bulk write operations.
 * Matches the format expected by /api/bulkWriteSti and /api/bulkWriteApiInfo endpoints.
 */
public class BulkUpdates {
    private Map<String, Object> filters;
    private List<String> updates;

    public BulkUpdates() {
    }

    public BulkUpdates(Map<String, Object> filters, List<String> updates) {
        this.filters = filters;
        this.updates = updates;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters;
    }

    public List<String> getUpdates() {
        return updates;
    }

    public void setUpdates(List<String> updates) {
        this.updates = updates;
    }

    @Override
    public String toString() {
        return "BulkUpdates{" +
                "filters=" + filters +
                ", updates=" + updates +
                '}';
    }
}
