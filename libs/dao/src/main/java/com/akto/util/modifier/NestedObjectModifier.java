package com.akto.util.modifier;

import com.mongodb.BasicDBObject;

public class NestedObjectModifier extends PayloadModifier {

    @Override
    public Object modify(String key, Object value) {
        BasicDBObject res = new BasicDBObject();
        res.put(key, value);
        return res;
    }
}
