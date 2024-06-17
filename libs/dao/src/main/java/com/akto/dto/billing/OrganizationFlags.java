package com.akto.dto.billing;

import java.util.Set;

public class OrganizationFlags {

    public static final String AGGREGATE_INCOMPLETE = "aggregateIncomplete";
    Set<String> aggregateIncomplete;

    public OrganizationFlags() {
    }

    public OrganizationFlags(Set<String> aggregateIncomplete) {
        this.aggregateIncomplete = aggregateIncomplete;
    }

    public Set<String> getAggregateIncomplete() {
        return aggregateIncomplete;
    }

    public void setAggregateIncomplete(Set<String> aggregateIncomplete) {
        this.aggregateIncomplete = aggregateIncomplete;
    }

}