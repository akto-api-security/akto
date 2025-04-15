package com.akto.action;

import static com.akto.dao.MCollection.SET;
import static com.mongodb.client.model.Filters.eq;

import com.akto.dao.AccountsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.SignupDao;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Account;
import com.akto.dto.Config;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.CustomRole;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.SignupInfo;
import com.akto.dto.SignupUserInfo;
import com.akto.dto.User;
import com.akto.dto.billing.Organization;
import com.akto.dto.sso.SAMLConfig;
import com.akto.listener.InitializerListener;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.mixpanel.AktoMixpanel;
import com.akto.notifications.slack.NewUserJoiningAlert;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.notifications.slack.SlackSender;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.akto.util.Util;
import com.akto.util.http_request.CustomHttpRequest;
import com.akto.utils.Auth0;
import com.akto.utils.GithubLogin;
import com.akto.utils.JWT;
import com.akto.utils.OktaLogin;
import com.akto.utils.billing.OrganizationUtils;
import com.akto.utils.sso.CustomSamlSettings;
import com.akto.utils.sso.SsoUtils;
import com.auth0.Tokens;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.onelogin.saml2.Auth;
import com.onelogin.saml2.settings.Saml2Settings;
import com.opensymphony.xwork2.Action;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.oauth.OAuthV2AccessRequest;
import com.slack.api.methods.request.users.UsersIdentityRequest;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import com.slack.api.methods.response.users.UsersIdentityResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.conversions.Bson;
import org.json.JSONObject;

public class SignupAction implements Action, ServletResponseAware, ServletRequestAware {

    private static final LoggerMaker logger = new LoggerMaker(SignupAction.class, LogDb.DASHBOARD);
    public static final String CHECK_INBOX_URI = "/check-inbox";
    public static final String BUSINESS_EMAIL_URI = "/business-email";
    public static final String TEST_EDITOR_URL = "/tools/test-editor";
    public static final String SSO_URL = "/sso-login";
    public static final String ACCESS_DENIED_ERROR = "access_denied";
    public static final String VERIFY_EMAIL_ERROR = "VERIFY_EMAIL";
    public static final String BUSINESS_EMAIL_REQUIRED_ERROR = "BUSINESS_EMAIL_REQUIRED";
    public static final String ERROR_STR = "error";
    public static final String ERROR_DESCRIPTION = "error_description";

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    String code;
    String state;
    public String registerViaSlack() {
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();
        String codeFromSlack = code;
        code = "err";
        logger.info(code+ " " +state);

        Config.SlackConfig aktoSlackConfig = (Config.SlackConfig) ConfigsDao.instance.findOne("_id", "SLACK-ankush");

        if(aktoSlackConfig == null) {
            aktoSlackConfig = (Config.SlackConfig) ConfigsDao.instance.findOne("_id", "SLACK-ankush");
        }

        OAuthV2AccessRequest request = OAuthV2AccessRequest.builder()
                .clientId(aktoSlackConfig.getClientId())
                .code(codeFromSlack)
                .clientSecret(aktoSlackConfig.getClientSecret())
                .redirectUri(aktoSlackConfig.getRedirect_url()).build();

        try {
            OAuthV2AccessResponse response = Slack.getInstance().methods().oauthV2Access(request);
            String error = response.getError();

            if (error != null && error.length() > 0) {
                code = error;
            } else {

                OAuthV2AccessResponse.AuthedUser authedUser = response.getAuthedUser();

                String enterpriseName = "", enterpriseId = "", teamName = "", teamId = "";
                if (response.getEnterprise() != null) {
                    enterpriseId = response.getEnterprise().getId();
                    enterpriseName = response.getEnterprise().getName();
                }

                if (response.getTeam() != null) {
                    teamId = response.getTeam().getId();
                    teamName = response.getTeam().getName();
                }

                SignupInfo.SlackSignupInfo info =
                    new SignupInfo.SlackSignupInfo(
                        aktoSlackConfig.getId(),
                        response.isOk(),
                        response.getAppId(),
                        authedUser.getAccessToken(),
                        authedUser.getId(),
                        authedUser.getScope(),
                        authedUser.getTokenType(),
                        response.getScope(),
                        response.getTokenType(),
                        response.getAccessToken(),
                        response.getBotUserId(),
                        teamName,
                        teamId,
                        enterpriseName,
                        enterpriseId,
                        response.isEnterpriseInstall()
                );


                UsersIdentityRequest usersIdentityRequest = UsersIdentityRequest.builder().token(authedUser.getAccessToken()).build();
                UsersIdentityResponse usersIdentityResponse = Slack.getInstance().methods(info.getAuthedUserAccessToken()).usersIdentity(usersIdentityRequest);

                if (usersIdentityResponse.isOk()) {
                    UsersIdentityResponse.User slackUser = usersIdentityResponse.getUser();
                    String userName = slackUser.getName();
                    String userEmail = slackUser.getEmail();
                    String userSlackId = slackUser.getId();

                    if(userSlackId != null && !userSlackId.equalsIgnoreCase(info.getAuthedUserId())) {
                        throw new IllegalStateException("user id mismatch between slack identity and token authed user");
                    }

                    code = "";
                    createUserAndRedirect(userEmail, userName, info, 0, Config.ConfigType.SLACK.toString());
                } else {
                    code = usersIdentityResponse.getError();
                }
            }

        } catch (IOException e) {
            code = e.getMessage();
        } catch (SlackApiException e) {
            code = e.getResponse().message();
        } catch (Exception e) {
            e.printStackTrace();
            code = e.getMessage();
        } finally {
            if (code.length() > 0) {
                return Action.ERROR.toUpperCase();
            } else {
                return Action.SUCCESS.toUpperCase();
            }
        }

    }

