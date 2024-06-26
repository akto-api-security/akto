package com.akto.dto;

import org.bson.types.ObjectId;

public class PendingInviteCode {

    private ObjectId id;
    private String inviteCode;
    public static final String INVITE_CODE = "inviteCode";
    private int issuer;
    private String inviteeEmailId;
    private long expiry;
    private int accountId;
    private RBAC.Role inviteeRole;

    public PendingInviteCode() {
    }

    public PendingInviteCode(String inviteCode, int issuer, String inviteeEmailId, long expiry, int accountId) {
        this.inviteCode = inviteCode;
        this.issuer = issuer;
        this.inviteeEmailId = inviteeEmailId;
        this.expiry = expiry;
        this.accountId = accountId;
        this.inviteeRole = RBAC.Role.GUEST;
    }

    public PendingInviteCode(String inviteCode, int issuer, String inviteeEmailId, long expiry, int accountId, RBAC.Role inviteeRole) {
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

    public RBAC.Role getInviteeRole() {
        return inviteeRole;
    }

    public void setInviteeRole(RBAC.Role inviteeRole) {
        this.inviteeRole = inviteeRole;
    }
}
