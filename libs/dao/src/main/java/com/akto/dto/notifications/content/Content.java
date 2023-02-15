package com.akto.dto.notifications.content;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public abstract class Content {

    public enum Type {
        COMPOSITE, IMAGE, LINKED, NEWLINE, STRING, BLOCK;
    }

    public abstract Type type();

}
