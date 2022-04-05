package com.akto.dto.data_types;

import java.util.List;


public class Conditions {
    List<Predicate> predicates;
    Operator operator;

    public Conditions() {
    }

    public Conditions(List<Predicate> predicates, Operator operator) {
        this.predicates = predicates;
        this.operator = operator;
    }

    public boolean validate(Object value) {
        boolean result = predicates.get(0).validate(value);
        for (int i = 1 ; i < this.predicates.size(); i++) {
            Predicate predicate = predicates.get(i);
            switch (operator) {
                case AND:
                    result = result && predicate.validate(value);
                    break;
                case OR:
                    result = result || predicate.validate(value);
                    break;
            }
        }

        return result;
    }

    public enum Operator {
        AND, OR
    }

    public List<Predicate> getPredicates() {
        return predicates;
    }

    public void setPredicates(List<Predicate> predicates) {
        this.predicates = predicates;
    }

    public Operator getOperator() {
        return operator;
    }

    public void setOperator(Operator operator) {
        this.operator = operator;
    }
}
