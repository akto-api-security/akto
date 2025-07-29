package com.akto.dto.billing;

import com.akto.dao.context.Context;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.Objects;

public class UningesetedApiOverage {
    
    @BsonId
    private ObjectId id;
    public static final String ID = "_id";
    
    private int apiCollectionId;
    public static final String API_COLLECTION_ID = "apiCollectionId";
    
    private String urlType; // "STATIC" or "TEMPLATE"
    public static final String URL_TYPE = "urlType";
    
    private int timestamp;
    public static final String TIMESTAMP = "timestamp";

    private String methodAndUrl;
    public static final String METHOD_AND_URL = "methodAndUrl";
    
    public UningesetedApiOverage() {}

    public UningesetedApiOverage(int apiCollectionId, String urlType, String methodAndUrl) {
        this.apiCollectionId = apiCollectionId;
        this.urlType = urlType;
        this.timestamp = Context.now();
        this.methodAndUrl = methodAndUrl;
    }

    // Getters and Setters
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public String getUrlType() {
        return urlType;
    }

    public void setUrlType(String urlType) {
        this.urlType = urlType;
    }

    public int getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(int timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UningesetedApiOverage that = (UningesetedApiOverage) o;
        return
                apiCollectionId == that.apiCollectionId &&
                Objects.equals(urlType, that.urlType) &&
                Objects.equals(methodAndUrl, that.methodAndUrl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiCollectionId, urlType, methodAndUrl);
    }

    @Override
    public String toString() {
        return "OverageInfo{" +
                "id=" + id +
                ", apiCollectionId=" + apiCollectionId +
                ", urlType='" + urlType + '\'' +
                ", timestamp=" + timestamp +
                ", methodAndUrl=" + methodAndUrl +
                '}';
    }

    public String getMethodAndUrl() {
        return methodAndUrl;
    }

    public void setMethodAndUrl(String methodAndUrl) {
        this.methodAndUrl = methodAndUrl;
    }
}