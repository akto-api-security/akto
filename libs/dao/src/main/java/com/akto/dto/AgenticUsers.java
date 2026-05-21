package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AgenticUsers {

    public static final String ID = "_id";
    public static final String USER_NAME = "userName";
    public static final String USER_EMAIL = "userEmail";
    public static final String USER_ROLE = "userRole";
    public static final String TEAM_NAME = "teamName";

    private String id; // same as username
    private String userName;
    private String userEmail;
    private String userRole;
    private String teamName;
}
