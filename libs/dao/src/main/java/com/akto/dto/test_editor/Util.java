package com.akto.dto.test_editor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

public class Util {

    public static boolean modifyValueInPayload(Object obj, String parentKey, String queryKey, Object queryVal){
        return modifyValueInPayload(obj, parentKey, queryKey, queryVal, new HashSet<>(), "");
    }

    private static boolean modifyValueInPayload(Object obj, String parentKey, String queryKey, Object queryVal, Set<String> visitedPaths, String currentPath){
        if (obj == null) {
            return false;
        }

        String pathKey;
        if (currentPath.isEmpty()) {
            pathKey = (parentKey == null) ? "" : parentKey; // If parentKey is null, use an empty path
        } else {
            pathKey = (parentKey == null) ? currentPath : currentPath + "." + parentKey; // Append parentKey only if it's not null
        }

        // If this path has already been processed, skip it
        if (visitedPaths.contains(pathKey)) {
            return false;
        }

        visitedPaths.add(pathKey); // Mark this path as visited

        boolean res = false;

        if (obj instanceof BasicDBObject) {
            BasicDBObject basicDBObject = (BasicDBObject) obj;

            Set<String> keySet = basicDBObject.keySet();
            // Collect keys to modify after iteration
            Set<String> keysToModify = new HashSet<>();

            for (String key : keySet) {
                if (key == null) {
                    continue;
                }
                Object value = basicDBObject.get(key);

                if (!(value instanceof BasicDBObject || value instanceof BasicDBList)) {
                    if (key.equalsIgnoreCase(queryKey)) {
                        keysToModify.add(key);
                        res = true;
                    }
                }

                if (value instanceof BasicDBList) {
                    BasicDBList valList = (BasicDBList) value;
                    if (valList.isEmpty() && key.equalsIgnoreCase(queryKey)) {
                        keysToModify.add(key);
                        res = true;
                    } else if (!valList.isEmpty() &&
                            !(valList.get(0) instanceof BasicDBObject || valList.get(0) instanceof BasicDBList) &&
                            key.equalsIgnoreCase(queryKey) &&
                            (queryVal instanceof List || queryVal instanceof BasicDBList)) {
                        keysToModify.add(key);
                        res = true;
                    }
                }

                // Recurse with the updated path
                res = modifyValueInPayload(value, key, queryKey, queryVal, visitedPaths, pathKey) || res;
            }

            // Modify keys after iteration
            for (String key : keysToModify) {
                basicDBObject.remove(key);
                basicDBObject.put(queryKey, queryVal);
            }
        } else if (obj instanceof BasicDBList) {
            int index = 0;
            for (Object elem : (BasicDBList) obj) {
                // Use the index of the element in the path
                String listPath = pathKey + "[" + index + "]";
                res = modifyValueInPayload(elem, null, queryKey, queryVal, visitedPaths, listPath) || res;
                index++;
            }
        }

        return res;
    }
}
