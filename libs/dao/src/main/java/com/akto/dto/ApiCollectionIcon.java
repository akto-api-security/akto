package com.akto.dto;

import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.bson.codecs.pojo.annotations.BsonId;
import com.akto.dao.context.Context;
import java.util.List;

@Getter @Setter
public class ApiCollectionIcon {
    
    @BsonId
    private ObjectId id;
    
    private String domainName; // Main domain name (e.g., "github.com")
    public static final String DOMAIN_NAME = "domainName";
    
    private List<String> matchingHostnames; // List of hostnames that match this icon (e.g., ["api.github.com", "raw.github.com"])
    public static final String MATCHING_HOSTNAMES = "matchingHostnames";
    
    private String imageData; // Base64 encoded image data
    public static final String IMAGE_DATA = "imageData";
    
    private int createdAt;
    public static final String CREATED_AT = "createdAt";
    
    private int updatedAt;
    public static final String UPDATED_AT = "updatedAt";

    public ApiCollectionIcon() {
    }

    public ApiCollectionIcon(String domainName, List<String> matchingHostnames, String imageData) {
        this.domainName = domainName;
        this.matchingHostnames = matchingHostnames;
        this.imageData = imageData;
        int now = Context.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    // Generate Google Favicon API URL for this domain
    public String getSourceUrl() {
        if (domainName == null) return null;
        return "https://t0.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://" + domainName + "&size=64";
    }

    // Helper method to check if icon is available
    public boolean isAvailable() {
        return imageData != null && !imageData.isEmpty();
    }
}