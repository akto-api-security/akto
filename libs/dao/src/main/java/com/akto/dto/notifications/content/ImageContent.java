package com.akto.dto.notifications.content;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class ImageContent extends Content {
    String name;
    String url;
    int width;

    public ImageContent() {
    }

    public ImageContent(String name, String url, int width) {
        this.name = name;
        this.url = url;
        this.width = width;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    @Override
    public Type type() {
        return Type.IMAGE;
    }
}
