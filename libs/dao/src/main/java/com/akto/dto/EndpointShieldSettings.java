package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EndpointShieldSettings {

    private int id; // accountId

    public static final String MANIFEST_URL = "manifestUrl";
    private String manifestUrl;

    public static final String AUTO_UPDATE_ENABLED = "autoUpdateEnabled";
    private boolean autoUpdateEnabled;

    // null = no forced version; set = all agents must reach exactly this version
    public static final String TARGET_VERSION = "targetVersion";
    private String targetVersion;

    // Cached from manifest, refreshed server-side with TTL
    public static final String LATEST_VERSION = "latestVersion";
    private String latestVersion;

    public static final String LATEST_VERSION_FETCHED_AT = "latestVersionFetchedAt";
    private long latestVersionFetchedAt; // epoch ms
}
