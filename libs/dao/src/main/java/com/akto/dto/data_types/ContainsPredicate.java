package com.akto.dto.data_types;

import com.akto.dto.ApiInfo;

public class ContainsPredicate extends Predicate{

    private String value;


    public ContainsPredicate() {
        super(Type.CONTAINS);
    }

    public ContainsPredicate(String value) {
        super(Type.CONTAINS);
        this.value = value;
    }

    @Override
    public boolean validate(Object value) {
        if (value instanceof ApiInfo.ApiInfoKey) {
            ApiInfo.ApiInfoKey infoKey = (ApiInfo.ApiInfoKey) value;
            return infoKey.getUrl() != null && (infoKey.getUrl().contains(this.value) || infoKey.getUrl().matches(this.value));
        } else if (value instanceof String) {
            return ((String) value).contains(this.value) || ((String) value).matches(this.value);
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
