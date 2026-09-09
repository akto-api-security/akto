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
    public static final String USER_ID = "userId";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
    public static final String LAST_UPDATED_BY = "lastUpdatedBy";

    public static final String DEVICE_TAGS = "deviceTags";

    private String userName;
    private String userEmail;
    // Raw id from whatever external identity source populated this row (e.g. the Microsoft
    // Graph AAD object id for Copilot Studio users) — generic and connector-agnostic, not
    // specific to any one ai-agent source.
    private String userId;
    private int lastUpdatedAt;
    private String lastUpdatedBy;
    private List<String> devices;

    // Generic key-value tags (team, role, department, arbitrary Okta groups, ...).
    private List<DeviceTag> deviceTags;
}
