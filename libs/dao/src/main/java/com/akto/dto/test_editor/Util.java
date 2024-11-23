package com.akto.dto.test_editor;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class Util {
    public static boolean modifyValueInPayload(Object obj, String parentKey, String queryKey, Object queryVal){
        boolean res = false;
        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();

            for(String key: keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);

                if (!( (value instanceof BasicDBObject) || (value instanceof BasicDBList) )) {
                    if (key.equalsIgnoreCase(queryKey)) {
                        basicDBObject.remove(key);
                        basicDBObject.put(queryKey, queryVal);
                        return true;
                    }
                }

                if (value instanceof BasicDBList) {
                    BasicDBList valList = (BasicDBList) value;
                    if (valList.size() == 0 && key.equalsIgnoreCase(queryKey)) {
                        List<Object> queryList = Collections.singletonList(queryVal);
                        basicDBObject.remove(key);
                        basicDBObject.put(queryKey, queryList);
                        return true;
                    } else if (valList.size() > 0 && !( (valList.get(0) instanceof BasicDBObject) || (valList.get(0) instanceof BasicDBList) ) && key.equalsIgnoreCase(queryKey)) {
                        List<Object> queryList = Collections.singletonList(queryVal);
                        basicDBObject.remove(key);
                        basicDBObject.put(queryKey, queryList);
                        return true;
                    }
                }

                res = modifyValueInPayload(value, key, queryKey, queryVal);
                if (res) {
                    break;
                }
            }
        } else if (obj instanceof BasicDBList) {
            for(Object elem: (BasicDBList) obj) {
                res = modifyValueInPayload(elem, parentKey, queryKey, queryVal);
                if (res) {
                    break;
                }
            }
        }
        return res;
    }
}
