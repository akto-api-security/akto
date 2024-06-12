package com.akto.interceptor;

import com.akto.dao.RBACDao;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.util.DashboardMode;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RoleAccessInterceptor extends AbstractInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(RoleAccessInterceptor.class, LoggerMaker.LogDb.DASHBOARD);

    String rolesList;

    public final static String FORBIDDEN = "FORBIDDEN";
    private final static String USER_ID = "userId";
    private final static String USER = "user";

    public void setRolesList(String rolesList) {
        this.rolesList = rolesList;
    }

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        try {
            if(!DashboardMode.isMetered()) {
                return invocation.invoke();
            }

            if(rolesList == null || rolesList.trim().isEmpty()) {
                throw new Exception("Roles list is null or empty");
            }

            List<String> roles = Arrays.stream(rolesList.split(" "))
                                        .map(String::trim)
                                        .map(String::toUpperCase)
                                        .collect(Collectors.toList());

            Map<String, Object> session = invocation.getInvocationContext().getSession();
            User user = (User) session.get(USER);

            if(user == null) {
                throw new Exception("User not found in session");
            }

            int userId = user.getId();

            String userRole = RBACDao.instance.findOne(Filters.eq(USER_ID, userId)).getRole().name().toUpperCase();

            if(userRole == null || userRole.isEmpty()) {
                throw new Exception("User role not found");
            }

            boolean hasRequiredRole = false;

            for(String requiredRole : roles) {
                if (RoleHierarchy.hasRole(userRole, requiredRole)) {
                    hasRequiredRole = true;
                    break;
                }
            }

            if(!hasRequiredRole) {
                ((ActionSupport) invocation.getAction()).addActionError("The role '" + userRole + "' does not have access.");
                return FORBIDDEN;
            }
        } catch(Exception e) {
            String api = invocation.getProxy().getActionName();
            String error = "Error in RoleInterceptor for api: " + api + " ERROR: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, error, LoggerMaker.LogDb.DASHBOARD);
        }

        return invocation.invoke();
    }
}
