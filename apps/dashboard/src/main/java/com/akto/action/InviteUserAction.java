package com.akto.action;

import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.notifications.email.SendgridEmail;
import com.akto.utils.JWT;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.Action;
import com.sendgrid.helpers.mail.Mail;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class InviteUserAction extends UserAction{

    private String inviteeName;
    private String inviteeEmail;
    private String websiteHostName;

    public static final String INVALID_EMAIL_ERROR = "Invalid email";
    public static final String DIFFERENT_ORG_EMAIL_ERROR = "Email must belong to same organisation";

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

        if (!domain.equals(inviteeEmailDomain)) {
            return DIFFERENT_ORG_EMAIL_ERROR;
        }

        return null;
    }

    private String finalInviteCode;

    @Override
    public String execute() {
        int user_id = getSUser().getId();

        User admin = UsersDao.instance.getFirstUser();
        String code = validateEmail(this.inviteeEmail, admin.getLogin());

        if (code != null) {
            addActionError(code);
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
            PendingInviteCodesDao.instance.insertOne(
                    new PendingInviteCode(inviteCode, user_id, inviteeEmail,jws.getBody().getExpiration().getTime())
            );
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }

        finalInviteCode = websiteHostName + "/signup?signupInvitationCode=" + inviteCode + "&signupEmailId=" + inviteeEmail;
        String inviteFrom = getSUser().getName();
        Mail email = SendgridEmail.buildInvitationEmail(inviteeName, inviteeEmail, inviteFrom, finalInviteCode);
        try {
            SendgridEmail.send(email);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
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
}
