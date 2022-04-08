package com.akto.dto.data_types;

import java.util.regex.Pattern;

public class RegexPredicate extends Predicate{
    private String value;

    public RegexPredicate() {
        super(Type.REGEX);
    }

    public RegexPredicate(String value) {
        super(Type.REGEX);
        this.value = value;
    }

    @Override
    public boolean validate(Object value) {
        if (!(value instanceof String)) return false;
        String str = value.toString();
        Pattern pattern = Pattern.compile(this.value);
        return pattern.matcher(str).matches();
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