    private BasicDBObject getParsedState(){
        return BasicDBObject.parse(new String(java.util.Base64.getDecoder().decode(state)));
    }

    private String getErrorPageRedirectUrl(String url, BasicDBObject parsedState){
        url +=  parsedState != null && parsedState.containsKey("signupInvitationCode")? "?state=" + state : "";
        return url;
    }

    public String registerViaAuth0() throws Exception {
        logger.infoAndAddToDb("registerViaAuth0 called");
        String error = servletRequest.getParameter(ERROR_STR);
        String errorDescription = servletRequest.getParameter(ERROR_DESCRIPTION);
        BasicDBObject parsedState = getParsedState();
        if(StringUtils.isNotEmpty(error) || StringUtils.isNotEmpty(errorDescription)) {
            if(error.equals(ACCESS_DENIED_ERROR)) {
                switch (errorDescription){
                    case VERIFY_EMAIL_ERROR:
                        servletResponse.sendRedirect(getErrorPageRedirectUrl(CHECK_INBOX_URI, parsedState));
                        return SUCCESS;
                    case BUSINESS_EMAIL_REQUIRED_ERROR:
                        servletResponse.sendRedirect(getErrorPageRedirectUrl(BUSINESS_EMAIL_URI, parsedState));
                        return SUCCESS;
                    default:
                        return ERROR;
                }
            }
        }
        Tokens tokens = Auth0.getInstance().handle(servletRequest, servletResponse);
        String accessToken = tokens.getAccessToken();
        String refreshToken = tokens.getRefreshToken();
        String idToken = tokens.getIdToken();
        String[] split_string = idToken.split("\\.");
        String base64EncodedBody = split_string[1];

        JwkProvider provider = new UrlJwkProvider("https://" + Auth0.getDomain() + "/");
        DecodedJWT jwt = com.auth0.jwt.JWT.decode(idToken);
        Jwk jwk = provider.get(jwt.getKeyId());

        Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

        JWTVerifier verifier = com.auth0.jwt.JWT.require(algorithm)
                .withIssuer("https://" + Auth0.getDomain() + "/")
                .build();

        jwt = verifier.verify(idToken);

        String body = new String(java.util.Base64.getUrlDecoder().decode(base64EncodedBody));
        JSONObject jsonBody = new JSONObject(body);
        logger.info(jsonBody.toString());
        String email = jsonBody.getString("email");
        String name = jsonBody.getString("name");

        SignupInfo.Auth0SignupInfo auth0SignupInfo = new SignupInfo.Auth0SignupInfo(accessToken, refreshToken,name, email);
        shouldLogin = "true";
        User user = UsersDao.instance.findOne(new BasicDBObject(User.LOGIN, email));
        if(user != null && user.getSignupInfoMap() != null && !user.getSignupInfoMap().containsKey(Config.ConfigType.AUTH0.name())){
            BasicDBObject setQ = new BasicDBObject(User.SIGNUP_INFO_MAP + "." +Config.ConfigType.AUTH0.name(), auth0SignupInfo);
            UsersDao.instance.getMCollection().updateOne(eq(User.LOGIN, email), new BasicDBObject(SET, setQ));
        }
        String decodedState = new String(java.util.Base64.getDecoder().decode(state));
        parsedState = BasicDBObject.parse(decodedState);

        if(parsedState.containsKey("signupInvitationCode")){
            // User clicked on signup link
            // Case 1: New user
            // Case 2: Existing user - Just add this new account to this user
            String signupInvitationCode = parsedState.getString("signupInvitationCode");
            Bson filter = Filters.eq(PendingInviteCode.INVITE_CODE, signupInvitationCode);
            PendingInviteCode pendingInviteCode = PendingInviteCodesDao.instance.findOne(filter);
            if (pendingInviteCode != null && pendingInviteCode.getInviteeEmailId().equals(email)) {
                PendingInviteCodesDao.instance.getMCollection().deleteOne(filter);
                if(user != null){
                    AccountAction.addUserToExistingAccount(email, pendingInviteCode.getAccountId());
                }
                createUserAndRedirect(email, name, auth0SignupInfo, pendingInviteCode.getAccountId(), Config.ConfigType.AUTH0.toString(), pendingInviteCode.getInviteeRole());

                return SUCCESS.toUpperCase();
            } else if(pendingInviteCode == null){
                // invalid code
                code = "Please ask admin to invite you!";
                return ERROR.toUpperCase();
            } else {
                // invalid email
                code = "This invitation doesn't belong to you!";
                return ERROR.toUpperCase();
            }

        }
        createUserAndRedirect(email, name, auth0SignupInfo, 0, Config.ConfigType.AUTH0.toString());
        code = "";
        logger.info("Executed registerViaAuth0");
        return SUCCESS.toUpperCase();
    }

