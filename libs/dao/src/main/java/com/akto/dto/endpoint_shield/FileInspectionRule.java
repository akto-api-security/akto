package com.akto.dto.endpoint_shield;

import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;

/**
 * One row in the Endpoint Shield "File Inspection" settings list.
 *
 * The user supplies just a path. The agent on each device stats the path and:
 *   - if it's a file, reads its content
 *   - if it's a directory, lists immediate children and reads each child file
 * When existenceOnly is true, the agent only records existence + metadata and
 * skips content reading.
 */
@Getter
@Setter
public class FileInspectionRule {

    public static final String ID = "_id";
    public static final String ACCOUNT_ID = "accountId";
    public static final String PATH = "path";
    public static final String EXISTENCE_ONLY = "existenceOnly";
    public static final String ADDED_BY = "addedBy";
    public static final String CREATED_TS = "createdTs";
    public static final String UPDATED_TS = "updatedTs";

    @BsonId
    private ObjectId id;
    private int accountId;

    @BsonIgnore
    public String getIdHex() { return id != null ? id.toHexString() : null; }

    private String path;
    private boolean existenceOnly;
    private int maxDepth; // 0 = immediate children only, -1 = unlimited recursion
    private String addedBy;
    private int createdTs;
    private int updatedTs;
}
