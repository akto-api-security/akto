package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class McpRegistryConfig {

    private ObjectId id;

    private String url;
    public static final String URL = "url";

    private Map<String, String> headers;
    public static final String HEADERS = "headers";

    private String hash;
    public static final String HASH = "hash";

    private int createdAt;
    public static final String CREATED_AT = "createdAt";

    private int updatedAt;
    public static final String UPDATED_AT = "updatedAt";

    private RegistryType registryType;
    public static final String REGISTRY_TYPE = "registryType";

    public enum RegistryType {
        CSV_URL
    }

    public McpRegistryConfig(String url, Map<String, String> headers, String hash, int createdAt, int updatedAt, RegistryType registryType) {
        this.url = url;
        this.headers = headers;
        this.hash = hash;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.registryType = registryType;
    }

    @BsonIgnore
    public String getHexId() {
        return id != null ? id.toHexString() : null;
    }
}
