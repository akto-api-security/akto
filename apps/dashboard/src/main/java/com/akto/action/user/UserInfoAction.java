package com.akto.action.user;

import com.akto.action.UserAction;
import com.akto.dao.UsersDao;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.opensymphony.xwork2.Action;

public class UserInfoAction extends UserAction {

    private String aktoUIMode;
    private static final LoggerMaker loggerMaker = new LoggerMaker(UserInfoAction.class, LogDb.DASHBOARD);;

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

    public String updateAktoUIMode() {
        if (aktoUIMode == null) {
            return ERROR.toUpperCase();
        }
        try {
            User.AktoUIMode mode = User.AktoUIMode.valueOf(aktoUIMode);
            User user = getSUser();
            UsersDao.instance.updateOne(Filters.eq("_id", user.getId()),
                    Updates.set(User.AKTO_UI_MODE, mode.name()));
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, e.toString(), LoggerMaker.LogDb.DASHBOARD);
        }
        return Action.SUCCESS.toUpperCase();
    }

    public Integer getLastLoginTs() {
        return lastLoginTs;
    }

    public String getAktoUIMode() {
        return aktoUIMode;
    }

    public void setAktoUIMode(String aktoUIMode) {
        this.aktoUIMode = aktoUIMode;
    }
}
