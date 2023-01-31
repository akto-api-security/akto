package com.akto.dao;

import com.akto.dto.PendingInviteCode;

public class PendingInviteCodesDao extends CommonContextDao<PendingInviteCode>{

    public static PendingInviteCodesDao instance = new PendingInviteCodesDao();

    @Override
    public String getCollName() {
        return "invite_codes";
    }

    @Override
    public Class<PendingInviteCode> getClassT() {
        return PendingInviteCode.class;
    }
}
