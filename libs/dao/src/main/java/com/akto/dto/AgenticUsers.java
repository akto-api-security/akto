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
    public static final String USER_ROLE = "userRole";
    public static final String TEAM_NAME = "teamName";
    public static final String LAST_UPDATED_AT = "lastUpdatedAt";
    public static final String LAST_UPDATED_BY = "lastUpdatedBy";

    public static final String DEVICE_TAGS = "deviceTags";

    private String userName;
    private String userEmail;
    private String userRole;
    private String teamName;
    private int lastUpdatedAt;
    private String lastUpdatedBy;
    private List<String> devices;

    // Generic key-value tags (e.g. Okta groups under key "group"). One entry per (key, value,
    // source) — see AgentUsersDao.upsertDeviceTags. Ported for the periodic Okta user-sync cron;
    // unrelated to the legacy teamName/userRole fields above. Multiple sources (or multiple
    // values from the same source) for the same key all coexist — this is the single source of
    // truth read directly wherever tags are used, no separate resolved field.
    private List<DeviceTag> deviceTags;
}
