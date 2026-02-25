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

    private String host;
    private List<String> paths;
    private boolean active;
    private String createdBy;
    private String updatedBy;
    private int createdTimestamp;
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
