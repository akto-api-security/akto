package com.akto.dto;

import org.bson.codecs.pojo.annotations.BsonId;
import com.akto.dao.context.Context;

public class ApiCollectionIcon {
    
    @BsonId
    private String id; // The hostname/iconUrl as the primary key (e.g., "api.github.com")
    
    private String imageData; // Base64 encoded image data
    public static final String IMAGE_DATA = "imageData";
    
    private String contentType;
    public static final String CONTENT_TYPE = "contentType";
    
    private String sourceUrl; // Google Favicon API URL for reference
    public static final String SOURCE_URL = "sourceUrl";
    
    private String description;
    public static final String DESCRIPTION = "description";
    
    private int createdAt;
    public static final String CREATED_AT = "createdAt";

    public ApiCollectionIcon() {
    }

    private String generateFaviconUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null;
        }
        // Ensure URL has protocol
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        return "https://t0.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=" + url + "&size=126";
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getHostname() {
        return id;
    }

    public void setHostname(String hostname) {
        this.id = hostname;
        this.sourceUrl = generateFaviconUrl(hostname);
    }

    public String getImageData() {
        return imageData;
    }

    public void setImageData(String imageData) {
        this.imageData = imageData;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isAvailable() {
        return imageData != null && !imageData.trim().isEmpty();
    }

    public boolean needsRefresh(int maxAgeSeconds) {
        if (createdAt == 0) return true;
        return (Context.now() - createdAt) > maxAgeSeconds;
    }
    public int getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(int createdAt) {
        this.createdAt = createdAt;
    }
}