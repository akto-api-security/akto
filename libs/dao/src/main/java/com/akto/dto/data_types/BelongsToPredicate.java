package com.akto.dto.data_types;

import com.akto.dto.ApiInfo;

import java.util.Set;

public class BelongsToPredicate extends Predicate{

    private Set<ApiInfo.ApiInfoKey> value;

    public BelongsToPredicate() {
        super(Type.BELONGS_TO);
    }
    public BelongsToPredicate(Set<ApiInfo.ApiInfoKey> value) {
        super(Type.BELONGS_TO);
            this.value = value;
    }

    @Override
    public boolean validate(Object value) {
        if (this.value != null) {
            return this.value.contains(value);
        }
        return false;
    }

    public Set<ApiInfo.ApiInfoKey> getValue() {
        return value;
    }

    public void setValue(Set<ApiInfo.ApiInfoKey> value) {
        this.value = value;
    }
}
