package com.akto.action;

import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
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

    public static String validateEmail(String email, String adminLogin) {
        if (email == null) return INVALID_EMAIL_ERROR;

        String[] inviteeEmailArr = email.split("@");
        if (inviteeEmailArr.length != 2) {
            return INVALID_EMAIL_ERROR;
        }

        // validating if same organisation or not
        String[] loginArr = adminLogin.split("@");
        if (loginArr.length != 2) return "Invalid admin login";
        String domain = loginArr[1];
        String inviteeEmailDomain = inviteeEmailArr[1];

        loggerMaker.infoAndAddToDb("inviteeEmailDomain: " + inviteeEmailDomain);
        loggerMaker.infoAndAddToDb("admin domain: " + domain);

        boolean isSameDomainVal = isSameDomain(inviteeEmailDomain, domain);
        loggerMaker.infoAndAddToDb("is same domain: " + isSameDomainVal);

        if (!isSameDomainVal && !inviteeEmailDomain.equals(AKTO_DOMAIN))  {
            return DIFFERENT_ORG_EMAIL_ERROR;
        }

        return null;
    }

    private static boolean isSameDomain(String inviteeDomain, String adminDomain) {
        if (inviteeDomain == null || adminDomain == null) return false;
        if (inviteeDomain.equalsIgnoreCase(adminDomain)) return true;

        String inviteeOrg = commonOrganisationsMap.get(inviteeDomain);
        String adminOrg = commonOrganisationsMap.get(adminDomain);

        loggerMaker.infoAndAddToDb("inviteeOrg: " + inviteeOrg);
        loggerMaker.infoAndAddToDb("adminOrg: " + adminOrg);
        if (inviteeOrg == null || adminOrg == null) return false;

        if (inviteeOrg.equalsIgnoreCase(adminOrg)) return true;

        loggerMaker.infoAndAddToDb("inviteeOrg and adminOrg different");
        return false;
    }

    private String finalInviteCode;
    private RBAC.Role inviteeRole;

    private static final LoggerMaker loggerMaker = new LoggerMaker(InviteUserAction.class, LoggerMaker.LogDb.DASHBOARD);

    @Override
    public String execute() {
        int user_id = getSUser().getId();
        loggerMaker.infoAndAddToDb(user_id + " inviting " + inviteeEmail);

        User admin = UsersDao.instance.getFirstUser(Context.accountId.get());
        if (admin == null) {
            loggerMaker.infoAndAddToDb("admin not found for organization");
            return ERROR.toUpperCase();
        }

        loggerMaker.infoAndAddToDb("admin user: " + admin.getLogin());
        String code = validateEmail(this.inviteeEmail, admin.getLogin());

        if (code != null) {
            addActionError(code);
            return ERROR.toUpperCase();
        }

        RBAC.Role userRole = RBACDao.getCurrentRoleForUser(user_id, Context.accountId.get());

        if (!Arrays.asList(userRole.getRoleHierarchy()).contains(this.inviteeRole)) {
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

        String endpoint = DashboardMode.isSaasDeployment() ? "/addUserToAccount" : "/signup";
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

    public RBAC.Role getInviteeRole() {
        return inviteeRole;
    }

    public void setInviteeRole(RBAC.Role inviteeRole) {
        this.inviteeRole = inviteeRole;
    }
}
