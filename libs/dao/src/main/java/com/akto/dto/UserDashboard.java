package com.akto.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserDashboard {

    public static final String SCREEN_NAME = "screenName";
    private String screenName;

    public static final String USER_ID = "userId";
    private int userId;

    public static final String LAYOUT = "layout";
    private String layout;
}
