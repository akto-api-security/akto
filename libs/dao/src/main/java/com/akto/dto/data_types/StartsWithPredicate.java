package com.akto.dto.data_types;

public class StartsWithPredicate extends Predicate{
    String value;

    public StartsWithPredicate() {
        super(Type.STARTS_WITH);
    }

    public StartsWithPredicate(String value) {
        super(Type.STARTS_WITH);
        this.value = value;
    }

    @Override
    public boolean validate(Object value) {
        if (!(value instanceof String)) return false;

        String str = value.toString();
        return str.startsWith(this.value);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
