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
}