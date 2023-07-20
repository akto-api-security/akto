package com.akto.action;

import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class AddUserToAccountAction implements Action, ServletResponseAware, ServletRequestAware {

    protected HttpServletRequest servletRequest;
    protected HttpServletResponse servletResponse;
    private String signupInvitationCode;
    private String signupEmailId;
    private String state;

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

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public String execute() throws Exception {
        if(state != null) {
            String s = new String(Base64.getDecoder().decode(state.getBytes(StandardCharsets.UTF_8)));
            return HomeAction.redirectToAuth0(servletRequest, servletResponse, null, s);
        }
        BasicDBObject stateObj = new BasicDBObject("signupInvitationCode", signupInvitationCode)
                .append("signupEmailId", signupEmailId);
        return HomeAction.redirectToAuth0(servletRequest, servletResponse, null, stateObj);

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
