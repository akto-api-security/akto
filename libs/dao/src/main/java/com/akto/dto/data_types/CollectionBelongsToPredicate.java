package com.akto.dto.data_types;

import com.akto.dto.ApiCollection;

import java.util.Set;

public class CollectionBelongsToPredicate extends Predicate {

    private Set<String> value;

    public CollectionBelongsToPredicate(Set<String> value) {
        super(Type.COLLECTION_BELONGS_TO);
        this.value = value;
    }

    @Override
    public boolean validate(Object value) {//Only accept ApiCollection
        if (this.value != null) {
            if (value instanceof ApiCollection) {
                ApiCollection infoKey = (ApiCollection) value;
                return this.value.contains(infoKey.getDisplayName());
            }
        }
        return false;
    }

    public Set<String> getValue() {
        return value;
    }

    public void setValue(Set<String> value) {
        this.value = value;
    }
}
