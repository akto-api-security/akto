package com.akto.util;

import java.util.Set;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class JSONUtils {
    private static void flatten(Object obj, String prefix, BasicDBObject ret) {        
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            if (prefix != null && !prefix.isEmpty() && (keySet == null || keySet.isEmpty())) {
                ret.put(prefix, obj);
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
            ret.put(prefix, obj);
        }
    }

    public static BasicDBObject flatten(BasicDBObject object) {
        BasicDBObject ret = new BasicDBObject();
        String prefix = "";
        flatten(object, prefix, ret);
        return ret;
    }

    public static BasicDBObject flattenWithDots(Object object) {
        BasicDBObject ret = new BasicDBObject();
        String prefix = "";
        flattenWithDots(object, prefix, ret);
        return ret;
    }

    private static void flattenWithDots(Object obj, String prefix, BasicDBObject ret) {
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            if (prefix != null && !prefix.isEmpty() && (keySet == null || keySet.isEmpty())) {
                ret.put(prefix, obj);
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
                flattenWithDots(value, prefix + (prefix.isEmpty() ? "" : ".") + key, ret);
            }
        } else if (obj instanceof BasicDBList) {
            int idx = 0;
            for(Object elem: (BasicDBList) obj) {
                flattenWithDots(elem, prefix+("["+idx+"]"), ret);
                idx += 1;
            }
        } else {
            ret.put(prefix, obj);
        }
    }

}
