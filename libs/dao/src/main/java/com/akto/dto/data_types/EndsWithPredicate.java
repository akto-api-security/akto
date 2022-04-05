package com.akto.dto.data_types;

public class EndsWithPredicate extends Predicate{
    String suffix;

    public EndsWithPredicate() {
        super();
    }

    public EndsWithPredicate(String suffix) {
        super();
        this.suffix = suffix;
    }

    @Override
    public boolean validate(Object value) {
        if (!(value instanceof String)) return false;

        String str = value.toString();
        return str.endsWith(suffix);
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
}
