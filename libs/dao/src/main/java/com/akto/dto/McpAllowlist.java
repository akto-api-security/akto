package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class McpAllowlist {

    private String name;
    public static final String NAME = "name";

    private String url;
    public static final String URL = "url";

    private String registryId;
    public static final String REGISTRY_ID = "registryId";

    private String addedBy;
    public static final String ADDED_BY = "addedBy";

    private int createdAt;
    public static final String CREATED_AT = "createdAt";

    private boolean manuallyAdded;
    public static final String MANUALLY_ADDED = "manuallyAdded";

    private Source source;
    public static final String SOURCE = "source";

    @Getter
    @AllArgsConstructor
    public enum Source {
        REGISTRY("Registry"), AUDIT_DATA("Audit data");

        private final String displayName;
    }

    public String getSourceDisplay() {
        return source != null ? source.getDisplayName() : null;
    }
}
