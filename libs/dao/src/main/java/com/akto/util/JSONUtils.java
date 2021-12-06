package com.akto.util;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class JSONUtils {
    private static void flatten(Object obj, String prefix, BasicDBObject ret) {        
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;
            for(String key: basicDBObject.keySet()) {
                Object value = basicDBObject.get(key);
                flatten(value, prefix + (prefix.isEmpty() ? "" : "#") + key, ret);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                flatten(elem, prefix+(prefix.isEmpty() ? "$" : "#$"), ret);
            }
        } else {
            ret.put(prefix, obj);
        }
    }

    public static BasicDBObject flatten(BasicDBObject object) {
        BasicDBObject ret = new BasicDBObject();
        String prefix = "";
        flatten(object, prefix, ret);
        return ret;
    }
}
