package com.akto.notifications.slack;

public class ActionButtonModel {
    private final String text;
    private final String url;

    public ActionButtonModel(String text, String url) {
        this.text = text;
        this.url = url;
    }

    public String getText() {
        return text;
    }

    public String getUrl() {
        return url;
    }
}
