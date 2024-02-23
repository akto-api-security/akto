package com.akto.util.enums;

public enum HeaderEnums {

    AKTO_IGNORE_FLAG("x-akto-ignore"),
    AKTO_ATTACH_FILE("x-akto-attach-file");

    private final String name;

    HeaderEnums(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
