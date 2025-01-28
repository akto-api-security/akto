package com.akto.dto;

import org.bson.types.ObjectId;

public class PendingInviteCode {

    private ObjectId id;
    private String inviteCode;
    public static final String INVITE_CODE = "inviteCode";    
    private int issuer;
    public static final String _ISSUER = "issuer";
    private String inviteeEmailId;
    public static final String INVITEE_EMAIL_ID = "inviteeEmailId";
    private long expiry;
    public static final String _EXPIRY = "expiry";
    private int accountId;
    public static final String ACCOUNT_ID = "accountId";
    private String inviteeRole;
    public static final String INVITEE_ROLE = "inviteeRole";

    public PendingInviteCode() {
    }

    public PendingInviteCode(String inviteCode, int issuer, String inviteeEmailId, long expiry, int accountId) {
        this.inviteCode = inviteCode;
        this.issuer = issuer;
        this.inviteeEmailId = inviteeEmailId;
        this.expiry = expiry;
        this.accountId = accountId;
        this.inviteeRole = RBAC.Role.GUEST.name();
    }

    public PendingInviteCode(String inviteCode, int issuer, String inviteeEmailId, long expiry, int accountId, String inviteeRole) {
        this.inviteCode = inviteCode;
        this.issuer = issuer;
        this.inviteeEmailId = inviteeEmailId;
        this.expiry = expiry;
        this.accountId = accountId;
        this.inviteeRole = inviteeRole;
    }
    public ObjectId getId() {
        return id;
    }

    public void setId(ObjectId id) {
        this.id = id;
    }

    public String getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public int getIssuer() {
        return issuer;
    }

    public void setIssuer(int issuer) {
        this.issuer = issuer;
    }

    public String getInviteeEmailId() {
        return inviteeEmailId;
    }

    public void setInviteeEmailId(String inviteeEmailId) {
        this.inviteeEmailId = inviteeEmailId;
    }

    public long getExpiry() {
        return expiry;
    }

    public void setExpiry(long expiry) {
        this.expiry = expiry;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public String getInviteeRole() {
        return inviteeRole;
    }

    public void setInviteeRole(String inviteeRole) {
        this.inviteeRole = inviteeRole;
    }
}
