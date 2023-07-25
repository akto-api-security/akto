package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.listener.InitializerListener;
import com.akto.utils.Auth0;
import com.akto.notifications.email.WelcomeEmail;
import com.akto.utils.JWT;
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
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.sendgrid.helpers.mail.Mail;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.request.oauth.OAuthV2AccessRequest;
import com.slack.api.methods.request.users.UsersIdentityRequest;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import com.slack.api.methods.response.users.UsersIdentityResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import org.apache.commons.lang3.StringUtils;
import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.conversions.Bson;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static com.akto.dao.MCollection.SET;
import static com.mongodb.client.model.Filters.all;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

public class SignupAction implements Action, ServletResponseAware, ServletRequestAware {

    public static final String SIGN_IN = "signin";
    public static final String CHECK_INBOX_URI = "/check-inbox";
    public static final String BUSINESS_EMAIL_URI = "/business-email";
    public static final String TEST_EDITOR_URL = "/tools/test-editor";
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
        String codeFromSlack = code;
        code = "err";
        System.out.println(code+ " " +state);

        Config.SlackConfig aktoSlackConfig = (Config.SlackConfig) ConfigsDao.instance.findOne("_id", "SLACK-ankush");

        if(aktoSlackConfig == null) {
            Config.SlackConfig newConfig = new Config.SlackConfig();
            newConfig.setClientId("1966181353905.1950504597541");
            newConfig.setClientSecret("2b586ffd4fe4bdccd35a8675b43c1e91");
            newConfig.setRedirect_url("");
            ConfigsDao.instance.insertOne(newConfig);
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
                    createUserAndRedirect(userEmail, userName, info, 0);
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

        String body = new String(java.util.Base64.getDecoder().decode(base64EncodedBody));
        JSONObject jsonBody = new JSONObject(body);
        System.out.print(jsonBody);
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
                createUserAndRedirect(email, name, auth0SignupInfo, pendingInviteCode.getAccountId());
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
        createUserAndRedirect(email, name, auth0SignupInfo, 0);
        code = "";
        System.out.println("Executed registerViaAuth0");
        return SUCCESS.toUpperCase();
    }

    public String registerViaGoogle() {

        System.out.println(code + " " + state);

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
            createUserAndRedirect(userEmail, username, signupInfo, 0);
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
            createUserAndRedirect(email, email, signupInfo, invitedToAccountId);
        } catch (IOException e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
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
                                       int invitationToAccount) throws IOException {
        User user = UsersDao.instance.findOne(eq("login", userEmail));
        if (user == null && "false".equalsIgnoreCase(shouldLogin)) {
            SignupUserInfo signupUserInfo = SignupDao.instance.insertSignUp(userEmail, username, signupInfo, invitationToAccount);
            LoginAction.loginUser(signupUserInfo.getUser(), servletResponse, false, servletRequest);
            servletResponse.sendRedirect("/dashboard/onboarding");
        } else {

            int accountId = 0;
            if (invitationToAccount > 0) {
                Account account = AccountsDao.instance.findOne("_id", invitationToAccount);
                if (account != null) {
                    accountId = account.getId();
                }
            }

            if (user == null) {

                if (accountId == 0 || invitationToAccount == 0) {
                    accountId = AccountAction.createAccountRecord("My account");
                }
                user = UsersDao.instance.insertSignUp(userEmail, username, signupInfo, accountId);

            } else if (StringUtils.isEmpty(code)) {
                if (accountId == 0) {
                    throw new IllegalStateException("The account doesn't exist.");
                }
            } else {
                LoginAction.loginUser(user, servletResponse, true, servletRequest);
                servletResponse.sendRedirect("/dashboard/observe/inventory");
                return;
            }

            user = AccountAction.initializeAccount(userEmail, accountId, "My account",invitationToAccount == 0);

            servletRequest.getSession().setAttribute("user", user);
            servletRequest.getSession().setAttribute("accountId", accountId);
            LoginAction.loginUser(user, servletResponse, true, servletRequest);
            servletResponse.sendRedirect("/dashboard/onboarding");
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
}
