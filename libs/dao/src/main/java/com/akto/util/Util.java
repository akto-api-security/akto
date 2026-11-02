package com.akto.util;

import java.nio.charset.StandardCharsets;
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

    /**
     * Truncate to at most maxBytes of UTF-8 without splitting a multi-byte character,
     * appending a note with the original size. Returns s unchanged when it fits.
     */
    public static String truncateUtf8(String s, int maxBytes) {
        if (s == null) {
            return null;
        }
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return s;
        }
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) { // don't cut inside a multi-byte char
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8)
                + "\n[truncated by server: was " + bytes.length + " bytes]";
    }

}
