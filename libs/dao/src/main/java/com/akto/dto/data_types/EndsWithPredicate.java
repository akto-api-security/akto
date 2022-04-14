package com.akto.dto.data_types;

public class EndsWithPredicate extends Predicate{
    String value;

    public EndsWithPredicate() {
        super(Type.ENDS_WITH);
    }

    public EndsWithPredicate(String value) {
        super(Type.ENDS_WITH);
        this.value = value;
    }

    @Override
    public boolean validate(Object obj) {
        if (!(obj instanceof String)) return false;

        String str = obj.toString();
        return str.endsWith(this.value);
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
