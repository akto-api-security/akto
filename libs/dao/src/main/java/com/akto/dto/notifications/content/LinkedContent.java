package com.akto.dto.notifications.content;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class LinkedContent extends Content {

    Content content;
    String link;

    public LinkedContent() {
    }

    public LinkedContent(Content content, String link) {
        this.content = content;
        this.link = link;
    }

    public Content getContent() {
        return content;
    }

    public void setContent(Content content) {
        this.content = content;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    @Override
    public Type type() {
        return Type.LINKED;
    }
}
