package com.akto.dto.type;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
public class URLStatic extends URLTemplate {
    
    public URLStatic(String url) {
        super(new String[]{url}, null);
    }

}
