package com.akto.dto.data_types;

import java.util.regex.Pattern;

public class EqualsToPredicate extends Predicate{
    private String value;

    public EqualsToPredicate() {
        super(Type.EQUALS_TO);
    }

    public EqualsToPredicate(String value) {
        super(Type.EQUALS_TO);
        this.value = value;
    }

    @Override
    public boolean validate(Object obj) {
        if (!(obj instanceof String)) return false;
        String str = obj.toString();
        str = str.trim();
        return str.equals(this.value);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
