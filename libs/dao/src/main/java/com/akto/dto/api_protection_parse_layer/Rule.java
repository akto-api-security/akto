package com.akto.dto.api_protection_parse_layer;

public class Rule {

    String name;
    Condition condition;

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
}
