package com.akto.dto;

import java.util.List;

import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BrowserExtensionConfig {

    private ObjectId id;

    @BsonIgnore
    private String hexId;

    public static final String HOST = "host";
    private String host;

    public static final String PATHS = "paths";
    private List<String> paths;

    public static final String ACTIVE = "active";
    private boolean active;

    public static final String CREATED_BY = "createdBy";
    private String createdBy;

    public static final String UPDATED_BY = "updatedBy";
    private String updatedBy;

    public static final String CREATED_TIMESTAMP = "createdTimestamp";
    private int createdTimestamp;

    public static final String UPDATED_TIMESTAMP = "updatedTimestamp";
    private int updatedTimestamp;

    public BrowserExtensionConfig(String host, List<String> paths, boolean active,
                                  String createdBy, String updatedBy,
                                  int createdTimestamp, int updatedTimestamp) {
        this.host = host;
        this.paths = paths;
        this.active = active;
        this.createdBy = createdBy;
        this.updatedBy = updatedBy;
        this.createdTimestamp = createdTimestamp;
        this.updatedTimestamp = updatedTimestamp;
    }

    public String getHexId() {
        if (this.id != null) {
            return this.id.toHexString();
        }
        return null;
    }
}
