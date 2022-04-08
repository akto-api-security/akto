package com.akto.dto.data_types;

public abstract class Predicate {
    private Type type;

    enum Type {
        REGEX, STARTS_WITH, ENDS_WITH
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
