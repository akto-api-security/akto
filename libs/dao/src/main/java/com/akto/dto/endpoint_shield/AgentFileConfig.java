package com.akto.dto.endpoint_shield;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

@Getter
@Setter
public class AgentFileConfig {

    public static final String ID = "_id";
    public static final String AGENT = "agent";
    public static final String OS = "os";
    public static final String ROLE = "role";
    public static final String PATH = "path";
    public static final String ADDED_BY = "addedBy";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    @BsonId
    private ObjectId id;

    @BsonIgnore
    public String getIdHex() { return id != null ? id.toHexString() : null; }

    private String agent;
    private String os;   // darwin | linux | windows
    private String role; // login | credentials | mcp_config
    private String path;
    private String addedBy;
    private int createdTs;
    private int updatedTs;
}
