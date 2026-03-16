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
    private String id; // same as uniqueUserId in user_analysis_data
    private String userName;
    private String userEmail;
    private String userRole;
    private String teamName;
}
