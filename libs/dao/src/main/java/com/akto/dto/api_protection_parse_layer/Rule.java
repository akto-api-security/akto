package com.akto.dto.api_protection_parse_layer;

import com.akto.dto.test_editor.ConfigParserResult;

public class Rule {

    String name;
    Condition condition;
    ConfigParserResult aggregationEvaluator;

    public Rule() {
    }

    public Rule(String name, Condition condition) {
        this.name = name;
        this.condition = condition;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Condition getCondition() {
        return condition;
    }
    public void setCondition(Condition condition) {
        this.condition = condition;
    }
    public ConfigParserResult getAggregationEvaluator() {
        return aggregationEvaluator;
    }
    public void setAggregationEvaluator(ConfigParserResult aggregationEvaluator) {
        this.aggregationEvaluator = aggregationEvaluator;
    }
}
