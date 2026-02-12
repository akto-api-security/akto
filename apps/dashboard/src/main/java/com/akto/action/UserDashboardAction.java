package com.akto.action;

import com.akto.dao.UserDashboardDao;
import com.akto.dao.context.Context;
import com.akto.dto.UserDashboard;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;

import lombok.Getter;
import lombok.Setter;

public class UserDashboardAction extends UserAction {

    private static final LoggerMaker logger = new LoggerMaker(UserDashboardAction.class, LogDb.DASHBOARD);

    @Getter
    @Setter
    private String screenName;

    @Getter
    @Setter
    private String layout;

    public String saveDashboardLayout() {
        try {
            int userId = Context.userId.get();

            UserDashboard userDashboard = new UserDashboard(screenName, userId, layout);

            UserDashboardDao.instance.replaceOne(
                UserDashboardDao.generateFilter(screenName, userId),
                userDashboard
            );

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error saving dashboard layout for user", e);
            return ERROR.toUpperCase();
        }
    }

    public String fetchDashboardLayout() {
        try {
            int userId = Context.userId.get();

            UserDashboard userDashboard = UserDashboardDao.instance.findOne(
                UserDashboardDao.generateFilter(screenName, userId)
            );

            this.layout = userDashboard != null ? userDashboard.getLayout() : null;
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            logger.error("Error fetching dashboard layout for user", e);
            return ERROR.toUpperCase();
        }
    }
}
