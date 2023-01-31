package com.akto.dto;

import org.bson.types.ObjectId;

public class PendingInviteCode {

    private ObjectId id;
    private String inviteCode;
    public static final String INVITE_CODE = "inviteCode";
    private int issuer;
    private String inviteeEmailId;
    private long expiry;

    public PendingInviteCode() {
    }

    public PendingInviteCode(String inviteCode, int issuer, String inviteeEmailId, long expiry) {
        this.inviteCode = inviteCode;
        this.issuer = issuer;
        this.inviteeEmailId = inviteeEmailId;
        this.expiry = expiry;
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
}
