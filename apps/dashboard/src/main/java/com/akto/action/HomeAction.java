package com.akto.action;

import static com.akto.action.SignupAction.BUSINESS_EMAIL_URI;
import static com.akto.action.SignupAction.CHECK_INBOX_URI;
import static com.akto.action.SignupAction.SSO_URL;
import static com.akto.action.SignupAction.TEST_EDITOR_URL;
import static com.akto.filter.UserDetailsFilter.LOGIN_URI;

import com.akto.dao.SSOConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.DashboardMode;
import com.akto.utils.Auth0;
import com.akto.utils.GithubLogin;
import com.akto.utils.JWT;
import com.akto.utils.OktaLogin;
import com.auth0.AuthorizeUrl;
import com.auth0.SessionUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.apache.struts2.interceptor.SessionAware;

// This is the first action that is triggered when the webpage is first fetched
// Basically sets the access token from the session (saved by UserDetailsFilter)
// Then the accessToken is accessed by login.jsp (the page being requested)
// in ${accessToken} field.
public class HomeAction implements Action, SessionAware, ServletResponseAware, ServletRequestAware {

    private static final LoggerMaker logger = new LoggerMaker(CustomAuthTypeAction.class, LogDb.DASHBOARD);

    protected HttpServletResponse servletResponse;

    @Override
    public void setServletResponse(HttpServletResponse httpServletResponse) {
        this.servletResponse = httpServletResponse;
    }

    protected HttpServletRequest servletRequest;

    @Override
    public String execute() {

        try {
            String nodeEnv = System.getenv("NODE_ENV");
            servletRequest.setAttribute("nodeEnv", nodeEnv != null ? nodeEnv : "production");    
        } catch(Exception e){
        }

        servletRequest.setAttribute("isSaas", InitializerListener.isSaas);
        if(DashboardMode.isOnPremDeployment()){
            if (GithubLogin.getGithubUrl() != null) {
                servletRequest.setAttribute("githubAuthUrl", GithubLogin.getGithubUrl() + "/login/oauth/authorize?client_id=" + GithubLogin.getClientId() + "&scope=user&state=1000000");
                servletRequest.setAttribute("activeSso", Config.ConfigType.GITHUB);
            }
    
            if (OktaLogin.getAuthorisationUrl() != null) {
                servletRequest.setAttribute("oktaAuthUrl", OktaLogin.getAuthorisationUrl());
                servletRequest.setAttribute("activeSso", Config.ConfigType.OKTA);
            }
    
            if (SSOConfigsDao.getSAMLConfigByAccountId(1000000, Config.ConfigType.AZURE) != null) {
                servletRequest.setAttribute("activeSso", Config.ConfigType.AZURE);
            }
    
            if (SSOConfigsDao.getSAMLConfigByAccountId(1000000, Config.ConfigType.GOOGLE_SAML) != null) {
                servletRequest.setAttribute("activeSso", Config.ConfigType.GOOGLE_SAML);
            }
        }
        
        if (InitializerListener.aktoVersion != null && InitializerListener.aktoVersion.contains("akto-release-version")) {
            servletRequest.setAttribute("AktoVersionGlobal", "");
        } else {
            servletRequest.setAttribute("AktoVersionGlobal", InitializerListener.aktoVersion);
        }
        logger.debug("in Home::execute: settings IS_SAAS to {}", InitializerListener.isSaas);
        if(DashboardMode.isSaasDeployment()) {
            if(accessToken != null && servletRequest.getAttribute("username") == null) {
                try {
                    Jws<Claims> jws = JWT.parseJwt(accessToken, "");
                    String username = jws.getBody().get("username").toString();
                    User user = UsersDao.instance.findOne(Filters.eq(User.LOGIN, username));
                    if(user != null && user.getRefreshTokens() != null && !user.getRefreshTokens().isEmpty()){
                        logger.debug("User has refresh tokens, setting up window vars");
                        ProfileAction.executeMeta1(null, user, servletRequest, servletResponse);
                    }
                }catch (Exception e){
                    logger.debug("Access token expired, unable to set window vars", e);
                }
            }
            return redirectToAuth0(servletRequest, servletResponse, accessToken, new BasicDBObject());
        }

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
            servletRequest.getRequestURI().contains(TEST_EDITOR_URL) || 
            servletRequest.getRequestURI().contains(SSO_URL)
            ) {
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
        String at = accessToken;
        if (at == null) {
            at = (String) SessionUtils.get(servletRequest, "accessToken");
        }
        try{
            JWT.parseJwt(at, "");
        } catch (Exception e){
            return false;
        }
        return true;
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
