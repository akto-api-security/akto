package com.akto.action;

import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class AddUserToAccountAction implements Action, ServletResponseAware, ServletRequestAware {

    protected HttpServletRequest servletRequest;
    protected HttpServletResponse servletResponse;
    private String signupInvitationCode;
    private String signupEmailId;

    public String getSignupInvitationCode() {
        return signupInvitationCode;
    }

    public void setSignupInvitationCode(String signupInvitationCode) {
        this.signupInvitationCode = signupInvitationCode;
    }

    public String getSignupEmailId() {
        return signupEmailId;
    }

    public void setSignupEmailId(String signupEmailId) {
        this.signupEmailId = signupEmailId;
    }

    public HttpServletRequest getServletRequest() {
        return servletRequest;
    }

    public HttpServletResponse getServletResponse() {
        return servletResponse;
    }

    @Override
    public String execute() throws Exception {
        BasicDBObject state = new BasicDBObject("signupInvitationCode", signupInvitationCode)
                .append("signupEmailId", signupEmailId);

        return HomeAction.redirectToAuth0(servletRequest, servletResponse, null, state);
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.servletRequest = request;
    }

    @Override
    public void setServletResponse(HttpServletResponse response) {
        this.servletResponse = response;
    }
}