    public String registerViaGoogle() {
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();
        logger.info(code + " " + state);

        String codeFromGoogle = code;
        code = "err";

        Config.GoogleConfig aktoGoogleConfig = (Config.GoogleConfig) ConfigsDao.instance.findOne("_id", "GOOGLE-ankush");
        if (aktoGoogleConfig == null) {
            Config.GoogleConfig newConfig = new Config.GoogleConfig();
            //Inserting blank config won't work, need to fill in Config manually in db
            ConfigsDao.instance.insertOne(newConfig);
            aktoGoogleConfig = (Config.GoogleConfig) ConfigsDao.instance.findOne("_id", "GOOGLE-ankush");
        }


        GoogleClientSecrets clientSecrets = new GoogleClientSecrets();
        GoogleClientSecrets.Details details = new GoogleClientSecrets.Details();
        details.setAuthUri(aktoGoogleConfig.getAuthURI());
        details.setClientId(aktoGoogleConfig.getClientId());
        details.setClientSecret(aktoGoogleConfig.getClientSecret());
        details.setTokenUri(aktoGoogleConfig.getTokenURI());
        clientSecrets.setWeb(details);

        GoogleTokenResponse tokenResponse;
        try {
            tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    new NetHttpTransport(),
                    JacksonFactory.getDefaultInstance(),
                    clientSecrets.getDetails().getTokenUri(),
                    clientSecrets.getDetails().getClientId(),
                    clientSecrets.getDetails().getClientSecret(),
                    codeFromGoogle,
                    InitializerListener.subdomain)
                    .execute();

            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            GoogleIdToken.Payload payload = tokenResponse.parseIdToken().getPayload();

            String username = (String) payload.get("name");
            String userEmail = payload.getEmail();

            SignupInfo.GoogleSignupInfo signupInfo = new SignupInfo.GoogleSignupInfo(aktoGoogleConfig.getId(), accessToken, refreshToken, tokenResponse.getExpiresInSeconds());
            shouldLogin = "true";
            createUserAndRedirect(userEmail, username, signupInfo, 0, Config.ConfigType.GOOGLE.toString());
            code = "";
        } catch (IOException e) {
            code = "Please login again";
            return ERROR.toUpperCase();

        }
        return SUCCESS.toUpperCase();

    };

    String password;
    String email;
    String invitationCode;

    public String registerViaEmail() {
        code = "";
        if (password != null ) {
            code = validatePassword(password);
            if (code != null) return ERROR.toUpperCase();
        } else {
            code = "Password can't be empty";
            return ERROR.toUpperCase();
        }
        int invitedToAccountId = 0;
        String inviteeRole = null;
        if (!invitationCode.isEmpty()) {
            Jws<Claims> jws;
            try {
                jws = JWT.parseJwt(invitationCode, "");
            } catch (Exception e) {
                code = "Ask admin to invite you";
                return ERROR.toUpperCase();
            }

            String emailFromJwt = jws.getBody().get("email").toString();
            if (!emailFromJwt.equals(email)) {
                code = "Ask admin to invite you";
                return ERROR.toUpperCase();
            }

            Bson filter = Filters.eq(PendingInviteCode.INVITE_CODE, invitationCode);
            PendingInviteCode pendingInviteCode = PendingInviteCodesDao.instance.findOne(filter);

            if (pendingInviteCode == null) {
                code = "Ask admin to invite you. If you are already a user, please click on login";
                return ERROR.toUpperCase();
            }

            // deleting the invitation code
            PendingInviteCodesDao.instance.getMCollection().deleteOne(filter);
            invitedToAccountId = pendingInviteCode.getAccountId();
            inviteeRole = pendingInviteCode.getInviteeRole();

        } else {
            if (!InitializerListener.isSaas) {
                long countUsers = UsersDao.instance.getMCollection().countDocuments();
                if (countUsers > 0) {
                    code = "Ask admin to invite you";
                    return ERROR.toUpperCase();
                }
            }
        }
        User userExists = UsersDao.instance.findOne("login", email);
        SignupInfo.PasswordHashInfo signupInfo;
        if (userExists != null) {
            if (invitationCode.isEmpty()) {
                code = "This user already exists.";
                return ERROR.toUpperCase();
            } else {
                signupInfo = (SignupInfo.PasswordHashInfo) userExists.getSignupInfoMap().get(Config.ConfigType.PASSWORD + "-ankush");
                String salt = signupInfo.getSalt();
                String passHash = Integer.toString((salt + password).hashCode());
                if (!passHash.equals(signupInfo.getPasshash())) {
                    return Action.ERROR.toUpperCase();
                }

            }
        } else {
            String salt = "39yu";
            String passHash = Integer.toString((salt + password).hashCode());
            signupInfo = new SignupInfo.PasswordHashInfo(passHash, salt);
        }

        try {
            shouldLogin = "true";
            createUserAndRedirect(email, email, signupInfo, invitedToAccountId, "email", inviteeRole);
        } catch (IOException e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String registerViaGithub() {
        logger.info("registerViaGithub");
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();
        GithubLogin ghLoginInstance = GithubLogin.getInstance();
        if (ghLoginInstance == null) {
            return ERROR.toUpperCase();
        }
        logger.info("Found github instance");
        Config.GithubConfig githubConfig = GithubLogin.getInstance().getGithubConfig();
        if (githubConfig == null) {
            return ERROR.toUpperCase();
        }
        logger.info("Found github configuration");
        BasicDBObject params = new BasicDBObject();
        params.put("client_id", githubConfig.getClientId());
        params.put("client_secret", githubConfig.getClientSecret());
        params.put("code", this.code);
        params.put("scope", "user");
        logger.info("Github code length: {}", this.code.length());
        try {
            String githubUrl = githubConfig.getGithubUrl();
            if (StringUtils.isEmpty(githubUrl)) githubUrl = "https://github.com";

            String githubApiUrl = githubConfig.getGithubApiUrl();
            if (StringUtils.isEmpty(githubApiUrl)) githubApiUrl = "https://api.github.com";

            if (githubApiUrl.endsWith("/")) githubApiUrl = githubApiUrl.substring(0, githubApiUrl.length() - 1);
            if (githubUrl.endsWith("/")) githubUrl = githubUrl.substring(0, githubUrl.length() - 1);

            logger.info("Github URL: {}", githubUrl);
            logger.info("Github API URL: {}", githubApiUrl);

            Map<String,Object> tokenData = CustomHttpRequest.postRequest(githubUrl + "/login/oauth/access_token", params);
            logger.info("Post request to {} success", githubUrl);

            String accessToken = tokenData.get("access_token").toString();
            if (StringUtils.isEmpty(accessToken)){
                logger.info("Access token empty");
            } else {
                logger.info("Access token length: {}", accessToken.length());
            }

            String refreshToken = tokenData.getOrDefault("refresh_token", "").toString();
            if (StringUtils.isEmpty(refreshToken)){
                logger.info("Refresh token empty");
            } else {
                logger.info("Refresh token length: {}", refreshToken.length());
            }

            int refreshTokenExpiry = (int) Double.parseDouble(tokenData.getOrDefault("refresh_token_expires_in", "0").toString());
            Map<String,Object> userData = CustomHttpRequest.getRequest(githubApiUrl + "/user", "Bearer " + accessToken);
            logger.info("Get request to {} success", githubApiUrl);

            List<Map<String, String>> emailResp = GithubLogin.getEmailRequest(accessToken);
            String username = userData.get("name").toString();
            String email = GithubLogin.getPrimaryGithubEmail(emailResp);
            if(email == null || email.isEmpty()) {
                email = username + "@sso";
            }
            logger.info("username {}", username);
            SignupInfo.GithubSignupInfo ghSignupInfo = new SignupInfo.GithubSignupInfo(accessToken, refreshToken, refreshTokenExpiry, email, username);
            shouldLogin = "true";
            createUserAndRedirectWithDefaultRole(email, username, ghSignupInfo, 1000000, Config.ConfigType.GITHUB.toString());
            code = "";
            logger.info("Executed registerViaGithub");

        } catch (IOException e) {
            e.printStackTrace();
            logger.error(e.getMessage());
            return ERROR.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error(e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String registerViaOkta() throws IOException{
        try {
            Config.OktaConfig oktaConfig;
            if(DashboardMode.isOnPremDeployment()) {
                OktaLogin oktaLoginInstance = OktaLogin.getInstance();
                if(oktaLoginInstance == null){
                    servletResponse.sendRedirect("/login");
                    return ERROR.toUpperCase();
                }
                try {
                    setAccountId(Integer.parseInt(state));
                } catch (NumberFormatException e) {
                    servletResponse.sendRedirect("/login");
                    return ERROR.toUpperCase();
                }
                oktaConfig = OktaLogin.getInstance().getOktaConfig();
            } else {
                setAccountId(Integer.parseInt(state));
                oktaConfig = Config.getOktaConfig(accountId);
            }
            if(oktaConfig == null) {
                servletResponse.sendRedirect("/login");
                return ERROR.toUpperCase();
            }

            String domainUrl = "https://" + oktaConfig.getOktaDomainUrl() + "/oauth2/" + oktaConfig.getAuthorisationServerId() + "/v1";
            String clientId = oktaConfig.getClientId();
            String clientSecret = oktaConfig.getClientSecret();
            String redirectUri = oktaConfig.getRedirectUri();

            BasicDBObject params = new BasicDBObject();
            params.put("grant_type", "authorization_code");
            params.put("code", this.code);
            params.put("client_id", clientId);
            params.put("client_secret", clientSecret);
            params.put("redirect_uri", redirectUri);

            Map<String,Object> tokenData = CustomHttpRequest.postRequestEncodedType(domainUrl +"/token",params);
            String accessToken = tokenData.get("access_token").toString();
            Map<String,Object> userInfo = CustomHttpRequest.getRequest( domainUrl + "/userinfo","Bearer " + accessToken);
            String email = userInfo.get("email").toString();
            String username = userInfo.get("preferred_username").toString();

            SignupInfo.OktaSignupInfo oktaSignupInfo= new SignupInfo.OktaSignupInfo(accessToken, username);
            shouldLogin = "true";
            createUserAndRedirectWithDefaultRole(email, username, oktaSignupInfo, accountId, Config.ConfigType.OKTA.toString());
            code = "";
        } catch (Exception e) {
            logger.errorAndAddToDb("Error while signing in via okta sso \n" + e.getMessage(), LogDb.DASHBOARD);
            servletResponse.sendRedirect("/login");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchDefaultInviteRole(int accountId, String fallbackDefault){
        try {
            Context.accountId.set(accountId);
            CustomRole defaultRole = CustomRoleDao.instance.findOne(CustomRole.DEFAULT_INVITE_ROLE, true);
            if(defaultRole != null){
                return defaultRole.getName();
            }
        } catch(Exception e){
            logger.error("Error while setting default role to " + fallbackDefault);
        }
        return fallbackDefault;
    }

    private int accountId;
    private String userEmail;

    public String sendRequestToSamlIdP() throws IOException{
        String queryString = servletRequest.getQueryString();
        String emailId = Util.getValueFromQueryString(queryString, "email");
        if(!DashboardMode.isOnPremDeployment() && emailId.isEmpty()){
            code = "Error, user email cannot be empty";
            logger.error(code);
            servletResponse.sendRedirect("/login");
            return ERROR.toUpperCase();
        }
        logger.info("Trying to sign in for: " + emailId);
        setUserEmail(emailId);
        SAMLConfig samlConfig = null;
        if(userEmail != null && !userEmail.isEmpty()) {
            samlConfig = SSOConfigsDao.instance.getSSOConfig(userEmail);
        } else if(DashboardMode.isOnPremDeployment()) {
            samlConfig = SSOConfigsDao.getSAMLConfigByAccountId(1000000);
        }
        if(samlConfig == null) {
            code = "Error, cannot login via SSO, trying to login with okta sso";
            logger.error(code);
            return oktaAuthUrlCreator(emailId);
        }
        int tempAccountId = Integer.parseInt(samlConfig.getId());
        logger.info("Account id: " + tempAccountId + " found for " + emailId);
        setAccountId(tempAccountId);

        Saml2Settings settings = null;
        settings = CustomSamlSettings.getSamlSettings(samlConfig);

        if(settings == null){
            code= "Error, cannot find sso for this organization, redirecting to login";
            logger.error(code);
            return ERROR.toUpperCase();
        }
        try {
            Auth auth = new Auth(settings, servletRequest, servletResponse);
            String relayState = String.valueOf(tempAccountId);
            auth.login(relayState);
            logger.info("Initiated login from saml of " + userEmail);
        } catch (Exception e) {
            servletResponse.sendRedirect("/login");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String oktaAuthUrlCreator(String emailId) throws IOException {
        logger.info("Trying to create auth url for okta sso for: " + emailId);
        Config.OktaConfig oktaConfig = Config.getOktaConfig(emailId);
        if(oktaConfig == null) {
            oktaConfig = OktaLogin.getInstance().getOktaConfig();
            if(oktaConfig == null){
                code= "Error, cannot find okta sso for this organization, redirecting to login";
                logger.error(code);
                servletResponse.sendRedirect("/login");
                return ERROR.toUpperCase();
            }
        }

        String authorisationUrl = OktaLogin.getAuthorisationUrl(emailId);
        servletResponse.sendRedirect(authorisationUrl);
        return SUCCESS.toUpperCase();
    }

    public String registerViaAzure() throws Exception{
        Auth auth;
        try {
            SAMLConfig samlConfig = null;
            String relayState = servletRequest.getParameter("RelayState");
            logger.info("RelayState received in registerViaAzure: " + relayState);
            if (relayState == null || relayState.isEmpty()) {
                logger.errorAndAddToDb("RelayState not found");
                return ERROR.toUpperCase();
            }

            Integer resolvedAccountId = null;

            if (StringUtils.isNumeric(relayState)) {
                resolvedAccountId = Integer.parseInt(relayState);
                samlConfig = SSOConfigsDao.getSAMLConfigByAccountId(resolvedAccountId);
            } else {
                samlConfig = SSOConfigsDao.instance.getSSOConfigByDomain(relayState);
            }

            if (samlConfig == null) {
                logger.errorAndAddToDb("Invalid RelayState: No matching samlConfig for orgName: " + relayState);
                servletResponse.sendRedirect("/login");
                return ERROR.toUpperCase();
            }

            try {
                resolvedAccountId = Integer.valueOf(samlConfig.getId());
            } catch (Exception e) {
                logger.errorAndAddToDb("Error while parsing account ID: " + e.getMessage());
                servletResponse.sendRedirect("/login");
                return ERROR.toUpperCase();
            }

            setAccountId(resolvedAccountId);
            CustomSamlSettings.getInstance(ConfigType.AZURE, this.accountId).setSamlConfig(samlConfig);
            Saml2Settings settings = CustomSamlSettings.buildSamlSettingsMap(samlConfig);
            HttpServletRequest wrappedRequest = SsoUtils.getWrappedRequest(servletRequest,ConfigType.AZURE, this.accountId);
            logger.info("Before sending request to Azure Idp");
            auth = new Auth(settings, wrappedRequest, servletResponse);
            auth.processResponse();
            logger.info("After processing response from Azure Idp");
            if (!auth.isAuthenticated()) {
                logger.errorAndAddToDb("Error reason: " + auth.getLastErrorReason(), LogDb.DASHBOARD);
                servletResponse.sendRedirect("/login");
                return ERROR.toUpperCase();
            }
            String useremail = null;
            String username = null;
            List<String> errors = auth.getErrors();
            if (!errors.isEmpty()) {
                logger.errorAndAddToDb("Error in authenticating azure user \n" + auth.getLastErrorReason(), LogDb.DASHBOARD);
                return ERROR.toUpperCase();
            } else {
                Map<String, List<String>> attributes = auth.getAttributes();
                if (attributes.isEmpty()) {
                    logger.error("Returning as attributes were not found");
                    return ERROR.toUpperCase();
                }
                String nameId = auth.getNameId();
                useremail = nameId;
                username = nameId;
            }
            shouldLogin = "true";
            logger.info("Successful signing with Azure Idp for: "+ useremail);
            SignupInfo.SamlSsoSignupInfo signUpInfo = new SignupInfo.SamlSsoSignupInfo(username, useremail, Config.ConfigType.AZURE);

            createUserAndRedirectWithDefaultRole(useremail, username, signUpInfo, this.accountId, Config.ConfigType.AZURE.toString());
        } catch (Exception e1) {
            logger.errorAndAddToDb("Error while signing in via azure sso \n" + e1.getMessage(), LogDb.DASHBOARD);
            servletResponse.sendRedirect("/login");
        }

        return SUCCESS.toUpperCase();
    }

    public String registerViaGoogleSamlSso() throws IOException{
        Auth auth;
        try {
            String tempAccountId = servletRequest.getParameter("RelayState");
            if(tempAccountId == null || tempAccountId.isEmpty()){
                logger.errorAndAddToDb("Account id not found");
                return ERROR.toUpperCase();
            }
            setAccountId(Integer.parseInt(tempAccountId));
            Saml2Settings settings = CustomSamlSettings.getSamlSettings(ConfigType.GOOGLE_SAML, this.accountId);
            HttpServletRequest wrappedRequest = SsoUtils.getWrappedRequest(servletRequest, ConfigType.GOOGLE_SAML, this.accountId);
            auth = new Auth(settings, wrappedRequest, servletResponse);
            auth.processResponse();
            if (!auth.isAuthenticated()) {
                return ERROR.toUpperCase();
            }
            String userEmail = null;
            String username = null;
            List<String> errors = auth.getErrors();
            if (!errors.isEmpty()) {
                logger.errorAndAddToDb("Error in authenticating user from google sso \n" + auth.getLastErrorReason(), LogDb.DASHBOARD);
                return ERROR.toUpperCase();
            }
            Map<String, List<String>> attributes = auth.getAttributes();
            String nameId = "";
            if (!attributes.isEmpty()) {
                List<String> emails = attributes.get("email");
                if(!emails.isEmpty()){
                    nameId = emails.get(0);
                }else{
                    nameId = auth.getNameId();
                }
            }else{
                nameId = auth.getNameId();
            }
            userEmail = nameId;
            username = nameId;
            
            shouldLogin = "true";
            SignupInfo.SamlSsoSignupInfo signUpInfo = new SignupInfo.SamlSsoSignupInfo(username, userEmail, Config.ConfigType.GOOGLE_SAML);

            createUserAndRedirectWithDefaultRole(userEmail, username, signUpInfo, this.accountId, Config.ConfigType.GOOGLE_SAML.toString());
        } catch (Exception e1) {
            logger.errorAndAddToDb("Error while signing in via google workspace sso \n" + e1.getMessage(), LogDb.DASHBOARD);
            servletResponse.sendRedirect("/login");
        }

        return SUCCESS.toUpperCase();
    }

    public static final String MINIMUM_PASSWORD_ERROR = "Minimum of 8 characters required";
    public static final String MAXIMUM_PASSWORD_ERROR = "Maximum of 40 characters allowed";
    public static final String INVALID_CHAR = "Invalid character";
    public static final String MUST_BE_ALPHANUMERIC_ERROR = "Must contain letters and numbers";
    public static String validatePassword(String password) {
        boolean minimumFlag = password.length() >= 8;
        if (!minimumFlag) return MINIMUM_PASSWORD_ERROR;
        boolean maximumFlag = password.length() < 40;
        if (!maximumFlag) return MAXIMUM_PASSWORD_ERROR;

        boolean numbersFlag = false;
        boolean lettersFlag = false;

        Set<String> allowedSpecialChars = new HashSet<>(Arrays.asList("+", "@", "*", "#", "$", "%", "&", "/", "(", ")", "=", "?", "^", "!", "[","]", "{", "}", "-", "_", ":", ";", ">", "<", "|", ",", "."));

        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            char upperCaseCh = Character.toUpperCase(ch);
            if (ch >= '0' && ch <= '9') {
                numbersFlag = true;
            } else if (upperCaseCh >= 'A' && upperCaseCh <= 'Z') {
                lettersFlag = true;
            } else if (!allowedSpecialChars.contains(ch+"")) {
                return INVALID_CHAR;
            }
        }

        if (numbersFlag && lettersFlag) {
            return null;
        }

        return MUST_BE_ALPHANUMERIC_ERROR;

    }

    String companyName, teamName;
    List<String> allEmails;
    String shouldLogin="false";

//    public String addSignupInfo() throws IOException {
//        String email = ((BasicDBObject) servletRequest.getAttribute("signupInfo")).getString("email");
//        Bson updates =
//                combine(
//                        set("companyName", companyName),
//                        set("teamName", teamName),
//                        set("emailInvitations", allEmails)
//                );
//
//        Bson findQ = eq("user.login", email);
//        SignupDao.instance.updateOne(findQ, updates);
//
//        SignupUserInfo updatedUserInfo = SignupDao.instance.findOne(findQ);
//
//        if (StringUtils.isEmpty(updatedUserInfo.getTeamName())) {
//            shouldLogin = "false";
//        } else {
//            shouldLogin = "true";
//            int invitationToAccountId = updatedUserInfo.getInvitationToAccount();
//            createUserAndRedirect(email, email, updatedUserInfo.getUser().getSignupInfoMap().entrySet().iterator().next().getValue(), invitationToAccountId);
//            Mail welcomeEmail = WelcomeEmail.buildWelcomeEmail("there", email);
//            try {
//                WelcomeEmail.send(welcomeEmail);
//            } catch (IOException e) {
//                // TODO Auto-generated catch block
//                e.printStackTrace();
//                return ERROR.toUpperCase();
//            }
//
//        }
//
//        return "SUCCESS";
//    }

    private void createUserAndRedirect(String userEmail, String username, SignupInfo signupInfo,
                                       int invitationToAccount, String method) throws IOException {
        createUserAndRedirect(userEmail, username, signupInfo, invitationToAccount, method, null);
    }

    private void createUserAndRedirectWithDefaultRole(String userEmail, String username, SignupInfo signupInfo,
                                       int invitationToAccount, String method) throws IOException {
        String defaultRole = RBAC.Role.MEMBER.name();
        if (UsageMetricCalculator.isRbacFeatureAvailable(invitationToAccount)) {
            defaultRole = fetchDefaultInviteRole(invitationToAccount, RBAC.Role.GUEST.name());
        }
        createUserAndRedirect(userEmail, username, signupInfo, invitationToAccount, method, defaultRole);
    }

    private void createUserAndRedirect(String userEmail, String username, SignupInfo signupInfo,
                                       int invitationToAccount, String method, String invitedRole) throws IOException {
        logger.infoAndAddToDb("createUserAndRedirect called");
        User user = UsersDao.instance.findOne(eq("login", userEmail));
        if (user == null && "false".equalsIgnoreCase(shouldLogin)) {
            logger.infoAndAddToDb("user null in createUserAndRedirect");
            SignupUserInfo signupUserInfo = SignupDao.instance.insertSignUp(userEmail, username, signupInfo, invitationToAccount);
            LoginAction.loginUser(signupUserInfo.getUser(), servletResponse, false, servletRequest);
            servletRequest.setAttribute("username", userEmail);
            servletResponse.sendRedirect("/dashboard/onboarding");
        } else {
            logger.infoAndAddToDb("user not null in createUserAndRedirect");
            logger.infoAndAddToDb("invitationToAccount: " + invitationToAccount);
            int accountId = 0;
            if (invitationToAccount > 0) {
                Account account = AccountsDao.instance.findOne("_id", invitationToAccount);
                if (account != null) {
                    accountId = account.getId();
                }
            }

            boolean isSSOLogin = Config.isConfigSSOType(signupInfo.getConfigType());
            logger.info("Is sso login: " + isSSOLogin);
            if (user == null) {

                if (accountId == 0) {
                    accountId = AccountAction.createAccountRecord("My account");
                    logger.infoAndAddToDb("new accountId : " + accountId);

                    // Create organization for new user
                    if (DashboardMode.isSaasDeployment()) {
                        String organizationUUID = UUID.randomUUID().toString();

                        Set<Integer> organizationAccountsSet = new HashSet<Integer>();
                        organizationAccountsSet.add(accountId);

                        Organization organization = new Organization(organizationUUID, userEmail, userEmail, organizationAccountsSet, false);
                        OrganizationsDao.instance.insertOne(organization);
                        logger.info(String.format("Created organization %s for new user %s", organizationUUID, userEmail));
                        
                        Boolean attemptSyncWithAktoSuccess = OrganizationUtils.syncOrganizationWithAkto(organization);
                        logger.info(String.format("Organization %s for new user %s - Akto sync status - %s", organizationUUID, userEmail, attemptSyncWithAktoSuccess));
                    }
                }

                user = UsersDao.instance.insertSignUp(userEmail, username, signupInfo, accountId);
                logger.infoAndAddToDb("new user: " + user.getId());

            } else if (StringUtils.isEmpty(code) && !isSSOLogin) {
                if (accountId == 0) {
                    logger.info("Returning as accountId was found 0");
                    throw new IllegalStateException("The account doesn't exist.");
                }
            } else {
                if(!isSSOLogin && invitedRole != null && accountId != 0){
                    RBACDao.instance.insertOne(
                        new RBAC(user.getId(), invitedRole, accountId)
                    );
                }
                LoginAction.loginUser(user, servletResponse, true, servletRequest);
                servletResponse.sendRedirect("/dashboard/observe/inventory");
                return;
            }


            logger.infoAndAddToDb("Initialize Account");
            user = AccountAction.initializeAccount(userEmail, accountId, "My account",invitationToAccount == 0, invitedRole == null ? RBAC.Role.ADMIN.name() : invitedRole);

            servletRequest.getSession().setAttribute("user", user);
            servletRequest.getSession().setAttribute("accountId", accountId);
            LoginAction.loginUser(user, servletResponse, true, servletRequest);
            servletRequest.setAttribute("username", userEmail);
            servletResponse.sendRedirect("/dashboard/onboarding");

            String dashboardMode = DashboardMode.getActualDashboardMode().toString();
            String distinct_id = userEmail + "_" + dashboardMode;
            JSONObject props = new JSONObject();
            props.put("Email ID", userEmail);
            props.put("Email Verified", false);
            props.put("Source", "dashboard");
            props.put("Dashboard Mode", dashboardMode);
            props.put("Invited", invitationToAccount != 0);
            props.put("method", method);

            SlackAlerts newUserJoiningAlert = new NewUserJoiningAlert(userEmail);
            SlackSender.sendAlert(accountId, newUserJoiningAlert);

            AktoMixpanel aktoMixpanel = new AktoMixpanel();
            aktoMixpanel.sendEvent(distinct_id, "SIGNUP_SUCCEEDED", props);
        }
    }

    protected HttpServletResponse servletResponse;
    @Override
    public void setServletResponse(HttpServletResponse httpServletResponse) {
        this.servletResponse= httpServletResponse;
    }

    @Override
    public String execute() throws Exception {
        return SUCCESS;
    }

    protected HttpServletRequest servletRequest;
    @Override
    public void setServletRequest(HttpServletRequest httpServletRequest) {
        this.servletRequest = httpServletRequest;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public List<String> getAllEmails() {
        return allEmails;
    }

    public void setAllEmails(List<String> allEmails) {
        this.allEmails = allEmails;
    }

    public void setInvitationCode(String invitationCode) {
        this.invitationCode = invitationCode;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
}
