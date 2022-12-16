package com.akto.dto.data_types;

public class ContainsPredicate extends Predicate{

    private String value;

    public ContainsPredicate(Type type) {
        super(Type.CONTAINS);
    }

    public ContainsPredicate(String value) {
        super(Type.CONTAINS);
        this.value = value;
    }

    @Override
    public boolean validate(Object value) {
        if (value instanceof String) {
            String str = (String) value;
            if (str.contains(this.value)) {
                return true;
            }
        }
        return false;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
