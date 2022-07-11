package com.akto.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class JSONUtils {
    private static void flatten(Object obj, String prefix, Map<String, Set<Object>> ret) {
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            if (prefix != null && !prefix.isEmpty() && (keySet == null || keySet.isEmpty())) {
                Set<Object> values = ret.getOrDefault(prefix, new HashSet<>());
                values.add(obj);
                ret.put(prefix, values);
            }

            for(String key: keySet) {

                if (key == null) {
                    continue;
                }
                boolean anyAlphabetExists = false;

                final int sz = key.length();
                for (int i = 0; i < sz; i++) {
                    final char nowChar = key.charAt(i);
                    if (Character.isLetter(nowChar)) {
                        anyAlphabetExists = true;
                        break;
                    }
                }

                key = anyAlphabetExists ? key: "NUMBER";
                Object value = basicDBObject.get(key);
                flatten(value, prefix + (prefix.isEmpty() ? "" : "#") + key, ret);
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                flatten(elem, prefix+(prefix.isEmpty() ? "$" : "#$"), ret);
            }
        } else {
            Set<Object> values = ret.getOrDefault(prefix, new HashSet<>());
            values.add(obj);
            ret.put(prefix, values);
        }
    }

    public static Map<String, Set<Object>> flatten(BasicDBObject object) {
        Map<String, Set<Object>> ret = new HashMap<>();
        String prefix = "";
        flatten(object, prefix, ret);
        return ret;
    }
}
