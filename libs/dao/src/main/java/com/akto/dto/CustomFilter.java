package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public abstract class CustomFilter {
    public CustomFilter() {
    }

    public abstract boolean process(HttpResponseParams httpResponseParams);
}
