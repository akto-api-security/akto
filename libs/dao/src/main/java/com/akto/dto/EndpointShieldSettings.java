package com.akto.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
public class EndpointShieldSettings {

    public static final String PLATFORM_MACOS_MDM      = "macos_mdm";
    public static final String PLATFORM_WINDOWS_MDM    = "windows_mdm";
    public static final String PLATFORM_MACOS_DIRECT   = "macos_direct";
    public static final String PLATFORM_WINDOWS_DIRECT = "windows_direct";

    public static final List<String> ALL_PLATFORMS = Arrays.asList(
        PLATFORM_MACOS_MDM,
        PLATFORM_WINDOWS_MDM,
        PLATFORM_MACOS_DIRECT,
        PLATFORM_WINDOWS_DIRECT
    );

    public static final String PLATFORMS = "platforms";

    private Map<String, PlatformShieldConfig> platforms = new HashMap<>();
}
