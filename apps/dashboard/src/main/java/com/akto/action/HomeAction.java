package com.akto.action;

import com.akto.listener.InitializerListener;
import com.akto.utils.Auth0;
import com.akto.utils.DashboardMode;
import com.auth0.AuthorizeUrl;
import com.auth0.SessionUtils;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.apache.struts2.interceptor.SessionAware;
import org.jetbrains.annotations.Nullable;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.akto.action.SignupAction.*;
import static com.akto.filter.UserDetailsFilter.LOGIN_URI;

// This is the first action that is triggered when the webpage is first fetched
// Basically sets the access token from the session (saved by UserDetailsFilter)
// Then the accessToken is accessed by login.jsp (the page being requested)
// in ${accessToken} field.
public class HomeAction implements Action, SessionAware, ServletResponseAware, ServletRequestAware {

    protected HttpServletResponse servletResponse;

    @Override
    public void setServletResponse(HttpServletResponse httpServletResponse) {
        this.servletResponse = httpServletResponse;
    }

    protected HttpServletRequest servletRequest;

    public String verifyEmail(){
        return "SUCCESS";
    }
    @Override
    public String execute() {

        servletRequest.setAttribute("isSaas", InitializerListener.isSaas);
        if (InitializerListener.aktoVersion != null && InitializerListener.aktoVersion.contains("akto-release-version")) {
            servletRequest.setAttribute("AktoVersionGlobal", "akto-release-version");
        } else {
            servletRequest.setAttribute("AktoVersionGlobal", InitializerListener.aktoVersion);
        }
        System.out.println("in Home::execute: settings IS_SAAS to " + InitializerListener.isSaas);
        if(DashboardMode.isSaasDeployment()){
            //Use Auth0
            return redirectToAuth0(servletRequest, servletResponse, accessToken, new BasicDBObject());
//            if (SUCCESS1 != null) return SUCCESS1;
//            System.out.println("Executed home action for auth0");
//            return "SUCCESS";
        }
        // Use existing flow

        return "SUCCESS";
    }

    public static String redirectToAuth0(HttpServletRequest servletRequest, HttpServletResponse servletResponse, String accessToken,BasicDBObject state) {
        return redirectToAuth0(servletRequest, servletResponse, accessToken, state.toString());
    }
    public static String redirectToAuth0(HttpServletRequest servletRequest, HttpServletResponse servletResponse, String accessToken,String state) {

        if(checkIfAccessTokenExists(servletRequest, accessToken)) {
            if (!servletRequest.getRequestURI().startsWith(LOGIN_URI)) {
                return "SUCCESS";
            }
        }

        if(servletRequest.getRequestURI().equals(CHECK_INBOX_URI) ||
            servletRequest.getRequestURI().contains(BUSINESS_EMAIL_URI) ||
            servletRequest.getRequestURI().contains(TEST_EDITOR_URL)) {
            return "SUCCESS";
        }

        String redirectUri = servletRequest.getScheme() + "://" + servletRequest.getServerName();
        if ((servletRequest.getScheme().equals("http") && servletRequest.getServerPort() != 80) ||
                (servletRequest.getScheme().equals("https") && servletRequest.getServerPort() != 443)) {
            redirectUri += ":" + servletRequest.getServerPort();
        }
        redirectUri += "/callback";

        String authorizeUrlStr;
        try {
            AuthorizeUrl authorizeUrl = Auth0.getInstance().buildAuthorizeUrl(servletRequest, servletResponse, redirectUri)
                    .withScope("openid profile email");

            String stateStr = java.util.Base64.getEncoder().encodeToString(state.getBytes(StandardCharsets.UTF_8));
            authorizeUrl.withState(stateStr);
            authorizeUrlStr = authorizeUrl.build();
            servletResponse.sendRedirect(authorizeUrlStr);
        } catch (Exception e) {
            System.err
                    .println("Couldn't create the AuthenticationController instance. Check the configuration.");
        }
        return null;
    }

    private static boolean checkIfAccessTokenExists(HttpServletRequest servletRequest, String accessToken) {
        return accessToken != null || SessionUtils.get(servletRequest, "accessToken") != null;
    }

    public String error() {
        return SUCCESS.toUpperCase();
    }

    private String accessToken;
    private String signupInvitationCode;
    private String signupEmailId;

    // to prevent redirect_uri not found warning
    public void setRedirect_uri(String redirect_uri) {
    }

    public String getAccessToken() {
        return accessToken;
    }

    @Override
    public void setSession(Map<String, Object> session) {
        this.accessToken = (String) session.get(AccessTokenAction.ACCESS_TOKEN_HEADER_NAME);
    }

    public void setSignupInvitationCode(String signupInvitationCode) {
        this.signupInvitationCode = signupInvitationCode;
    }

    public String getSignupInvitationCode() {
        return signupInvitationCode;
    }

    public String getSignupEmailId() {
        return signupEmailId;
    }

    public void setSignupEmailId(String signupEmailId) {
        this.signupEmailId = signupEmailId;
    }

    @Override
    public void setServletRequest(HttpServletRequest request) {
        this.servletRequest = request;
    }
}
