package com.akto.dto;

import java.util.List;

public class SignupUserInfo {

    User user;

    public SignupUserInfo() {}

    String companyName, teamName;
    boolean completedSignup = false;
    int formVersion = 1;

    public SignupUserInfo(User user, String companyName, String teamName, List<String> metrics, List<String> emailInvitations) {
        this.user = user;
        this.companyName = companyName;
        this.teamName = teamName;
        this.metrics = metrics;
        this.emailInvitations = emailInvitations;
    }

    List<String> metrics;


    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public List<String> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<String> metrics) {
        this.metrics = metrics;
    }

    public List<String> getEmailInvitations() {
        return emailInvitations;
    }

    public void setEmailInvitations(List<String> emailInvitations) {
        this.emailInvitations = emailInvitations;
    }

    List<String> emailInvitations;

}
