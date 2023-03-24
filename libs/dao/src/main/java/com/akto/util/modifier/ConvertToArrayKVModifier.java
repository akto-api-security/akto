package com.akto.util.modifier;

import java.util.Collections;

public class ConvertToArrayKVModifier extends KVModifier{

    @Override
    public Object modify(String key, Object value) {
        return Collections.singletonList(value);
    }
}
