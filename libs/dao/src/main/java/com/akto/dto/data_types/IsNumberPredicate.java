package com.akto.dto.data_types;

public class IsNumberPredicate extends Predicate{

    public IsNumberPredicate() {
        super(Type.IS_NUMBER);
    }

    @Override
    public boolean validate(Object obj) {
        return (obj instanceof Integer) || (obj instanceof Double);
    }
}
