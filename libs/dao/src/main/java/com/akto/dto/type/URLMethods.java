package com.akto.dto.type;

import com.akto.util.enums.GlobalEnums;

public class URLMethods {

    public enum Method {
        GET, POST, PUT, DELETE, HEAD, OPTIONS, TRACE, PATCH, OTHER;

        public static final Method[] values = values();
        public static Method fromString(String text) {
            if (text == null) return OTHER;
            for (Method b : Method.values()) {
                if (b.name().equalsIgnoreCase(text)) {
                    return b;
                }
            }
            return OTHER;
        }
    }
}
