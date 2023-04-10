package com.akto.action;

import com.akto.dao.*;
import com.akto.dao.context.Context;
import com.akto.dto.*;
import com.akto.listener.InitializerListener;
import com.akto.notifications.email.WelcomeEmail;
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
                    createUserAndRedirect(userEmail, userName, info);
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

    public String registerViaGoogle() {

        System.out.println(code + " " + state);

        String codeFromGoogle = code;
        code = "err";

        Config.GoogleConfig aktoGoogleConfig = (Config.GoogleConfig) ConfigsDao.instance.findOne("_id", "GOOGLE-ankush");
        if (aktoGoogleConfig == null) {
            Config.GoogleConfig newConfig = new Config.GoogleConfig();
            newConfig.setClientId("323545309385-7nr1afdhf4kautv9bridgadh19krfkn5.apps.googleusercontent.com");
            newConfig.setProjectId("my-project-1523474847276");
            newConfig.setAuthURI("https://accounts.google.com/o/oauth2/auth");
            newConfig.setCertURL("https://www.googleapis.com/oauth2/v1/certs");
            newConfig.setJsOrigins(InitializerListener.getDomain());
            newConfig.setClientSecret("gRUVh9tRfWg2vDbYK1fBbsE8");
            newConfig.setTokenURI("https://oauth2.googleapis.com/token");
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
                    InitializerListener.getDomain()+"/signup-google")
                    .execute();

            String accessToken = tokenResponse.getAccessToken();
            String refreshToken = tokenResponse.getRefreshToken();
            GoogleIdToken.Payload payload = tokenResponse.parseIdToken().getPayload();

            String username = (String) payload.get("name");
            String userEmail = payload.getEmail();

            SignupInfo.GoogleSignupInfo signupInfo = new SignupInfo.GoogleSignupInfo(aktoGoogleConfig.getId(), accessToken, refreshToken, tokenResponse.getExpiresInSeconds());
            createUserAndRedirect(userEmail, username, signupInfo);
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
        long count = UsersDao.instance.getMCollection().countDocuments();
        // only 1st user is allowed to signup without invitationCode
        if (count != 0) {
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

            if (UsersDao.instance.findOne("login", email) != null) {
                code = "This ";
                return ERROR.toUpperCase();
            }
        }

        String salt = "39yu";
        String passHash = Integer.toString((salt + password).hashCode());

        SignupInfo.PasswordHashInfo signupInfo = new SignupInfo.PasswordHashInfo(passHash, salt);
        try {
            createUserAndRedirect(email, email, signupInfo);
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

    private void createUserAndRedirect(String userEmail, String username, SignupInfo signupInfo) throws IOException {
            String accountName = System.getenv("AKTO_ACCOUNT_NAME");
            if (accountName == null) accountName = "Helios";
            Account account = AccountsDao.instance.findOne("name", accountName);
            if(!SIGN_IN.equals(state)) {
                if (account == null) {
                    account = new Account(1000000, accountName);
                    AccountsDao.instance.insertOne(account);
                }
            }

            int accountId = account.getId();
            User user = UsersDao.instance.insertSignUp(userEmail, username, signupInfo, accountId);
            long count = UsersDao.instance.getMCollection().countDocuments();
            // if first user then automatic admin
            // else check if rbac is 0 or not. If 0 then make the user that was created first as admin.
            // done for customers who were there before rbac feature
            if (count == 1) {
                RBACDao.instance.insertOne(new RBAC(user.getId(), RBAC.Role.ADMIN));
            } else {
                long rbacCount = RBACDao.instance.getMCollection().countDocuments();
                if (rbacCount == 0) {
                    MongoCursor<User> cursor = UsersDao.instance.getMCollection().find().sort(Sorts.ascending("_id")).limit(1).cursor();
                    if (cursor.hasNext()) {
                        User firstUser = cursor.next();
                        RBACDao.instance.insertOne(new RBAC(firstUser.getId(), RBAC.Role.ADMIN));
                    }
                }
            }

            Context.accountId.set(accountId);
            AccountSettingsDao.instance.updateOnboardingFlag(true);
            servletRequest.getSession().setAttribute("user", user);
            new LoginAction().loginUser(user, servletResponse, true, servletRequest);
            servletResponse.sendRedirect("/dashboard/onboarding");

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
