package com.akto.dao;

import com.akto.dto.UserDashboard;
import com.mongodb.client.model.Filters;
import org.bson.conversions.Bson;

public class UserDashboardDao extends AccountsContextDao<UserDashboard> {

    public static final UserDashboardDao instance = new UserDashboardDao();

    @Override
    public String getCollName() {
        return "user_dashboards";
    }

    @Override
    public Class<UserDashboard> getClassT() {
        return UserDashboard.class;
    }

    public static Bson generateFilter(String screenName, int userId) {
        return Filters.and(
            Filters.eq(UserDashboard.SCREEN_NAME, screenName),
            Filters.eq(UserDashboard.USER_ID, userId)
        );
    }
}
