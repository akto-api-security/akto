package com.akto.dto.data_types;

import com.akto.dto.ApiInfo;

import java.util.Set;

public class NotBelongsToPredicate extends Predicate{

    private Set<ApiInfo.ApiInfoKey> value;

    public NotBelongsToPredicate(Set<ApiInfo.ApiInfoKey> value) {
        super(Type.NOT_BELONGS_TO);
        this.value = value;
    }
    public NotBelongsToPredicate() {
        super(Type.NOT_BELONGS_TO);
        this.value = null;
    }

    @Override
    public boolean validate(Object value) {
        if (this.value != null) {//Replacing instance for generic type
            return !this.value.contains(value);
        }
        return true;
    }

    public Set<ApiInfo.ApiInfoKey> getValue() {
        return value;
    }

    public void setValue(Set<ApiInfo.ApiInfoKey> value) {
        this.value = value;
    }
}
