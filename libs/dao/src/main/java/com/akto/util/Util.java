package com.akto.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util {
    
    public static <T> List<T> replaceElementInList(List<T> list, T to, T from) {
        if (list == null) {
            list = new ArrayList<>();
        }
        if (from != null) {
            list.remove(from);
        }
        if (to != null) {
            list.add(to);
        }

        return list;
    }

    private static final String DOLLAR = "$";

    public static String prefixDollar(String str) {
        if (str == null) {
            return null;
        }
        return DOLLAR + str;
    }

    public static String getEnvironmentVariable(String var){
        return System.getenv(var);
    }

    public static long getLongValue(Object obj) {
        long ret = 0;
        if (obj instanceof Integer) {
            ret = (int) obj;
        } else if (obj instanceof Long) {
            ret = (long) obj;
        } else {
            throw new IllegalArgumentException("Unsupported type for count");
        }
        return ret;
    }

    public static String getValueFromQueryString(String queryParamsStr, String key){
        if(queryParamsStr == null){
            return  "";
        }
        String[] queryParams = queryParamsStr.split("&");
        Map<String, String> queryMap = new HashMap<>();
        for (String queryParam : queryParams) {
            String[] keyVal = queryParam.split("=");
            if (keyVal.length != 2) {
                continue;
            }
            queryMap.put(keyVal[0], keyVal[1]);
        }

        return queryMap.getOrDefault(key, "");
    }

}