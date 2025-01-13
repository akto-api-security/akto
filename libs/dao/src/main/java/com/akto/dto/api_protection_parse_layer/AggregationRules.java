package com.akto.dto.api_protection_parse_layer;

import java.util.List;

public class AggregationRules {

    private List<Rule> rule;

    public AggregationRules() {
    }

    public AggregationRules(List<Rule> rule) {
        this.rule = rule;
    }

    public List<Rule> getRule() {
        return rule;
    }

    public void setRule(List<Rule> rule) {
        this.rule = rule;
    }

}
