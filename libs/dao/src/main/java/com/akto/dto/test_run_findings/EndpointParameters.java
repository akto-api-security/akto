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
    private final String url;

    public static final String METHOD = "method";
    @BsonProperty(value = METHOD)
    private final URLMethods.Method method;

    public static final String COLLECTION_ID = "collection_id";
    @BsonProperty(value = COLLECTION_ID)
    private final ObjectId objectId;

    EndpointParameters(ObjectId objectId, String url, URLMethods.Method method) {
        this.objectId = objectId;
        this.url = url;
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public URLMethods.Method getMethod() {
        return method;
    }

    public ObjectId getObjectId() {
        return objectId;
    }
}
