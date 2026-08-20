package com.akto.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AgenticUsers {

    public static final String USER_NAME = "userName";
    public static final String USER_EMAIL = "userEmail";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
    public static final String LAST_UPDATED_BY = "lastUpdatedBy";

    public static final String DEVICE_TAGS = "deviceTags";
    public static final String CONNECTOR_ONLY = "connectorOnly";

    private String userName;
    private String userEmail;
    private int lastUpdatedAt;
    private String lastUpdatedBy;
    private List<String> devices;

    // Generic key-value tags (team, role, department, arbitrary Okta groups, ...).
    private List<DeviceTag> deviceTags;

    // True when there's no real endpoint-shield device behind this identity (e.g. a Claude
    // Inference Hooks actor) — keeps stored `devices` from being wiped when there's no live heartbeat.
    private boolean connectorOnly;
}
