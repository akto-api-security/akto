package com.akto.dto.data_types;

import java.util.regex.Pattern;

public class RegexPredicate extends Predicate{
    private String regex;

    public RegexPredicate() {
        super();
    }

    public RegexPredicate(String regex) {
        super();
        this.regex = regex;
    }

    @Override
    public boolean validate(Object value) {
        if (!(value instanceof String)) return false;
        String str = value.toString();
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(str).matches();
    }

    public String getRegex() {
        return regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
    }
}
