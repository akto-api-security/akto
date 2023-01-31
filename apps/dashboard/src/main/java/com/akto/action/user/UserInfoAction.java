package com.akto.action.user;

import com.akto.action.UserAction;
import com.akto.dao.UsersDao;
import com.opensymphony.xwork2.Action;

public class UserInfoAction extends UserAction {

    @Override
    public String execute() throws Exception {
        return Action.SUCCESS;
    }

    private Integer lastLoginTs;
    public String fetchUserLastLoginTs() {
        lastLoginTs = UsersDao.instance.fetchUserLasLoginTs(getSUser().getId());
        if (lastLoginTs == null) return ERROR.toUpperCase();
        return Action.SUCCESS.toUpperCase();
    }

    public Integer getLastLoginTs() {
        return lastLoginTs;
    }
}
