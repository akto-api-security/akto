package com.akto.notifications.slack;

public class FieldsModel {
    private final String title;
    private final String value;

    public FieldsModel(String title, String value) {
        this.title = title;
        this.value = value;
    }

    /* \u200C: http://www.unicode-symbol.com/u/200C.html */
    public String toCustomString(boolean isLastTwo) {
        return title + "\n*" + value + "*" + (isLastTwo ? "" : "\n\u200c");
    }
}
