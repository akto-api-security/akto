package com.akto.action;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.CustomRole;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC.Role;
import com.akto.dto.SignupInfo;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.notifications.email.SendgridEmail;
import com.akto.util.DashboardMode;
import com.akto.utils.JWT;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;
import com.sendgrid.helpers.mail.Mail;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.springframework.util.StringUtils;

public class InviteUserAction extends UserAction{

    private String inviteeName;
    private String inviteeEmail;
    private String websiteHostName;

    public static final String INVALID_EMAIL_ERROR = "Invalid email";
    public static final String DIFFERENT_ORG_EMAIL_ERROR = "Email must belong to same organisation";
    public static final String NOT_ALLOWED_TO_INVITE = "you're not authorised to invite for this role";
    public static final String AKTO_DOMAIN = "akto.io";

    public static Map<String, String> commonOrganisationsMap = new HashMap<>();
    private static final ExecutorService executor = Executors.newFixedThreadPool(1);

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    static {
        commonOrganisationsMap.put("blinkhealth.com", "blinkhealth.com");
        commonOrganisationsMap.put("blinkrx.com", "blinkhealth.com");
        commonOrganisationsMap.put("hollywoodbets.net ", "betsoftware.com ");
        commonOrganisationsMap.put("betsoftware.com ", "hollywoodbets.net ");
    }

    public static String validateEmail(String email, String adminLogin) {
        if (email == null) return INVALID_EMAIL_ERROR;

        String[] inviteeEmailArr = email.split("@");
        if (inviteeEmailArr.length != 2) {
            return INVALID_EMAIL_ERROR;
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return INVALID_EMAIL_ERROR;
        }

        // validating if same organisation or not
        String[] loginArr = adminLogin.split("@");
        if (loginArr.length != 2) return "Invalid admin login";
        String domain = loginArr[1];
        String inviteeEmailDomain = inviteeEmailArr[1];

        loggerMaker.debugAndAddToDb("inviteeEmailDomain: " + inviteeEmailDomain);
        loggerMaker.debugAndAddToDb("admin domain: " + domain);

        boolean isSameDomainVal = isSameDomain(inviteeEmailDomain, domain);
        loggerMaker.debugAndAddToDb("is same domain: " + isSameDomainVal);

        if (!isSameDomainVal && !inviteeEmailDomain.equals(AKTO_DOMAIN))  {
            return DIFFERENT_ORG_EMAIL_ERROR;
        }

        return null;
    }

    private static boolean isSameDomain(String inviteeDomain, String adminDomain) {
        if (inviteeDomain == null || adminDomain == null) return false;
        if (inviteeDomain.equalsIgnoreCase(adminDomain)) return true;

        if (("consulting-for."+adminDomain).equals(inviteeDomain)) return true;

        String inviteeOrg = commonOrganisationsMap.get(inviteeDomain);
        String adminOrg = commonOrganisationsMap.get(adminDomain);

        loggerMaker.debugAndAddToDb("inviteeOrg: " + inviteeOrg);
        loggerMaker.debugAndAddToDb("adminOrg: " + adminOrg);
        if (inviteeOrg == null || adminOrg == null) return false;

        if (inviteeOrg.equalsIgnoreCase(adminOrg)) return true;

        loggerMaker.debugAndAddToDb("inviteeOrg and adminOrg different");
        return false;
    }

    private String finalInviteCode;
    private String inviteeRole;

    private static final LoggerMaker loggerMaker = new LoggerMaker(InviteUserAction.class, LoggerMaker.LogDb.DASHBOARD);

