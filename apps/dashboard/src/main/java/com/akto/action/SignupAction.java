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
import lombok.Setter;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.Arrays;
import java.util.HashMap;
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
        logger.debug(code+ " " +state);

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
        logger.debugAndAddToDb("registerViaAuth0 called");
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
        logger.debug(jsonBody.toString());
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
                    AccountAction.addUserToExistingAccount(email, pendingInviteCode.getAccountId(), pendingInviteCode.getInviteeRole());
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
        logger.debug("Executed registerViaAuth0");
        return SUCCESS.toUpperCase();
    }

    public String registerViaGoogle() {
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();
        logger.debug(code + " " + state);

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
        logger.debug("registerViaGithub");
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();
        GithubLogin ghLoginInstance = GithubLogin.getInstance();
        if (ghLoginInstance == null) {
            return ERROR.toUpperCase();
        }
        logger.debug("Found github instance");
        Config.GithubConfig githubConfig = GithubLogin.getInstance().getGithubConfig();
        if (githubConfig == null) {
            return ERROR.toUpperCase();
        }
        logger.debug("Found github configuration");
        BasicDBObject params = new BasicDBObject();
        params.put("client_id", githubConfig.getClientId());
        params.put("client_secret", githubConfig.getClientSecret());
        params.put("code", this.code);
        params.put("scope", "user");
        logger.debug("Github code length: {}", this.code.length());
        try {
            String githubUrl = githubConfig.getGithubUrl();
            if (StringUtils.isEmpty(githubUrl)) githubUrl = "https://github.com";

            String githubApiUrl = githubConfig.getGithubApiUrl();
            if (StringUtils.isEmpty(githubApiUrl)) githubApiUrl = "https://api.github.com";

            if (githubApiUrl.endsWith("/")) githubApiUrl = githubApiUrl.substring(0, githubApiUrl.length() - 1);
            if (githubUrl.endsWith("/")) githubUrl = githubUrl.substring(0, githubUrl.length() - 1);

            logger.debug("Github URL: {}", githubUrl);
            logger.debug("Github API URL: {}", githubApiUrl);

            Map<String,Object> tokenData = CustomHttpRequest.postRequest(githubUrl + "/login/oauth/access_token", params);
            logger.debug("Post request to {} success", githubUrl);

            String accessToken = tokenData.get("access_token").toString();
            if (StringUtils.isEmpty(accessToken)){
                logger.debug("Access token empty");
            } else {
                logger.debug("Access token length: {}", accessToken.length());
            }

            String refreshToken = tokenData.getOrDefault("refresh_token", "").toString();
            if (StringUtils.isEmpty(refreshToken)){
                logger.debug("Refresh token empty");
            } else {
                logger.debug("Refresh token length: {}", refreshToken.length());
            }

            int refreshTokenExpiry = (int) Double.parseDouble(tokenData.getOrDefault("refresh_token_expires_in", "0").toString());
            Map<String,Object> userData = CustomHttpRequest.getRequest(githubApiUrl + "/user", "Bearer " + accessToken);
            logger.debug("Get request to {} success", githubApiUrl);

            List<Map<String, String>> emailResp = GithubLogin.getEmailRequest(accessToken);
            String username = userData.get("name").toString();
            String email = GithubLogin.getPrimaryGithubEmail(emailResp);
            if(email == null || email.isEmpty()) {
                email = username + "@sso";
            }
            logger.debug("username {}", username);
            SignupInfo.GithubSignupInfo ghSignupInfo = new SignupInfo.GithubSignupInfo(accessToken, refreshToken, refreshTokenExpiry, email, username);
            shouldLogin = "true";
            createUserAndRedirectWithDefaultRole(email, username, ghSignupInfo, 1000000, Config.ConfigType.GITHUB.toString());
            code = "";
            logger.debug("Executed registerViaGithub");

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
        logger.infoAndAddToDb("registerViaOkta called with code: " + (this.code != null ? this.code.substring(0, Math.min(10, this.code.length())) + "..." : "null") + ", state: " + (this.state != null ? "present" : "null"));
        try {
            Config.OktaConfig oktaConfig = null;
            logger.info("Checking deployment mode");
            if(DashboardMode.isOnPremDeployment()) {
                logger.info("On-prem deployment detected");
                OktaLogin oktaLoginInstance = OktaLogin.getInstance();
                logger.info("OktaLogin instance: " + (oktaLoginInstance != null ? "found" : "null"));
                if(oktaLoginInstance == null){
                    logger.infoAndAddToDb("OktaLogin instance is null, redirecting to /login");
                    servletResponse.sendRedirect("/login");
                    return ERROR.toUpperCase();
                }
                try {
                    logger.info("Parsing state as accountId for on-prem");
                    setAccountId(Integer.parseInt(state));
                    logger.infoAndAddToDb("Parsed accountId from state: " + this.accountId);
                } catch (NumberFormatException e) {
                    logger.infoAndAddToDb("Failed to parse state as accountId: " + e.getMessage());
                    servletResponse.sendRedirect("/login");
                    return ERROR.toUpperCase();
                }
                logger.info("Getting OktaConfig from OktaLogin instance");
                oktaConfig = OktaLogin.getInstance().getOktaConfig();
                logger.info("OktaConfig retrieved: " + (oktaConfig != null ? "found" : "null"));
            } else {
                logger.info("SaaS deployment detected");
                logger.info("Decoding state from Base64");
                String decodedState = new String(java.util.Base64.getDecoder().decode(state));
                logger.info("Decoded state: " + decodedState);
                BasicDBObject parsedState = BasicDBObject.parse(decodedState);
                String accountId = parsedState.getString("accountId");
                logger.info("Extracted accountId from parsed state: " + accountId);
                setAccountId(Integer.parseInt(accountId));
                logger.infoAndAddToDb("Set accountId: " + this.accountId);

                if(parsedState.containsKey("signupInvitationCode") && parsedState.containsKey("signupEmailId")) {
                    logger.info("State contains signupInvitationCode and signupEmailId");
                    setSignupInvitationCode(parsedState.getString("signupInvitationCode"));
                    setSignupEmailId(parsedState.getString("signupEmailId"));
                    logger.infoAndAddToDb("Set signupInvitationCode: " + (this.signupInvitationCode != null ? "present" : "null") + ", signupEmailId: " + this.signupEmailId);
                } else {
                    logger.info("State does not contain signupInvitationCode or signupEmailId");
                }
                logger.info("Getting OktaConfig for accountId: " + this.accountId);
                oktaConfig = Config.getOktaConfig(this.accountId);
                logger.info("OktaConfig retrieved: " + (oktaConfig != null ? "found" : "null"));
            }
            if(oktaConfig == null) {
                logger.infoAndAddToDb("OktaConfig is null, redirecting to /login");
                servletResponse.sendRedirect("/login");
                return ERROR.toUpperCase();
            }
            logger.info("Building domain URL from OktaDomainUrl: " + oktaConfig.getOktaDomainUrl());
            String domainUrl = "https://" + oktaConfig.getOktaDomainUrl() + "/oauth2/";
            if(oktaConfig.getAuthorisationServerId() == null || oktaConfig.getAuthorisationServerId().isEmpty()){
                logger.info("No authorisation server ID found, using default /v1");
                domainUrl += "/v1";
            }else{
                logger.info("Using authorisation server ID: " + oktaConfig.getAuthorisationServerId());
                domainUrl += oktaConfig.getAuthorisationServerId() + "/v1";
            }
            logger.infoAndAddToDb("Trying to login with okta sso for account id: " + accountId + " domain url: " + domainUrl);
            String clientId = oktaConfig.getClientId();
            String clientSecret = oktaConfig.getClientSecret();
            String redirectUri = oktaConfig.getRedirectUri();
            logger.info("OktaConfig - ClientId: " + (clientId != null ? "present" : "null") + ", ClientSecret: " + (clientSecret != null ? "present" : "null") + ", RedirectUri: " + redirectUri);

            BasicDBObject params = new BasicDBObject();
            params.put("grant_type", "authorization_code");
            params.put("code", this.code);
            params.put("client_id", clientId);
            params.put("client_secret", clientSecret);
            params.put("redirect_uri", redirectUri);
            logger.info("Built token request params with grant_type: authorization_code");
            logger.infoAndAddToDb("========== [registerViaOkta] API CALL 1: TOKEN ENDPOINT ==========");
            logger.infoAndAddToDb("Making POST request to: " + domainUrl + "/token");
            Map<String,Object> tokenData = CustomHttpRequest.postRequestEncodedType(domainUrl +"/token",params);
            logger.infoAndAddToDb("Token response received, checking for access_token");
            String accessToken = tokenData.get("access_token").toString();
            logger.info("Access token retrieved: " + (accessToken != null && !accessToken.isEmpty() ? "present (length: " + accessToken.length() + ")" : "null/empty"));
            logger.infoAndAddToDb("========== [registerViaOkta] API CALL 2: USERINFO ENDPOINT ==========");
            logger.infoAndAddToDb("Making GET request to: " + domainUrl + "/userinfo");
            Map<String,Object> userInfo = CustomHttpRequest.getRequest( domainUrl + "/userinfo","Bearer " + accessToken);
            logger.infoAndAddToDb("UserInfo response received");
            String email = userInfo.get("email").toString();
            String username = userInfo.get("preferred_username").toString();
            logger.infoAndAddToDb("Extracted user info - email: " + email + ", preferred_username: " + username);

            if (oktaConfig.getOrganizationDomain() != null && !oktaConfig.getOrganizationDomain().isEmpty()) {
                logger.info("Organization domain validation required: " + oktaConfig.getOrganizationDomain());
                String userDomain = null;
                if (email != null && email.contains("@")) {
                    userDomain = email.substring(email.indexOf('@') + 1);
                    logger.info("Extracted user domain from email: " + userDomain);
                } else {
                    logger.info("Email does not contain '@' or is null");
                }

                if (userDomain == null || !oktaConfig.getOrganizationDomain().equalsIgnoreCase(userDomain)) {
                    logger.errorAndAddToDb("Domain mismatch: user " + email + " with domain " + userDomain +
                                         " attempted to access account " + accountId +
                                         " with required domain " + oktaConfig.getOrganizationDomain(), LogDb.DASHBOARD);
                    logger.info("Redirecting to /login?error=unauthorized due to domain mismatch");
                    servletResponse.sendRedirect("/login?error=unauthorized");
                    return ERROR.toUpperCase();
                }
                logger.infoAndAddToDb("Domain validation passed for user " + email + " accessing account " + accountId);
            } else {
                logger.info("No organization domain configured, skipping domain validation");
            }

            logger.info("Checking for pending invite code");
            if(!StringUtils.isEmpty(signupInvitationCode) && !StringUtils.isEmpty(signupEmailId)) {
                logger.info("Processing invite code: " + signupInvitationCode + " for email: " + signupEmailId);
                Bson filter = Filters.eq(PendingInviteCode.INVITE_CODE, signupInvitationCode);
                logger.info("Querying PendingInviteCodesDao for invite code");
                PendingInviteCode pendingInviteCode = PendingInviteCodesDao.instance.findOne(filter);
                logger.info("PendingInviteCode found: " + (pendingInviteCode != null ? "yes" : "no"));
                if (pendingInviteCode != null && pendingInviteCode.getInviteeEmailId().equals(email)) {
                    logger.info("Invite code matches, deleting invite code and updating accountId to: " + pendingInviteCode.getAccountId());
                    PendingInviteCodesDao.instance.getMCollection().deleteOne(filter);
                    accountId = pendingInviteCode.getAccountId();
                    logger.info("Updated accountId from invite: " + accountId);
                } else {
                    logger.info("Invite code does not match or invitee email mismatch");
                }
            } else {
                logger.info("No signupInvitationCode or signupEmailId present");
            }

            logger.info("Creating OktaSignupInfo for user: " + username);
            SignupInfo.OktaSignupInfo oktaSignupInfo= new SignupInfo.OktaSignupInfo(accessToken, username);
            shouldLogin = "true";
            logger.info("Setting shouldLogin to true");
            logger.info("Calling createUserAndRedirectWithDefaultRole for email: " + email + ", accountId: " + accountId);
            createUserAndRedirectWithDefaultRole(email, username, oktaSignupInfo, accountId, Config.ConfigType.OKTA.toString());
            code = "";
            logger.info("registerViaOkta completed successfully");
        } catch (Exception e) {
            logger.errorAndAddToDb("Error while signing in via okta sso \n" + e.getMessage(), LogDb.DASHBOARD);
            logger.info("Exception in registerViaOkta: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            logger.info("Redirecting to /login due to exception");
            servletResponse.sendRedirect("/login");
            return ERROR.toUpperCase();
        }
        logger.info("Returning SUCCESS from registerViaOkta");
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

    @Setter
    private String signupInvitationCode;
    @Setter
    private String signupEmailId;

    public String sendRequestToSamlIdP() throws IOException{
        String queryString = servletRequest.getQueryString();
        String emailId = Util.getValueFromQueryString(queryString, "email");
        setSignupInvitationCode(Util.getValueFromQueryString(queryString, "signupInvitationCode"));
        setSignupEmailId(Util.getValueFromQueryString(queryString, "signupEmailId"));
        
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
            BasicDBObject relayStateObj = new BasicDBObject("accountId", String.valueOf(tempAccountId));
            if(!StringUtils.isEmpty(this.signupInvitationCode) && !StringUtils.isEmpty(this.signupEmailId)) {
                relayStateObj.append("signupInvitationCode", this.signupInvitationCode)
                        .append("signupEmailId", this.signupEmailId);
            }
            String relayState = java.util.Base64.getEncoder().encodeToString(relayStateObj.toJson().getBytes());
            auth.login(relayState);
            logger.info("Initiated login from saml of " + userEmail);
        } catch (Exception e) {
            servletResponse.sendRedirect("/login");
            return ERROR.toUpperCase();
        }
        return SUCCESS.toUpperCase();
    }

    public String oktaAuthUrlCreator(String emailId) throws IOException {
        logger.debug("Trying to create auth url for okta sso for: " + emailId);
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

        String authorisationUrl = OktaLogin.getAuthorisationUrl(emailId, this.signupEmailId, this.signupInvitationCode);
        servletResponse.sendRedirect(authorisationUrl);
        return SUCCESS.toUpperCase();
    }

    public String oktaInitiateLogin() throws IOException {
        logger.info("Okta-initiated login flow started");

        Config.OktaConfig oktaConfig = null;
        int accountId = -1;

        logger.info("Getting query string from servlet request");
        String queryString = servletRequest.getQueryString();
        logger.info("Query string: " + (queryString != null ? queryString : "null"));
        String accountIdParam = null;
        if(queryString != null && !queryString.isEmpty()) {
            logger.info("Extracting accountId parameter from query string");
            accountIdParam = Util.getValueFromQueryString(queryString, "accountId");
            logger.info("Extracted accountId parameter: " + (accountIdParam != null ? accountIdParam : "null"));
        } else {
            logger.info("Query string is null or empty");
        }

        if(accountIdParam != null && !accountIdParam.isEmpty()) {
            try {
                logger.info("Parsing accountId from query parameter");
                accountId = Integer.parseInt(accountIdParam);
                logger.info("Using accountId from query parameter: " + accountId);
            } catch (NumberFormatException e) {
                logger.error("Invalid accountId in query parameter: " + accountIdParam);
                logger.info("NumberFormatException: " + e.getMessage());
            }
        } else {
            logger.info("No accountId parameter in query string or it is empty");
        }

        logger.info("Getting OktaConfig for accountId: " + accountId);
        oktaConfig = Config.getOktaConfig(accountId);
        logger.info("OktaConfig from Config.getOktaConfig: " + (oktaConfig != null ? "found" : "null"));

        if(oktaConfig == null && DashboardMode.isOnPremDeployment()) {
            logger.info("Trying OktaLogin.getInstance() for on-prem deployment");
            logger.info("Setting Context.accountId to 1000000");
            Context.accountId.set(1_000_000);
            logger.info("Getting OktaLogin instance");
            OktaLogin oktaLoginInstance = OktaLogin.getInstance();
            logger.info("OktaLogin instance: " + (oktaLoginInstance != null ? "found" : "null"));
            if(oktaLoginInstance != null) {
                logger.info("Getting OktaConfig from OktaLogin instance");
                oktaConfig = oktaLoginInstance.getOktaConfig();
                logger.info("OktaConfig from OktaLogin instance: " + (oktaConfig != null ? "found" : "null"));
            } else {
                logger.info("OktaLogin instance is null");
            }
        } else {
            logger.info("Skipping OktaLogin.getInstance() - oktaConfig: " + (oktaConfig != null ? "found" : "null") + ", isOnPremDeployment: " + DashboardMode.isOnPremDeployment());
        }

        if(oktaConfig == null) {
            logger.error("No Okta configuration found for accountId: " + accountId + ", redirecting to SSO login page");
            logger.info("Redirecting to: " + SSO_URL);
            servletResponse.sendRedirect(SSO_URL);
            return ERROR.toUpperCase();
        }

        logger.infoAndAddToDb("Okta SSO login initiated for accountId: " + accountId +
                             " with organizationDomain: " + oktaConfig.getOrganizationDomain());

        logger.info("Okta config found - ClientId: " + oktaConfig.getClientId() +
                    ", Domain: " + oktaConfig.getOktaDomainUrl() +
                    ", RedirectUri: " + oktaConfig.getRedirectUri() +
                    ", AuthServerId: " + oktaConfig.getAuthorisationServerId());

        logger.info("Building OAuth parameter map");
        Map<String, String> paramMap = new HashMap<>();
        paramMap.put("client_id", oktaConfig.getClientId());
        paramMap.put("redirect_uri", oktaConfig.getRedirectUri());
        paramMap.put("response_type", "code");
        paramMap.put("scope", "openid%20email%20profile");
        logger.info("OAuth parameters set - client_id, redirect_uri, response_type: code, scope: openid%20email%20profile");

        logger.info("Building state parameter");
        String stateParam;
        if(DashboardMode.isOnPremDeployment()) {
            logger.info("On-prem deployment: using plain accountId as state");
            stateParam = String.valueOf(accountId);
            logger.info("Using plain state for on-prem: " + stateParam);
        } else {
            logger.info("SaaS deployment: building Base64-encoded JSON state");
            BasicDBObject stateObj = new BasicDBObject("accountId", String.valueOf(accountId));
            String stateJson = stateObj.toJson();
            logger.info("State JSON before encoding: " + stateJson);
            stateParam = java.util.Base64.getEncoder().encodeToString(stateJson.getBytes());
            logger.info("Using Base64-encoded JSON state for SaaS: " + stateJson);
        }
        paramMap.put("state", stateParam);
        logger.info("State parameter added to paramMap");
        String nonce = UUID.randomUUID().toString();
        logger.info("Generated nonce: " + nonce);
        paramMap.put("nonce", nonce);
        logger.info("Nonce added to paramMap");

        logger.info("Converting paramMap to query string");
        String queryStringParams = SsoUtils.getQueryString(paramMap);
        logger.info("Query string params: " + queryStringParams);

        logger.info("Building authorization URL");
        String authUrl = "https://" + oktaConfig.getOktaDomainUrl() + "/oauth2/";
        logger.info("Base auth URL: " + authUrl);
        if(oktaConfig.getAuthorisationServerId() != null && !oktaConfig.getAuthorisationServerId().isEmpty()) {
            logger.info("Adding authorisation server ID: " + oktaConfig.getAuthorisationServerId());
            authUrl += oktaConfig.getAuthorisationServerId() + "/";
        } else {
            logger.info("No authorisation server ID, using default");
        }
        authUrl += "v1/authorize?" + queryStringParams;

        logger.info("Final authorization URL: " + authUrl);
        logger.info("Redirecting to Okta authorization URL for accountId: " + accountId);
        servletResponse.sendRedirect(authUrl);
        logger.info("Redirect issued successfully");
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
            String origRelayState = relayState;
            try {
                BasicDBObject parsedState = BasicDBObject.parse(new String(java.util.Base64.getDecoder().decode(relayState)));
                String tempState = parsedState.getString("accountId");
                if(!StringUtils.isEmpty(tempState)) {
                    origRelayState = tempState;
                }

                if(parsedState.containsKey("signupInvitationCode") && parsedState.containsKey("signupEmailId")) {
                    setSignupInvitationCode(parsedState.getString("signupInvitationCode"));
                    setSignupEmailId(parsedState.getString("signupEmailId"));
                }
            } catch (Exception e) {
                // TODO: handle exception
            }

            Integer resolvedAccountId = null;

            if (StringUtils.isNumeric(origRelayState)) {
                resolvedAccountId = Integer.parseInt(origRelayState);
                samlConfig = SSOConfigsDao.getSAMLConfigByAccountId(resolvedAccountId);
            } else {
                samlConfig = SSOConfigsDao.instance.getSSOConfigByDomain(origRelayState);
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

            int invitedToAccountId = 0;

            if(!StringUtils.isEmpty(this.signupInvitationCode) && !StringUtils.isEmpty(this.signupEmailId)) {
                Bson filter = Filters.eq(PendingInviteCode.INVITE_CODE, signupInvitationCode);
                PendingInviteCode pendingInviteCode = PendingInviteCodesDao.instance.findOne(filter);
                if (pendingInviteCode != null && pendingInviteCode.getInviteeEmailId().equals(email)) {
                    PendingInviteCodesDao.instance.getMCollection().deleteOne(filter);
                    invitedToAccountId = pendingInviteCode.getAccountId();
                }
            }

            setAccountId(resolvedAccountId);
            CustomSamlSettings.getInstance(ConfigType.AZURE, this.accountId).setSamlConfig(samlConfig);
            Saml2Settings settings = CustomSamlSettings.buildSamlSettingsMap(samlConfig);
            HttpServletRequest wrappedRequest = SsoUtils.getWrappedRequest(servletRequest,ConfigType.AZURE, this.accountId);
            logger.debug("Before sending request to Azure Idp");
            auth = new Auth(settings, wrappedRequest, servletResponse);
            auth.processResponse();
            logger.debug("After processing response from Azure Idp");
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
            logger.debug("Successful signing with Azure Idp for: "+ useremail);
            SignupInfo.SamlSsoSignupInfo signUpInfo = new SignupInfo.SamlSsoSignupInfo(username, useremail, Config.ConfigType.AZURE);

            if(invitedToAccountId > 0){
                setAccountId(invitedToAccountId);
            }
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
        logger.debugAndAddToDb("createUserAndRedirect called");
        User user = UsersDao.instance.findOne(eq("login", userEmail));
        if (user == null && "false".equalsIgnoreCase(shouldLogin)) {
            logger.debugAndAddToDb("user null in createUserAndRedirect");
            SignupUserInfo signupUserInfo = SignupDao.instance.insertSignUp(userEmail, username, signupInfo, invitationToAccount);
            LoginAction.loginUser(signupUserInfo.getUser(), servletResponse, false, servletRequest);
            servletRequest.setAttribute("username", userEmail);
            servletResponse.sendRedirect("/dashboard/onboarding");
        } else {
            logger.debugAndAddToDb("user not null in createUserAndRedirect");
            logger.debugAndAddToDb("invitationToAccount: " + invitationToAccount);
            int accountId = 0;
            Account account = null;
            if (invitationToAccount > 0) {
                account = AccountsDao.instance.findOne("_id", invitationToAccount);
                if (account != null) {
                    accountId = account.getId();
                }
            }

            boolean isSSOLogin = Config.isConfigSSOType(signupInfo.getConfigType());
            logger.debug("Is sso login: " + isSSOLogin);
            if (user == null) {

                if (accountId == 0) {
                    accountId = AccountAction.createAccountRecord("My account");
                    logger.debugAndAddToDb("new accountId : " + accountId);

                    // Create organization for new user
                    if (DashboardMode.isSaasDeployment()) {
                        String organizationUUID = UUID.randomUUID().toString();

                        Set<Integer> organizationAccountsSet = new HashSet<Integer>();
                        organizationAccountsSet.add(accountId);

                        Organization organization = new Organization(organizationUUID, userEmail, userEmail, organizationAccountsSet, false);
                        OrganizationsDao.instance.insertOne(organization);
                        logger.debug(String.format("Created organization %s for new user %s", organizationUUID, userEmail));
                        
                        Boolean attemptSyncWithAktoSuccess = OrganizationUtils.syncOrganizationWithAkto(organization);
                        logger.debug(String.format("Organization %s for new user %s - Akto sync status - %s", organizationUUID, userEmail, attemptSyncWithAktoSuccess));
                    }
                }

                user = UsersDao.instance.insertSignUp(userEmail, username, signupInfo, accountId);
                logger.debugAndAddToDb("new user: " + user.getId());

            } else if (StringUtils.isEmpty(code) && !isSSOLogin) {
                if (accountId == 0) {
                    logger.debug("Returning as accountId was found 0");
                    throw new IllegalStateException("The account doesn't exist.");
                }
            } else {
                logger.infoAndAddToDb("Invited user found, updating accountId: " + accountId + " for user: " + user.getLogin());
                if(invitedRole != null && accountId != 0){
                    // check if the invited account exists in the user info, if not, add it
                    String accountIdStr = String.valueOf(accountId);
                    boolean exists = user.getAccounts().containsKey(accountIdStr);
                    if(!exists){
                        RBACDao.instance.insertOne(
                            new RBAC(user.getId(), invitedRole, accountId)
                        );
                        String accountName = account != null ? account.getName() : "My account";
                        user = UsersDao.addAccount(user.getLogin(), accountId, accountName);
                    }

                    servletRequest.getSession().setAttribute("accountId", accountId);
                }

                LoginAction.loginUser(user, servletResponse, true, servletRequest, signupInfo);
                servletResponse.sendRedirect("/dashboard/observe/inventory");
                return;
            }


            logger.debugAndAddToDb("Initialize Account");
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
            SlackSender.sendAlert(accountId, newUserJoiningAlert, null);

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
