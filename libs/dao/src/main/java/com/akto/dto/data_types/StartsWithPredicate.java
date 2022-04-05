package com.akto.dto.data_types;

public class StartsWithPredicate extends Predicate{
    String prefix;

    public StartsWithPredicate() {
        super();
    }

    public StartsWithPredicate(String prefix) {
        super();
        this.prefix = prefix;
    }

    @Override
    public boolean validate(Object value) {
        if (!(value instanceof String)) return false;

        String str = value.toString();
        return str.startsWith(prefix);
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
}
