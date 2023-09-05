package com.akto.dto.data_types;

import java.util.List;
import java.util.Objects;

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
        if (predicates == null || predicates.size() == 0) return false;
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


    @Override
    public String toString() {
        return "{" +
            " predicates='" + getPredicates() + "'" +
            ", operator='" + getOperator() + "'" +
            "}";
    }

    @Override
    public int hashCode() {
        return Objects.hash(predicates, operator);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Conditions)) {
            return false;
        }
        Conditions conditions = (Conditions) o;

        boolean ret = true;

        if((conditions.operator==null && this.operator!=null) || !conditions.operator.equals(this.operator)){
            ret = false;
        }

        if(conditions.predicates!=null && this.predicates!=null){

            for(Predicate p1 : conditions.predicates){
                boolean found = false;
                for(Predicate p2 : this.predicates){
                    if(p2.toString().equals(p1.toString())){
                        found = true;
                        break;
                    }
                }
                if(!found){
                    ret= false;
                    break;
                }
            }

        } else if(!(conditions.predicates==null && this.predicates==null)){
            ret = false;
        }

        return ret;
    }

    public static boolean areEqual(Conditions a, Conditions b) {
        boolean ret = false;

        ret = ((a == null && b == null) ||
                (a != null && b != null && a.equals(b)));

        return ret;
    }
}
