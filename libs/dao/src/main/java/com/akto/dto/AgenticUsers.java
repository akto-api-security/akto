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

    private String userName;
    private String userEmail;
    private int lastUpdatedAt;
    private String lastUpdatedBy;
    private List<String> devices;

    // Generic key-value tags (team, role, department, arbitrary Okta groups, ...).
    private List<DeviceTag> deviceTags;
}
