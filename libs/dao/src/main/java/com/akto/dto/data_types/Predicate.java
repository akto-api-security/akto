package com.akto.dto.data_types;

public abstract class Predicate {
    public Predicate() {}

    public abstract boolean validate(Object value);
}
