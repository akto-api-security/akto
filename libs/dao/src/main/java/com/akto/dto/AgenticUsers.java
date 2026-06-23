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
    public static final String TEAM_SOURCE = "teamSource";
    public static final String ROLE_SOURCE = "roleSource";
    public static final String SSO_TEAM_NAME = "ssoTeamName";
    public static final String SSO_USER_ROLE = "ssoUserRole";
    public static final String SOURCE_SSO = "sso";
    public static final String SOURCE_MANUAL = "manual";
    public static final String DEVICES = "devices";

    private String userName;
    private String userEmail;
    private String userRole;
    private String teamName;
    private int lastUpdatedAt;
    private String lastUpdatedBy;
    private List<String> devices;
    // "sso" or "manual" — SSO writes are skipped when source is "manual".
    private String teamSource;
    private String roleSource;
    // Last values provided by SSO — always kept up-to-date regardless of manual pin.
    // Used to restore the effective value immediately when an admin clears a manual override.
    private String ssoTeamName;
    private String ssoUserRole;
}
