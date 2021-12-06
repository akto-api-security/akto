package com.akto.dto.notifications.content;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class NewlineContent extends Content {


    @Override
    public Type type() {
        return Type.NEWLINE;
    }
}
