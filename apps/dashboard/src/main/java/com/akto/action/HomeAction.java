package com.akto.action;

import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.SessionAware;
import java.util.Map;

// This is the first action that is triggered when the webpage is first fetched
// Basically sets the access token from the session (saved by UserDetailsFilter)
// Then the accessToken is accessed by login.jsp (the page being requested)
// in ${accessToken} field.
public class HomeAction implements Action, SessionAware {
    @Override
    public String execute() {
        return "SUCCESS";
    }

    private String accessToken;

    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public void setSession(Map<String, Object> session) {
        this.accessToken = (String) session.get(AccessTokenAction.ACCESS_TOKEN_HEADER_NAME);
    }
}
