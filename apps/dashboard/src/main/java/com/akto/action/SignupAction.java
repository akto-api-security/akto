package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.listener.InitializerListener;
import com.akto.utils.JWT;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.mongodb.BasicDBObject;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

import static com.mongodb.client.model.Filters.all;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

public class SignupAction implements Action, ServletResponseAware, ServletRequestAware {

    public static final String SIGN_IN = "signin";

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

        Config.SlackConfig aktoSlackConfig = (Config.SlackConfig) ConfigsDao.instance.findOne("_id", "SLACK-ankush");

        if(aktoSlackConfig == null) {
            return Action.ERROR.toUpperCase();
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
            ;
            code = e.getMessage();
        } finally {
            if (code.length() > 0) {
                return Action.ERROR.toUpperCase();
            } else {
                return Action.SUCCESS.toUpperCase();
            }
        }

    }

    public String registerViaGoogle() {

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

        GoogleTokenResponse tokenResponse =
                null;
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
            createUserAndRedirect(userEmail, username, signupInfo, 0);
            code = "";
        } catch (IOException e) {
            code = e.getMessage();
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
            ;
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
            new LoginAction().loginUser(user, servletResponse, true, servletRequest);
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
