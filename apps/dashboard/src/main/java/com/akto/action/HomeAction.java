package com.akto.action;

import com.akto.dao.UsersDao;
import com.akto.dto.Config;
import com.akto.dto.User;
import com.akto.dto.sso.SAMLConfig;
import com.akto.listener.InitializerListener;
import com.akto.utils.*;
import com.akto.util.DashboardMode;
import com.akto.utils.sso.CustomSamlSettings;
import com.auth0.AuthorizeUrl;
import com.auth0.SessionUtils;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.onelogin.saml2.authn.AuthnRequest;
import com.onelogin.saml2.settings.Saml2Settings;
import com.opensymphony.xwork2.Action;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.apache.struts2.interceptor.SessionAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

import static com.akto.action.SignupAction.*;
import static com.akto.filter.UserDetailsFilter.LOGIN_URI;

// This is the first action that is triggered when the webpage is first fetched
// Basically sets the access token from the session (saved by UserDetailsFilter)
// Then the accessToken is accessed by login.jsp (the page being requested)
// in ${accessToken} field.
public class HomeAction implements Action, SessionAware, ServletResponseAware, ServletRequestAware {

    protected HttpServletResponse servletResponse;
    private static final Logger logger = LoggerFactory.getLogger(LoginAction.class);

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
        if(DashboardMode.isOnPremDeployment()) {
            if (GithubLogin.getGithubUrl() != null) {
                servletRequest.setAttribute("githubAuthUrl", GithubLogin.getGithubUrl() + "/login/oauth/authorize?client_id=" + GithubLogin.getClientId() + "&scope=user&state=1000000");
                servletRequest.setAttribute("activeSso", Config.ConfigType.GITHUB);
            } else if (OktaLogin.getAuthorisationUrl() != null) {
                servletRequest.setAttribute("oktaAuthUrl", OktaLogin.getAuthorisationUrl());
                servletRequest.setAttribute("activeSso", Config.ConfigType.OKTA);
            } else if (Config.AzureConfig.getSSOConfigByAccountId(1000000, Config.ConfigType.AZURE) != null) {
                try {
                    SAMLConfig samlConfig = Config.AzureConfig.getSSOConfigByAccountId(1000000, Config.ConfigType.AZURE);
                    Saml2Settings samlSettings = CustomSamlSettings.getSamlSettings(samlConfig);
                    String samlRequestXml = new AuthnRequest(samlSettings).getAuthnRequestXml();

                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    Deflater deflater = new Deflater(Deflater.DEFLATED, true);
                    DeflaterOutputStream deflaterOutputStream = new DeflaterOutputStream(byteArrayOutputStream, deflater);
                    deflaterOutputStream.write(samlRequestXml.getBytes(StandardCharsets.UTF_8));
                    deflaterOutputStream.close();
                    String base64Encoded = Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
                    String urlEncoded = URLEncoder.encode(base64Encoded, "UTF-8");

                    servletRequest.setAttribute("azureAuthUrl", samlConfig.getLoginUrl() + "?SAMLRequest=" + urlEncoded + "&RelayState=" + 1000000);
                    servletRequest.setAttribute("activeSso", Config.ConfigType.AZURE);
                } catch (Exception e) {
                    e.printStackTrace();
                    logger.error(e.getMessage());
                }
            } else if (Config.GoogleConfig.getSSOConfigByAccountId(1000000, Config.ConfigType.GOOGLE_SAML) != null) {
                Config.GoogleConfig googleSamlConfig = (Config.GoogleConfig) Config.GoogleConfig.getSSOConfigByAccountId(1000000, Config.ConfigType.GOOGLE_SAML);
                servletRequest.setAttribute("googleSamlAuthUrl", googleSamlConfig.getAuthURI());
                servletRequest.setAttribute("activeSso", Config.ConfigType.GOOGLE_SAML);
            }
        }
        if (InitializerListener.aktoVersion != null && InitializerListener.aktoVersion.contains("akto-release-version")) {
            servletRequest.setAttribute("AktoVersionGlobal", "");
        } else {
            servletRequest.setAttribute("AktoVersionGlobal", InitializerListener.aktoVersion);
        }
        logger.info("in Home::execute: settings IS_SAAS to " + InitializerListener.isSaas);
        if(DashboardMode.isSaasDeployment()){
            if(accessToken != null && servletRequest.getAttribute("username") == null) {
                try {
                    Jws<Claims> jws = JWT.parseJwt(accessToken, "");
                    String username = jws.getBody().get("username").toString();
                    User user = UsersDao.instance.findOne(Filters.eq(User.LOGIN, username));
                    if(user != null && user.getRefreshTokens() != null && !user.getRefreshTokens().isEmpty()){
                        logger.info("User has refresh tokens, setting up window vars");
                        ProfileAction.executeMeta1(null, user, servletRequest, servletResponse);
                    }
                }catch (Exception e){
                    logger.info("Access token expired, unable to set window vars", e);
                }
            }
            return redirectToAuth0(servletRequest, servletResponse, accessToken, new BasicDBObject());
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
