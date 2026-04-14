package com.akto.action;

import static com.akto.dao.MCollection.SET;
import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;

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
import com.akto.utils.crons.OrganizationCache;
import com.akto.utils.sso.CustomSamlSettings;
import com.akto.util.Pair;
import com.akto.utils.Utils;
import com.akto.utils.sso.SsoUtils;
import com.auth0.Tokens;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import com.mongodb.BasicDBObject;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
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
                    createUserAndRedirect(userEmail, userName, info, 0, Config.ConfigType.SLACK.toString(),null);
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

                // Extract scope-role mapping from invitation
                this.scopeRoleMapping = pendingInviteCode.getScopeRoleMapping();

                // Ensure complete mapping with NO_ACCESS for unassigned scopes
                logger.infoAndAddToDb("[registerViaAuth0] scopeRoleMapping before init: " + this.scopeRoleMapping);
                if (this.scopeRoleMapping == null || this.scopeRoleMapping.isEmpty()) {

                    this.scopeRoleMapping = RBAC.initializeScopeRoleMapping(this.scopeRoleMapping, RBAC.Role.MEMBER.getName());
                    logger.infoAndAddToDb("[registerViaAuth0] scopeRoleMapping after init: " + this.scopeRoleMapping);
                }
                logger.infoAndAddToDb("[registerViaAuth0] scopeRoleMapping after ensuring complete: " + this.scopeRoleMapping);

                if(user != null){
                    AccountAction.addUserToExistingAccount(email, pendingInviteCode.getAccountId(), pendingInviteCode.getInviteeRole(),this.scopeRoleMapping);
                }
                createUserAndRedirect(email, name, auth0SignupInfo, pendingInviteCode.getAccountId(), Config.ConfigType.AUTH0.toString(), pendingInviteCode.getInviteeRole(), this.scopeRoleMapping);

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

        }else {
            this.scopeRoleMapping = RBAC.initializeScopeRoleMapping(this.scopeRoleMapping, RBAC.Role.MEMBER.name());
            // Ensure all scopes are present with NO_ACCESS as default for unmapped scopes
        }
        createUserAndRedirect(email, name, auth0SignupInfo, 0, Config.ConfigType.AUTH0.toString(), this.scopeRoleMapping);
        code = "";
        logger.debug("Executed registerViaAuth0");
        return SUCCESS.toUpperCase();
    }

    public String registerViaGoogle() {
        if (!DashboardMode.isOnPremDeployment()) return Action.ERROR.toUpperCase();
        // Implementation deleted.
        // Not present in the new workflows.
        return SUCCESS.toUpperCase();
    };

    String password;
    String email;
    String invitationCode;
    Map<String, String> scopeRoleMapping;

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
        this.scopeRoleMapping = null;
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

            // Extract scope-role mapping if available (new n:n mapping approach)
            this.scopeRoleMapping = pendingInviteCode.getScopeRoleMapping();

            // Ensure complete mapping with NO_ACCESS for unassigned scopes
            logger.infoAndAddToDb("[registerViaEmail] scopeRoleMapping before init: " + this.scopeRoleMapping);
            if (this.scopeRoleMapping == null || this.scopeRoleMapping.isEmpty()) {

                this.scopeRoleMapping = RBAC.initializeScopeRoleMapping(this.scopeRoleMapping, RBAC.Role.MEMBER.getName());
                logger.infoAndAddToDb("[registerViaEmail] scopeRoleMapping after init: " + this.scopeRoleMapping);
            }
            // Ensure all scopes are present with NO_ACCESS as default for unmapped scopes
            logger.infoAndAddToDb("[registerViaEmail] scopeRoleMapping after ensuring complete: " + this.scopeRoleMapping);

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
            createUserAndRedirect(email, email, signupInfo, invitedToAccountId, "email", inviteeRole, this.scopeRoleMapping);
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
            createUserAndRedirectWithDefaultRole(email, username, ghSignupInfo, 1000000, Config.ConfigType.GITHUB.toString(), null);
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

    public String registerViaOkta() throws IOException {
        try {
            Config.OktaConfig oktaConfig = null;
            if (DashboardMode.isOnPremDeployment()) {
                OktaLogin oktaLoginInstance = OktaLogin.getInstance();
                if (oktaLoginInstance == null) {
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
                BasicDBObject parsedState = BasicDBObject.parse(new String(java.util.Base64.getDecoder().decode(state)));
                setAccountId(Integer.parseInt(parsedState.getString("accountId")));
                Context.accountId.set(this.accountId);
                if (parsedState.containsKey("signupInvitationCode") && parsedState.containsKey("signupEmailId")) {
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
            if (oktaConfig == null) {
                servletResponse.sendRedirect("/login");
                return ERROR.toUpperCase();
            }
            String domainUrl = oktaConfig.getOAuthDomainUrl();

            BasicDBObject params = new BasicDBObject();
            params.put("grant_type", "authorization_code");
            params.put("code", this.code);
            params.put("client_id", oktaConfig.getClientId());
            params.put("client_secret", oktaConfig.getClientSecret());
            params.put("redirect_uri", oktaConfig.getRedirectUri());

            Map<String,Object> tokenData = CustomHttpRequest.postRequestEncodedType(domainUrl + "/token", params);
            String accessToken = tokenData.get("access_token").toString();
            Map<String, Object> userInfo = CustomHttpRequest.getRequest(domainUrl + "/userinfo", "Bearer " + accessToken, false);
            String email = userInfo.get("email").toString();
            String username = userInfo.get("preferred_username").toString();
            logger.infoAndAddToDb("Trying to login with okta sso for email: " + email + ", accountId: " + accountId);
            String oktaUserId = userInfo.get("sub") != null ? userInfo.get("sub").toString() : null;
            if (!StringUtils.isEmpty(oktaConfig.getOrganizationDomain())) {
                String userDomain = email.contains("@") ? email.substring(email.indexOf('@') + 1) : null;
                if (userDomain == null || !oktaConfig.getOrganizationDomain().equalsIgnoreCase(userDomain)) {
                    logger.errorAndAddToDb("Domain mismatch: user " + email + " attempted to access account " + accountId +
                        " with required domain " + oktaConfig.getOrganizationDomain(), LogDb.DASHBOARD);
                    servletResponse.sendRedirect("/login?error=unauthorized");
                    return ERROR.toUpperCase();
                }
            }

            if (!StringUtils.isEmpty(signupInvitationCode) && !StringUtils.isEmpty(signupEmailId)) {
                Bson filter = Filters.eq(PendingInviteCode.INVITE_CODE, signupInvitationCode);
                PendingInviteCode pendingInviteCode = PendingInviteCodesDao.instance.findOne(filter);
                if (pendingInviteCode != null && pendingInviteCode.getInviteeEmailId().equals(email)) {
                    PendingInviteCodesDao.instance.getMCollection().deleteOne(filter);
                    accountId = pendingInviteCode.getAccountId();
                    logger.info("Updated accountId from invite: " + accountId);

                    // Extract scope-role mapping if available (new n:n mapping approach)
                    this.scopeRoleMapping = pendingInviteCode.getScopeRoleMapping();
                    logger.info("Extracted scope-role mapping from invite: " + this.scopeRoleMapping);

                    // Ensure complete mapping with NO_ACCESS for unassigned scopes
                    logger.infoAndAddToDb("[registerViaOkta] scopeRoleMapping before init: " + this.scopeRoleMapping);
                    if (this.scopeRoleMapping == null || this.scopeRoleMapping.isEmpty()) {

                        this.scopeRoleMapping = RBAC.initializeScopeRoleMapping(this.scopeRoleMapping, RBAC.Role.MEMBER.getName());
                        logger.infoAndAddToDb("[registerViaOkta] scopeRoleMapping after init: " + this.scopeRoleMapping);
                    }
                    // Ensure all scopes are present with NO_ACCESS as default for unmapped scopes
                    logger.infoAndAddToDb("[registerViaOkta] scopeRoleMapping after ensuring complete: " + this.scopeRoleMapping);
                } else {
                    logger.info("Invite code does not match or invitee email mismatch");
                }
            }

            shouldLogin = "true";
            String resolvedRole = fetchOktaRole(oktaConfig, oktaUserId, accessToken);
            if(resolvedRole!= null && !resolvedRole.isEmpty()) {
                logger.infoAndAddToDb("[registerViaOkta] resolved role received via okta" + resolvedRole);
                this.scopeRoleMapping = RBAC.initializeFullScopeRoleMapping(this.scopeRoleMapping, resolvedRole);
                logger.infoAndAddToDb("[registerViaOkta] scopeRoleMapping set in  okta" + this.scopeRoleMapping);
            }
            createUserAndRedirect(email, username, new SignupInfo.OktaSignupInfo(accessToken, username), accountId, Config.ConfigType.OKTA.toString(), resolvedRole, this.scopeRoleMapping);
            code = "";
        } catch (Exception e) {
            logger.errorAndAddToDb("Error while signing in via okta sso: " + e.getMessage(), LogDb.DASHBOARD);
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

    private static final Map<String, String> OKTA_CONVENTION_ROLE_MAP = new java.util.LinkedHashMap<String, String>() {{
        put("akto_admin", RBAC.Role.ADMIN.name());
        put("akto_security_engineer", RBAC.Role.MEMBER.name());
        put("akto_developer", RBAC.Role.DEVELOPER.name());
        put("akto_guest", RBAC.Role.GUEST.name());
    }};

    /** Normalize Okta group name for convention lookup: lowercase, spaces to underscores (e.g. "Akto Admin" -> "akto_admin"). */
    private static String normalizeGroupForConvention(String group) {
        if (group == null) return "";
        return group.toLowerCase().replace(' ', '_').trim();
    }

    /**
     * Resolves Akto user role from Okta group membership: custom {@code oktaGroupToAktoUserRoleMap} first, then convention.
     * Custom role names are supported in {@code oktaGroupToAktoUserRoleMap}; priority is derived from the custom role's baseRole.
     */
    private String resolveAktoRoleFromOktaGroupNames(List<String> groupNames, Map<String, String> oktaGroupToAktoUserRoleMap) {
        if (groupNames == null || groupNames.isEmpty()) {
            return RBAC.Role.MEMBER.name();
        }
        if (oktaGroupToAktoUserRoleMap != null && !oktaGroupToAktoUserRoleMap.isEmpty()) {
            String role = resolveHighestPriorityRole(groupNames, oktaGroupToAktoUserRoleMap);
            if (role != null) return role;
        }
        List<String> normalized = new ArrayList<>();
        for (String g : groupNames) {
            normalized.add(normalizeGroupForConvention(g));
        }
        String conventionRole = resolveHighestPriorityRole(normalized, OKTA_CONVENTION_ROLE_MAP);
        return conventionRole != null ? conventionRole : RBAC.Role.MEMBER.name();
    }

    /**
     * Fetches all Okta group names from the Okta Management API (GET /api/v1/groups).
     * Use when adding mappings from the dashboard (no user ID available). Requires API token.
     *
     * @param managementBaseUrl Okta Management API base URL (e.g. https://your-domain.okta.com)
     * @param apiToken          Okta API token (SSWS)
     * @return list of group names; empty list if any param is null/empty or on API failure
     */
    public static List<String> fetchAllOktaGroupNamesFromManagementApi(String managementBaseUrl, String apiToken) {
        if (StringUtils.isEmpty(managementBaseUrl) || StringUtils.isEmpty(apiToken)) {
            return Collections.emptyList();
        }
        String url = managementBaseUrl + "/api/v1/groups";
        String ssws = "SSWS " + apiToken;
        try {
            List<Map<String, Object>> groupsList = CustomHttpRequest.getRequestAsList(url, ssws);
            return extractOktaGroupNamesFromApiResponse(groupsList);
        } catch (Exception e) {
            logger.errorAndAddToDb("[Okta SSO] Failed to fetch Okta groups list: " + e.getMessage(), LogDb.DASHBOARD);
            return Collections.emptyList();
        }
    }

    /**
     * Fetches Okta group names for a user from the Okta Management API.
     * Call this when groups are not available in the access token (e.g. when the token scope does not include groups).
     *
     * @param managementBaseUrl Okta Management API base URL (e.g. https://your-domain.okta.com)
     * @param apiToken          Okta API token (SSWS)
     * @param oktaUserId        Okta user ID
     * @return list of group names; empty list if any param is null/empty or on API failure
     */
    public static List<String> fetchOktaGroupsFromManagementApi(String managementBaseUrl, String apiToken, String oktaUserId) {
        if (StringUtils.isEmpty(managementBaseUrl) || StringUtils.isEmpty(apiToken) || oktaUserId == null || oktaUserId.isEmpty()) {
            return Collections.emptyList();
        }
        String url = managementBaseUrl + "/api/v1/users/" + oktaUserId + "/groups";
        String ssws = "SSWS " + apiToken;
        try {
            List<Map<String, Object>> groupsList = CustomHttpRequest.getRequestAsList(url, ssws);
            return extractOktaGroupNamesFromApiResponse(groupsList);
        } catch (Exception e) {
            logger.errorAndAddToDb("[Okta SSO] Failed to fetch Okta groups for user " + oktaUserId + ": " + e.getMessage(), LogDb.DASHBOARD);
            return Collections.emptyList();
        }
    }

    /**
     * Uses JWT {@code groups} first; if empty and an API token is configured, loads groups from Okta Management API.
     */
    private String fetchOktaRole(Config.OktaConfig oktaConfig, String oktaUserId, String accessToken) {
        Map<String, String> oktaGroupToAktoUserRoleMap = oktaConfig.getOktaGroupToAktoUserRoleMap();

        if (StringUtils.isEmpty(oktaConfig.getManagementApiToken()) || oktaUserId == null) {
            return RBAC.Role.MEMBER.name();
        }
        List<String> groupsFromApi = fetchOktaGroupsFromManagementApi(
                oktaConfig.getManagementBaseUrl(), oktaConfig.getManagementApiToken(), oktaUserId);
        return resolveAktoRoleFromOktaGroupNames(groupsFromApi, oktaGroupToAktoUserRoleMap);
    }

    private static List<String> extractOktaGroupNamesFromApiResponse(List<Map<String, Object>> groupsList) {
        List<String> result = new ArrayList<>();
        if (groupsList == null) return result;
        for (Map<String, Object> item : groupsList) {
            Object profileObj = item.get("profile");
            if (!(profileObj instanceof Map)) continue;
            Object name = ((Map<?, ?>) profileObj).get("name");
            if (name != null) result.add(name.toString());
        }
        return result;
    }

    private String resolveHighestPriorityRole(List<String> keys, Map<String, String> mapping) {
        if (keys == null || keys.isEmpty() || mapping == null || mapping.isEmpty()) return null;
        int bestPriority = Integer.MAX_VALUE;
        String bestRole = null;
        for (String key : keys) {
            String mappedRole = mapping.get(key);
            if (mappedRole == null) continue;
            int priority;
            try {
                priority = RBAC.Role.valueOf(mappedRole).ordinal();
                String dbName = Context.accountId.get()+"";
                logger.info("Role " + mappedRole + " found in mapping");
                logger.info("DB Name: " + dbName);
            } catch (IllegalArgumentException e) {
                String dbName = Context.accountId.get()+"";
                CustomRole customRole = CustomRoleDao.instance.findRoleByName(mappedRole);
                if (customRole == null) continue;
                try {
                    priority = RBAC.Role.valueOf(customRole.getBaseRole()).ordinal();
                } catch (IllegalArgumentException ex) {
                    continue;
                }
            }
            if (priority < bestPriority) {
                bestPriority = priority;
                bestRole = mappedRole;
            }
        }
        return bestRole;
    }

    private int accountId;
    private String userEmail;

    @Setter
    private String signupInvitationCode;
    @Setter
    private String signupEmailId;

    public String sendRequestToSamlIdP() throws IOException{
        logger.infoAndAddToDb("[sendRequestToSamlIdP] ========== SSO LOGIN INITIATED ==========");
        String queryString = servletRequest.getQueryString();
        logger.info("[sendRequestToSamlIdP] Query string: " + (queryString != null ? queryString : "null"));

        String emailId = Util.getValueFromQueryString(queryString, "email");
        setSignupInvitationCode(Util.getValueFromQueryString(queryString, "signupInvitationCode"));
        setSignupEmailId(Util.getValueFromQueryString(queryString, "signupEmailId"));

        logger.infoAndAddToDb("[sendRequestToSamlIdP] Parsed parameters - email: " + emailId +
            ", signupEmailId: " + (this.signupEmailId != null ? this.signupEmailId : "null") +
            ", signupInvitationCode: " + (this.signupInvitationCode != null ? "present" : "null"));

        if(!DashboardMode.isOnPremDeployment() && emailId.isEmpty()){
            code = "Error, user email cannot be empty";
            logger.error(code);
            logger.errorAndAddToDb("[sendRequestToSamlIdP] Email is empty in SaaS deployment, redirecting to /login");
            servletResponse.sendRedirect("/login");
            return ERROR.toUpperCase();
        }
        logger.info("Trying to sign in for: " + emailId);
        logger.infoAndAddToDb("[sendRequestToSamlIdP] Attempting SSO sign in for email: " + emailId);
        setUserEmail(emailId);

        logger.info("[sendRequestToSamlIdP] Looking up SAML configuration for user");
        SAMLConfig samlConfig = null;
        if(userEmail != null && !userEmail.isEmpty()) {
            logger.info("[sendRequestToSamlIdP] Calling SSOConfigsDao.instance.getSSOConfig for email: " + userEmail);
            samlConfig = SSOConfigsDao.instance.getSSOConfig(userEmail);
            logger.infoAndAddToDb("[sendRequestToSamlIdP] SAML config lookup by email result: " + (samlConfig != null ? "FOUND" : "NOT FOUND"));
        } else if(DashboardMode.isOnPremDeployment()) {
            logger.info("[sendRequestToSamlIdP] On-prem deployment, looking up SAML config by accountId: 1000000");
            samlConfig = SSOConfigsDao.getSAMLConfigByAccountId(1000000);
            logger.infoAndAddToDb("[sendRequestToSamlIdP] SAML config lookup by accountId result: " + (samlConfig != null ? "FOUND" : "NOT FOUND"));
        }

        if(samlConfig == null) {
            code = "Error, cannot login via SSO, trying to login with okta sso";
            logger.error(code);
            logger.infoAndAddToDb("[sendRequestToSamlIdP] No SAML config found, falling back to Okta SSO for email: " + emailId);
            return oktaAuthUrlCreator(emailId);
        }

        int tempAccountId = Integer.parseInt(samlConfig.getId());
        logger.info("Account id: " + tempAccountId + " found for " + emailId);
        logger.infoAndAddToDb("[sendRequestToSamlIdP] SAML config found! AccountId: " + tempAccountId + " for email: " + emailId);
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
        logger.infoAndAddToDb("[oktaAuthUrlCreator] Called for email: " + emailId);
        logger.info("[oktaAuthUrlCreator] signupEmailId: " + (this.signupEmailId != null ? this.signupEmailId : "null") +
            ", signupInvitationCode: " + (this.signupInvitationCode != null ? "present" : "null"));

        logger.info("[oktaAuthUrlCreator] Attempting to get OktaConfig by email domain");
        Config.OktaConfig oktaConfig = Config.getOktaConfig(emailId);

        if(oktaConfig == null) {
            logger.infoAndAddToDb("[oktaAuthUrlCreator] Config.getOktaConfig(emailId) returned NULL for email: " + emailId +
                ". This means no OktaConfig found for user's email domain. Trying fallback to OktaLogin.getInstance()");

            OktaLogin oktaLoginInstance = OktaLogin.getInstance();
            logger.info("[oktaAuthUrlCreator] OktaLogin.getInstance() returned: " + (oktaLoginInstance != null ? "instance found" : "NULL"));

            if(oktaLoginInstance != null) {
                oktaConfig = oktaLoginInstance.getOktaConfig();
                logger.infoAndAddToDb("[oktaAuthUrlCreator] OktaLogin.getInstance().getOktaConfig() returned: " +
                    (oktaConfig != null ? "config found (domain: " + oktaConfig.getOktaDomainUrl() + ", accountId: " + oktaConfig.getAccountId() + ")" : "NULL"));
            }

            if(oktaConfig == null){
                code= "Error, cannot find okta sso for this organization, redirecting to login";
                logger.error(code);
                logger.errorAndAddToDb("[oktaAuthUrlCreator] CRITICAL FAILURE: No OktaConfig found for email: " + emailId +
                    ". Both Config.getOktaConfig() and OktaLogin.getInstance() returned null. " +
                    "This is why new user signup is FAILING. User will be redirected to /login.");
                servletResponse.sendRedirect("/login");
                return ERROR.toUpperCase();
            }
        } else {
            logger.infoAndAddToDb("[oktaAuthUrlCreator] Successfully found OktaConfig by email domain for: " + emailId +
                " -> accountId: " + oktaConfig.getAccountId() +
                ", domain: " + oktaConfig.getOktaDomainUrl() +
                ", organizationDomain: " + oktaConfig.getOrganizationDomain());
        }

        logger.info("[oktaAuthUrlCreator] Calling OktaLogin.getAuthorisationUrl to generate authorization URL");
        String authorisationUrl = OktaLogin.getAuthorisationUrl(emailId, this.signupEmailId, this.signupInvitationCode);

        if(authorisationUrl == null) {
            logger.errorAndAddToDb("[oktaAuthUrlCreator] CRITICAL: OktaLogin.getAuthorisationUrl returned NULL. Cannot redirect user to Okta. Redirecting to /login.");
            servletResponse.sendRedirect("/login");
            return ERROR.toUpperCase();
        }

        logger.infoAndAddToDb("[oktaAuthUrlCreator] Successfully generated authorization URL, redirecting user to Okta: " + authorisationUrl);
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

                    // Extract scope-role mapping if available (new n:n mapping approach)
                    this.scopeRoleMapping = pendingInviteCode.getScopeRoleMapping();
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
            createUserAndRedirectWithDefaultRole(useremail, username, signUpInfo, this.accountId, Config.ConfigType.AZURE.toString(), null);
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

            createUserAndRedirectWithDefaultRole(userEmail, username, signUpInfo, this.accountId, Config.ConfigType.GOOGLE_SAML.toString(), null);
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
                                       int invitationToAccount, String method, Map<String,String> scopeRoleMapping) throws IOException {
        createUserAndRedirect(userEmail, username, signupInfo, invitationToAccount, method, null, scopeRoleMapping);
    }

    private void createUserAndRedirectWithDefaultRole(String userEmail, String username, SignupInfo signupInfo,
                                       int invitationToAccount, String method, Map<String,String> scopeRoleMapping) throws IOException {
        // For new users without explicit invitation, initialize with NO_ACCESS for all scopes
        this.scopeRoleMapping = RBAC.initializeScopeRoleMapping(this.scopeRoleMapping, RBAC.Role.MEMBER.name());
        logger.infoAndAddToDb("[createUserAndRedirectWithDefaultRole] Initialized scopeRoleMapping for new user: " + this.scopeRoleMapping);
        // Pass null as invitedRole so that createUserAndRedirect uses scopeRoleMapping instead of role
        createUserAndRedirect(userEmail, username, signupInfo, invitationToAccount, method, null, scopeRoleMapping);
    }

    // Organization cache initialization - only done once
    private static boolean organizationCacheInitialized = false;
    private static final OrganizationCache organizationCache = new OrganizationCache();

    private void createUserAndRedirect(String userEmail, String username, SignupInfo signupInfo,
                                       int invitationToAccount, String method, String invitedRole, Map<String,String> scopeRoleMapping) throws IOException {
        
        // Trigger organization cache once
        if (!organizationCacheInitialized) {
            synchronized (SignupAction.class) {
                if (!organizationCacheInitialized) {
                    try {
                        logger.infoAndAddToDb("Initializing organization cache scheduler for signup flow", LogDb.DASHBOARD);
                        organizationCache.setUpOrganizationCacheScheduler();
                        organizationCacheInitialized = true;
                        logger.infoAndAddToDb("Organization cache scheduler initialized successfully. Cache will refresh every 10 minutes.", LogDb.DASHBOARD);
                    } catch (Exception e) {
                        logger.errorAndAddToDb(e, "Failed to initialize organization cache: " + e.getMessage(), LogDb.DASHBOARD);
                    }
                }
            }
        }
        
        logger.infoAndAddToDb("[createUserAndRedirect] ========== USER CREATION/LOGIN FLOW ==========");
        logger.infoAndAddToDb("[createUserAndRedirect] Called with parameters:");
        logger.infoAndAddToDb("  - userEmail: " + userEmail);
        logger.infoAndAddToDb("  - username: " + username);
        logger.infoAndAddToDb("  - signupInfo.configType: " + (signupInfo != null ? signupInfo.getConfigType() : "null"));
        logger.infoAndAddToDb("  - invitationToAccount: " + invitationToAccount);
        logger.infoAndAddToDb("  - method: " + method);
        logger.infoAndAddToDb("  - invitedRole: " + (invitedRole != null ? invitedRole : "null"));
        logger.infoAndAddToDb("  - shouldLogin flag: " + shouldLogin);

        logger.info("[createUserAndRedirect] Looking up existing user by email");
        User user = UsersDao.instance.findOne(eq("login", userEmail));
        logger.infoAndAddToDb("[createUserAndRedirect] User lookup result: " + (user != null ? "EXISTING USER FOUND (id: " + user.getId() + ")" : "NEW USER (no existing record)"));
        boolean newOrgSetup = false;
        if (user == null && "false".equalsIgnoreCase(shouldLogin)) {
            logger.infoAndAddToDb("[createUserAndRedirect] Path: NEW USER + shouldLogin=false -> Creating signup record without full account");
            SignupUserInfo signupUserInfo = SignupDao.instance.insertSignUp(userEmail, username, signupInfo, invitationToAccount);
            logger.info("[createUserAndRedirect] Signup record created, logging in user");
            LoginAction.loginUser(signupUserInfo.getUser(), servletResponse, false, servletRequest);
            servletRequest.setAttribute("username", userEmail);
            logger.infoAndAddToDb("[createUserAndRedirect] Redirecting to /dashboard/onboarding");
            servletResponse.sendRedirect("/dashboard/onboarding");
        } else {
            logger.infoAndAddToDb("[createUserAndRedirect] Path: " + (user == null ? "NEW USER" : "EXISTING USER") + " with shouldLogin=true or existing user");
            logger.info("[createUserAndRedirect] invitationToAccount: " + invitationToAccount);

            int accountId = 0;
            Account account = null;
            if (invitationToAccount > 0) {
                logger.info("[createUserAndRedirect] Invitation present, looking up account: " + invitationToAccount);
                account = AccountsDao.instance.findOne("_id", invitationToAccount);
                if (account != null) {
                    accountId = account.getId();
                    logger.infoAndAddToDb("[createUserAndRedirect] Found invited account: " + accountId + " (name: " + account.getName() + ")");
                } else {
                    logger.errorAndAddToDb("[createUserAndRedirect] WARNING: invitationToAccount " + invitationToAccount + " was provided but account not found in database");
                }
            } else {
                logger.info("[createUserAndRedirect] No invitation, will create new account or use existing");
            }

            boolean isSSOLogin = Config.isConfigSSOType(signupInfo.getConfigType());
            logger.infoAndAddToDb("[createUserAndRedirect] Is SSO login: " + isSSOLogin + " (configType: " + signupInfo.getConfigType() + ")");

            if (Utils.shouldBlockNonSsoLogin(accountId, user, userEmail, isSSOLogin)) {
                logger.infoAndAddToDb("[createUserAndRedirect] Blocking non-SSO login for user: " + userEmail);
                if (user != null && user.getSignupInfoMap() != null && user.getSignupInfoMap().containsKey("AUTH0")) {
                    logger.infoAndAddToDb("[createUserAndRedirect] Unsetting AUTH0 signup info for user: " + userEmail);
                    UsersDao.instance.updateOne(eq("login", userEmail), Updates.unset(User.SIGNUP_INFO_MAP + ".AUTH0"));
                }
                logger.infoAndAddToDb("[createUserAndRedirect] Redirecting to SSO login page");
                servletResponse.sendRedirect(SSO_URL);
                return;
            }

            if (user == null) {
                logger.infoAndAddToDb("[createUserAndRedirect] Creating NEW USER account");

                if (accountId == 0) {
                    logger.info("[createUserAndRedirect] No accountId from invitation, checking for existing organization by domain matching");

                    // Extract domain from user email for domain matching
                    String userDomain = null;
                    if (userEmail != null && userEmail.contains("@")) {
                        userDomain = userEmail.split("@")[1].toLowerCase();
                        logger.info("[createUserAndRedirect] User domain extracted: " + userDomain);
                    }
                    com.akto.util.OrganizationInfo matchedOrgInfo = null;

                    if (userDomain != null) {
                        logger.info("[createUserAndRedirect] Searching for existing organizations with matching domain using cache");

                            logger.info("[createUserAndRedirect] Cache populated with " + OrganizationCache.getCacheSize() + " organizations");
                            matchedOrgInfo = OrganizationCache.getOrganizationInfoByDomain(userDomain);
                    }

                    if (matchedOrgInfo != null) {
                        logger.infoAndAddToDb("[createUserAndRedirect] Adding user to existing organization: " + matchedOrgInfo.getOrganizationId() + " with planType: " + matchedOrgInfo.getPlanType());

                        // Fetch organization with projection to get only ACCOUNTS field for performance
                        Organization matchedOrganization = OrganizationsDao.instance.findOne(
                            Filters.eq(Organization.ID, matchedOrgInfo.getOrganizationId()),
                            Projections.include(Organization.ID, Organization.ACCOUNTS)
                        );
                        Set<Integer> orgAccounts = matchedOrganization.getAccounts();
                        logger.info("[createUserAndRedirect] Organization has " + orgAccounts.size() + " account(s)");

                        if (orgAccounts.size() == 1) {
                            // Single account - link user to this account
                            accountId = orgAccounts.iterator().next();
                            logger.infoAndAddToDb("[createUserAndRedirect] Single account organization - linking user to accountId: " + accountId);
                        } else {
                            // Multiple accounts organization - link user to admin's first account
                            logger.info("[createUserAndRedirect] Multiple account organization found, finding admin user");
                            
                            String orgAdminEmail = matchedOrgInfo.getAdminEmail(); // Get admin email from cache
                            
                            // Find the admin user in users collection
                            User adminUser = UsersDao.instance.findOne(eq(User.LOGIN, orgAdminEmail));
                            // Get the first account from admin's account array
                            String firstAdminAccountId = adminUser.getAccounts().keySet().iterator().next();
                            accountId = Integer.parseInt(firstAdminAccountId);
                            logger.infoAndAddToDb("[createUserAndRedirect] Multiple account organization - linking user to admin's first accountId: " + accountId);
                        }
                        invitationToAccount = accountId;
                        if (UsageMetricCalculator.isRbacFeatureAvailable(invitationToAccount)) {
                            invitedRole = fetchDefaultInviteRole(invitationToAccount, RBAC.Role.GUEST.name());
                        }
                    } else {
                        logger.info("[createUserAndRedirect] No matching organization found by domain, creating new account and organization");

                        accountId = AccountAction.createAccountRecord("My account");
                        logger.infoAndAddToDb("[createUserAndRedirect] Created new accountId: " + accountId);

                        // Create organization for new user
                        newOrgSetup = true;
                            logger.info("[createUserAndRedirect] SaaS deployment, creating organization for new user");
                            String organizationUUID = UUID.randomUUID().toString();

                            Set<Integer> organizationAccountsSet = new HashSet<Integer>();
                            organizationAccountsSet.add(accountId);

                            Organization organization = new Organization(organizationUUID, userEmail, userEmail, organizationAccountsSet, false);
                            OrganizationsDao.instance.insertOne(organization);
                            logger.infoAndAddToDb(String.format("[createUserAndRedirect] Created organization %s for new user %s", organizationUUID, userEmail));

                            String adminEmailDomain = userEmail.split("@")[1].toLowerCase();
                            com.akto.util.OrganizationInfo orgInfo = new com.akto.util.OrganizationInfo(organization.getId(), userEmail, null);
                            OrganizationCache.domainToOrgInfoCache.put(adminEmailDomain, orgInfo);

                            Boolean attemptSyncWithAktoSuccess = OrganizationUtils.syncOrganizationWithAkto(organization);
                            logger.infoAndAddToDb(String.format("[createUserAndRedirect] Organization %s for new user %s - Akto sync status: %s", organizationUUID, userEmail, attemptSyncWithAktoSuccess));

                        // Note: If no planType is set on the organization, user will see FreeApp UI
                        logger.info("[createUserAndRedirect] Organization created without planType - user will see FreeApp UI");
                    }
                }

                logger.info("[createUserAndRedirect] Calling UsersDao.instance.insertSignUp to create user record");
                user = UsersDao.instance.insertSignUp(userEmail, username, signupInfo, accountId);
                logger.infoAndAddToDb("[createUserAndRedirect] NEW USER CREATED SUCCESSFULLY - userId: " + user.getId() + ", accountId: " + accountId);

            } else if (StringUtils.isEmpty(code) && !isSSOLogin) {
                if (accountId == 0) {
                    logger.errorAndAddToDb("[createUserAndRedirect] ERROR: accountId is 0 for existing user non-SSO login. This is an invalid state.");
                    throw new IllegalStateException("The account doesn't exist.");
                }
            } else {
                logger.info("[createUserAndRedirect] User: " + user.getLogin() + ", accountId: " + accountId + ", invitedRole: " + invitedRole);

                if((invitedRole != null || scopeRoleMapping!= null) && accountId != 0){
                    logger.info("[createUserAndRedirect] Processing invitation for existing user");
                    // check if the invited account exists in the user info, if not, add it
                    String accountIdStr = String.valueOf(accountId);
                    boolean exists = user.getAccounts().containsKey(accountIdStr);
                    logger.info("[createUserAndRedirect] Account " + accountId + " exists in user's accounts: " + exists);

                    if(!exists){
                        logger.info("[createUserAndRedirect] Adding new account to existing user with role: " + invitedRole);
                        RBAC rbacEntry = new RBAC(user.getId(), invitedRole, accountId);

                        // Set scope-role mapping if available (new n:n mapping approach)
                        if (scopeRoleMapping != null && !scopeRoleMapping.isEmpty()) {
                            rbacEntry.setScopeRoleMapping(scopeRoleMapping);
                        }
                        logger.info("[createUserAndRedirect] Set scope-role mapping for existing user: " + scopeRoleMapping);
                        RBACDao.instance.insertOne(rbacEntry);
                        String accountName = account != null ? account.getName() : "My account";
                        user = UsersDao.addAccount(user.getLogin(), accountId, accountName);
                        logger.infoAndAddToDb("[createUserAndRedirect] Successfully added account " + accountId + " to existing user " + user.getLogin());
                    } else {
                        Bson filter = Filters.and(Filters.eq(RBAC.USER_ID, user.getId()), Filters.eq(RBAC.ACCOUNT_ID, accountId));
                        RBAC existingRbac = RBACDao.instance.findOne(filter);
                        if (existingRbac != null) {
                            Bson update = Updates.set(RBAC.SCOPE_ROLE_MAPPING, scopeRoleMapping);
                            if(scopeRoleMapping == null || scopeRoleMapping.isEmpty()){
                                update = Updates.set(RBAC.ROLE, invitedRole);
                            }
                            RBACDao.instance.updateOne(filter, update);
                        }else{
                            RBAC rbacEntry = new RBAC(user.getId(), invitedRole, accountId);
                            if(scopeRoleMapping != null && !scopeRoleMapping.isEmpty()){
                                rbacEntry.setScopeRoleMapping(scopeRoleMapping);
                            }
                            RBACDao.instance.insertOne(rbacEntry);
                        }

                    servletRequest.getSession().setAttribute("accountId", accountId);
                    logger.info("[createUserAndRedirect] Set session accountId to: " + accountId);
                }
                }
                logger.info("[createUserAndRedirect] Logging in existing user and redirecting to /dashboard/observe/inventory");
                LoginAction.loginUser(user, servletResponse, true, servletRequest, signupInfo);
                servletResponse.sendRedirect("/dashboard/observe/inventory");
                logger.infoAndAddToDb("[createUserAndRedirect] EXISTING USER LOGIN COMPLETED - redirected to inventory");
                return;
            }


            logger.infoAndAddToDb("[createUserAndRedirect] Initializing account for new user");
            if(!newOrgSetup){
                Account oldAccount = AccountsDao.instance.findOne("_id", invitationToAccount);
                user = AccountAction.initializeAccount(userEmail, invitationToAccount, oldAccount.getName(), false, this.scopeRoleMapping);
            }else {
                this.scopeRoleMapping.put("API", RBAC.Role.ADMIN.name());
                this.scopeRoleMapping.put("ENDPOINT", RBAC.Role.ADMIN.name());
                this.scopeRoleMapping.put("DAST", RBAC.Role.ADMIN.name());
                this.scopeRoleMapping.put("AGENTIC", RBAC.Role.ADMIN.name());
                user = AccountAction.initializeAccount(userEmail, accountId, "My account", invitationToAccount == 0, this.scopeRoleMapping);
            }
            logger.infoAndAddToDb("[createUserAndRedirect] Account initialized successfully for accountId: " + accountId);

            // No need to update scope-role mapping separately now since it's set during account initialization
            if (this.scopeRoleMapping != null && !this.scopeRoleMapping.isEmpty()) {
                // Only update if this is an invited user (invitedRole is set) and scope-role mapping needs to be updated
                try {
                    RBACDao.instance.getMCollection().updateOne(
                        Filters.and(
                            Filters.eq(RBAC.USER_ID, user.getId()),
                            Filters.eq(RBAC.ACCOUNT_ID, accountId)
                        ),
                        Updates.set(RBAC.SCOPE_ROLE_MAPPING, this.scopeRoleMapping)
                    );
                    logger.info("[createUserAndRedirect] Set scope-role mapping for invited user: " + this.scopeRoleMapping);
                } catch (Exception e) {
                    logger.errorAndAddToDb(e, "[createUserAndRedirect] Error setting scope-role mapping for invited user: " + e.getMessage());
                }
            }
            RBACDao.instance.deleteUserEntryFromCache(new Pair<>(user.getId(), accountId));


            servletRequest.getSession().setAttribute("user", user);
            servletRequest.getSession().setAttribute("accountId", accountId);
            logger.info("[createUserAndRedirect] Set session attributes - user and accountId: " + accountId);

            logger.info("[createUserAndRedirect] Logging in new user");
            LoginAction.loginUser(user, servletResponse, true, servletRequest);
            servletRequest.setAttribute("username", userEmail);
            logger.infoAndAddToDb("[createUserAndRedirect] Redirecting new user to /dashboard/onboarding");
            servletResponse.sendRedirect("/dashboard/onboarding");

            String dashboardMode = DashboardMode.getActualDashboardMode().toString();
            String distinct_id = userEmail + "_" + dashboardMode;
            logger.info("[createUserAndRedirect] Sending analytics and notifications");
            JSONObject props = new JSONObject();
            props.put("Email ID", userEmail);
            props.put("Email Verified", false);
            props.put("Source", "dashboard");
            props.put("Dashboard Mode", dashboardMode);
            props.put("Invited", invitationToAccount != 0);
            props.put("method", method);

            SlackAlerts newUserJoiningAlert = new NewUserJoiningAlert(userEmail);
            SlackSender.sendAlert(accountId, newUserJoiningAlert, null);
            logger.info("[createUserAndRedirect] Sent Slack alert for new user");

            AktoMixpanel aktoMixpanel = new AktoMixpanel();
            aktoMixpanel.sendEvent(distinct_id, "SIGNUP_SUCCEEDED", props);
            logger.infoAndAddToDb("[createUserAndRedirect] ========== USER CREATION FLOW COMPLETED SUCCESSFULLY ==========");
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