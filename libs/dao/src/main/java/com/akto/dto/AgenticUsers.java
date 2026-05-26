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

    private String userName;
    private String userEmail;
    private String userRole;
    private String teamName;
    private int lastUpdatedAt;
    private String lastUpdatedBy;
    private List<String> devices;
}
