package com.akto.dto.billing;

import com.akto.dao.context.Context;
import com.akto.dto.type.URLMethods;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.types.ObjectId;

import java.util.Objects;

public class UningestedApiOverage {
    
    @BsonId
    private ObjectId id;
    public static final String ID = "_id";
    
    private int apiCollectionId;
    public static final String API_COLLECTION_ID = "apiCollectionId";
    
    private String urlType; // "STATIC" or "TEMPLATE"
    public static final String URL_TYPE = "urlType";
    
    private int timestamp;
    public static final String TIMESTAMP = "timestamp";

    private URLMethods.Method method;
    public static final String METHOD = "method";
    
    private String url;
    public static final String URL = "url";
    
    public UningestedApiOverage() {}

    public UningestedApiOverage(int apiCollectionId, String urlType, String methodAndUrl) {
        this.apiCollectionId = apiCollectionId;
        this.urlType = urlType;
        this.timestamp = Context.now();
        
        // Parse methodAndUrl to extract method and url
        parseMethodAndUrl(methodAndUrl);
    }

    public UningestedApiOverage(int apiCollectionId, String urlType, URLMethods.Method method, String url) {
        this.apiCollectionId = apiCollectionId;
        this.urlType = urlType;
        this.timestamp = Context.now();
        this.method = method != null ? method : URLMethods.Method.OTHER;
        this.url = url != null ? url : "";
    }

    public UningestedApiOverage(int apiCollectionId, String urlType, String method, String url) {
        this.apiCollectionId = apiCollectionId;
        this.urlType = urlType;
        this.timestamp = Context.now();
        this.method = URLMethods.Method.fromString(method.toUpperCase());
        this.url = url != null ? url : "";
    }

    private void parseMethodAndUrl(String methodAndUrl) {
        if (methodAndUrl != null && !methodAndUrl.trim().isEmpty()) {
            String trimmed = methodAndUrl.trim();
            String[] parts = trimmed.split(" ", 2);
            if (parts.length >= 2) {
                this.method = URLMethods.Method.fromString(parts[0].trim());
                this.url = parts[1].trim();
            } else {
                // If no space found, treat the entire string as URL with OTHER method
                this.method = URLMethods.Method.OTHER;
                this.url = trimmed;
            }
        } else {
            this.method = URLMethods.Method.OTHER;
            this.url = "";
        }
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

    public URLMethods.Method getMethod() {
        return method;
    }

    public void setMethod(URLMethods.Method method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UningestedApiOverage that = (UningestedApiOverage) o;
        return
                apiCollectionId == that.apiCollectionId &&
                Objects.equals(urlType, that.urlType) &&
                Objects.equals(method, that.method) &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiCollectionId, urlType, method, url);
    }

    @Override
    public String toString() {
        return "OverageInfo{" +
                "id=" + id +
                ", apiCollectionId=" + apiCollectionId +
                ", urlType='" + urlType + '\'' +
                ", timestamp=" + timestamp +
                ", method=" + method +
                ", url='" + url + '\'' +
                '}';
    }
}