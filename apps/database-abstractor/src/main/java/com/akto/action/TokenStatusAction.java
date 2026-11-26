package com.akto.action;

import com.akto.dao.context.Context;
import com.akto.utils.TokenRegistry;
import com.opensymphony.xwork2.ActionSupport;

import java.util.List;

import lombok.Getter;

@Getter
public class TokenStatusAction extends ActionSupport {

    private List<String> revokedTokenIds;

    // Ids of this account's revoked, not yet expired tokens; polled by services that verify tokens locally.
    public String fetchRevokedTokenIds() {
        revokedTokenIds = TokenRegistry.revokedIdList(Context.accountId.get());
        return SUCCESS.toUpperCase();
    }
}
