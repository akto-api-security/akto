package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PlatformShieldConfig {

    public static final String MANIFEST_URL              = "manifestUrl";
    public static final String AUTO_UPDATE_ENABLED       = "autoUpdateEnabled";
    public static final String TARGET_VERSION            = "targetVersion";
    public static final String LATEST_VERSION            = "latestVersion";
    public static final String LATEST_VERSION_FETCHED_AT = "latestVersionFetchedAt";

    private String manifestUrl;
    private boolean autoUpdateEnabled = true;
    private String targetVersion;
    private String latestVersion;
    private long latestVersionFetchedAt;
}
