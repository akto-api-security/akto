// Keep in sync with the identical copy at cyborg/libs/dao/src/main/java/com/akto/dto/config_field_policy/ConfigFieldPolicy.java (same filename) — both repos read/write the same MongoDB collection independently.
package com.akto.dto.config_field_policy;

import java.util.List;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.types.ObjectId;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ConfigFieldPolicy {
    public static final String COLLECTION_NAME = "config_field_policies";

    public static final String ID = "_id";
    public static final String POLICY_NAME = "policyName";
    public static final String DESCRIPTION = "description";
    public static final String STATUS = "status";
    public static final String TOOL_NAME = "toolName";
    public static final String FIELD_PATH = "fieldPath";
    public static final String ENFORCED_VALUE_JSON = "enforcedValueJson";
    public static final String DEVICES = "devices";
    public static final String CREATED_AT = "createdAt";
    public static final String CREATED_BY = "createdBy";
    public static final String UPDATED_AT = "updatedAt";
    public static final String UPDATED_BY = "updatedBy";

    private ObjectId id;
    private String policyName;
    private String description;
    private String status;
    private String toolName;
    private String fieldPath;
    private String enforcedValueJson;
    // Device IDs this policy applies to. Empty/null = apply to all devices.
    private List<String> devices;
    private int createdAt;
    private String createdBy;
    private int updatedAt;
    private String updatedBy;

    public ConfigFieldPolicy(String policyName, String description, String status, String toolName, String fieldPath, String enforcedValueJson) {
        this.policyName = policyName;
        this.description = description;
        this.status = status;
        this.toolName = toolName;
        this.fieldPath = fieldPath;
        this.enforcedValueJson = enforcedValueJson;
    }

    @BsonIgnore
    public String getHexId() {
        return id != null ? id.toHexString() : null;
    }
}
