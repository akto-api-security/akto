package com.akto.dto.data_types;

import java.util.regex.Pattern;

public class RegexPredicate extends Predicate{
    private String value;

    /** Lazily compiled; invalidated when {@link #setValue} runs. */
    private transient volatile Pattern compiledPattern;

    public RegexPredicate() {
        super(Type.REGEX);
    }

    public RegexPredicate(String value) {
        super(Type.REGEX);
        this.value = value;
    }

    private Pattern compiledPattern() {
        Pattern p = compiledPattern;
        if (p != null) {
            return p;
        }
        if (value == null) {
            return null;
        }
        synchronized (this) {
            if (compiledPattern == null && value != null) {
                compiledPattern = Pattern.compile(value);
            }
            return compiledPattern;
        }
    }

    @Override
    public boolean validate(Object obj) {
        if (!(obj instanceof String)) return false;
        Pattern pattern = compiledPattern();
        if (pattern == null) return false;
        String str = obj.toString();
        return pattern.matcher(str).matches();
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        this.compiledPattern = null;
    }

    @Override
    public String toString() {
        return "{" +
            " value='" + getValue() + "'" +
            "}";
    }

}