    @Override
    public String execute() {
        inviteeEmail = inviteeEmail != null ? inviteeEmail.toLowerCase() : null;

        if(inviteeEmail == null) {
            addActionError("Invalid email");
            return ERROR.toUpperCase();
        }

        int user_id = getSUser().getId();
        loggerMaker.debugAndAddToDb(user_id + " inviting " + inviteeEmail);

        Integer accountId = Context.accountId.get();
        User user = UsersDao.instance.findOne(Filters.and(
                Filters.eq(User.LOGIN, inviteeEmail),
                Filters.eq(User.ACCOUNTS+"."+accountId+".accountId", accountId)
        ));
        if(user != null) {
            addActionError("User already exists");
            return ERROR.toUpperCase();
        }


        User admin = UsersDao.instance.getFirstUser(Context.accountId.get());
        if (admin == null) {
            loggerMaker.debugAndAddToDb("admin not found for organization");
            return ERROR.toUpperCase();
        }

        loggerMaker.debugAndAddToDb("admin user: " + admin.getLogin());
        String code = validateEmail(this.inviteeEmail, admin.getLogin());

        if (code != null) {
            addActionError(code);
            return ERROR.toUpperCase();
        }

        Role userRole = RBACDao.getCurrentRoleForUser(user_id, Context.accountId.get());
        Role baseRole = null;

        CustomRole customRole = CustomRoleDao.instance.findRoleByName(this.inviteeRole);

        try {
            if (customRole != null) {
                baseRole = Role.valueOf(customRole.getBaseRole());
            } else {
                baseRole = Role.valueOf(this.inviteeRole);
            }
        } catch (Exception e) {
            addActionError("Invalid role");
            return ERROR.toUpperCase();
        }

        if (!Arrays.asList(userRole.getRoleHierarchy()).contains(baseRole)) {
            addActionError("User not allowed to invite for this role");
            return ERROR.toUpperCase();
        }

        Map<String,Object> claims = new HashMap<>();
        claims.put("email", inviteeEmail);

        String inviteCode;

        try {
            inviteCode = JWT.createJWT(
                    "/home/avneesh/Desktop/akto/dashboard/private.pem",
                    claims,
                    "Akto",
                    "invite_user",
                    Calendar.WEEK_OF_MONTH,
                    1
            );
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            // TODO: find better error
            return Action.ERROR.toUpperCase();
        }

        // to get expiry date

        try {
            Jws<Claims> jws = JWT.parseJwt(inviteCode,"");
            /*
             * There should only be one invite code per user per account.
             * So if we update with upsert:true per account-inviteeEmail
             */
            PendingInviteCodesDao.instance.updateOne(
                    Filters.and(
                        Filters.eq(PendingInviteCode.ACCOUNT_ID, Context.accountId.get()),
                        Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, inviteeEmail)
                    ),
                    Updates.combine(
                        Updates.set(PendingInviteCode.INVITEE_ROLE, this.inviteeRole),
                        Updates.set(PendingInviteCode.INVITE_CODE, inviteCode),
                        Updates.set(PendingInviteCode._EXPIRY, jws.getBody().getExpiration().getTime()),
                        Updates.set(PendingInviteCode._ISSUER, user_id)
                    )
            );
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }

        // check if user who is being invited has sso-signup 
        boolean hasSSOSignup = false;
        if (StringUtils.hasText(inviteeEmail)) {
            User invitedUser = UsersDao.instance.findOne(Filters.and(
                Filters.eq(User.LOGIN, inviteeEmail)
            ));

            if(invitedUser != null && invitedUser.getSignupInfoMap() != null && invitedUser.getSignupInfoMap().size() > 0) {
                for (SignupInfo signupInfo : invitedUser.getSignupInfoMap().values()) {
                    hasSSOSignup = Config.isConfigSSOType(signupInfo.getConfigType());
                    if(hasSSOSignup) {
                        break;
                    }
                }
            }
        }

        String endpoint = DashboardMode.isSaasDeployment() ? "/addUserToAccount" : "/signup";
        if(hasSSOSignup && DashboardMode.isSaasDeployment()) {
            endpoint = "/sso-login";
        }
        finalInviteCode = websiteHostName + endpoint + "?signupInvitationCode=" + inviteCode + "&signupEmailId=" + inviteeEmail;

        String inviteFrom = getSUser().getName();
        Mail email = SendgridEmail.getInstance().buildInvitationEmail(inviteeName, inviteeEmail, inviteFrom, finalInviteCode);
        if (!DashboardMode.isOnPremDeployment()) {
            sendInviteEmail(email);
        } else {
            executor.submit(() -> {
                sendInviteEmail(email);
            });
        }

        return Action.SUCCESS.toUpperCase();
    }

    private void sendInviteEmail(Mail email) {
        try {
            SendgridEmail.getInstance().send(email);
        } catch (IOException e) {
            loggerMaker.errorAndAddToDb("invite email sending failed" + e.getMessage(), LoggerMaker.LogDb.DASHBOARD);
            e.printStackTrace();
        }
    }

    private String invitationCodeToDelete;
    public String deleteInvitationCode() {
        PendingInviteCodesDao.instance.getMCollection().deleteOne(Filters.eq(PendingInviteCode.INVITE_CODE, invitationCodeToDelete));
        return Action.SUCCESS.toUpperCase();
    }

    public void setInvitationCodeToDelete(String invitationCodeToDelete) {
        this.invitationCodeToDelete = invitationCodeToDelete;
    }

    public void setInviteeName(String inviteeName) {
        this.inviteeName = inviteeName;
    }

    public void setInviteeEmail(String inviteeEmail) {
        this.inviteeEmail = inviteeEmail;
    }

    public void setWebsiteHostName(String websiteHostName) {
        this.websiteHostName = websiteHostName;
    }

    public String getFinalInviteCode() {
        return finalInviteCode;
    }

    public String getInviteeRole() {
        return inviteeRole;
    }

    public void setInviteeRole(String inviteeRole) {
        this.inviteeRole = inviteeRole;
    }
}
