package com.akto.util;

import java.util.ArrayList;
import java.util.List;

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

}