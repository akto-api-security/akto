package com.akto.util.modifier;

import java.util.HashMap;
import java.util.Map;

public class SetValueModifier extends PayloadModifier {

    Map<String, Object> store = new HashMap<>();

    public SetValueModifier(Map<String, Object> store) {
        super();
        this.store = store;
    }

    @Override
    public Object modify(String key, Object value) {
        if (!store.containsKey(key)) return value;
        return store.get(key);
    }
}
