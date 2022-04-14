package com.akto.dto.data_types;

public abstract class Predicate {
    private Type type;

    public enum Type {
        REGEX, STARTS_WITH, ENDS_WITH, IS_NUMBER, EQUALS_TO
    }

    public Predicate(Type type) {
        this.type = type;
    }

    public abstract boolean validate(Object value);

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}
