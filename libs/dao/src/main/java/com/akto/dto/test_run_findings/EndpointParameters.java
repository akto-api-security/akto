package com.akto.dto.test_run_findings;

import com.akto.dto.type.URLMethods;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

public class EndpointParameters {
    /*
    * Issues Endpoint description needs these parameters
    * */
    public static final String URL = "url";
    @BsonProperty(value = URL)
    public String url;

    public static final String METHOD = "method";
    @BsonProperty(value = METHOD)
    public URLMethods.Method method;

    public static final String COLLECTION_ID = "collection_id";
    @BsonProperty(value = COLLECTION_ID)
    public ObjectId objectId;
}
