package com.akto.interceptor;

import java.util.Map;

import org.springframework.util.StringUtils;

import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.util.Constants;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

public class InternalDashboardInterceptor extends AbstractInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(InternalDashboardInterceptor.class, LoggerMaker.LogDb.DASHBOARD);

    
    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        try {

            Map<String, Object> session = invocation.getInvocationContext().getSession();
            
            if(session == null){
                throw new Exception("Found session null, returning from interceptor");
            }
            User user = (User) session.get("user");
            if(user == null){
                throw new Exception("Found user null, returning from interceptor");
            }

            String login = user.getLogin();
            if(!login.contains("@akto.io")){
                return RoleAccessInterceptor.FORBIDDEN;
            }

            String accountId = String.valueOf(RoleAccessInterceptor.getUserAccountId(session));
            if(!StringUtils.hasLength(Constants.ALLOWED_ACCOUNT_IDS_FOR_INTERNAL_DASHBOARD) || !Constants.ALLOWED_ACCOUNT_IDS_FOR_INTERNAL_DASHBOARD.contains(accountId)){
                return RoleAccessInterceptor.FORBIDDEN;
            }

        } catch(Exception e) {
            String api = invocation.getProxy().getActionName();
            String error = "Error in RoleInterceptor for api: " + api + " ERROR: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, error, LoggerMaker.LogDb.DASHBOARD);
            return RoleAccessInterceptor.FORBIDDEN;
        }

        String result = invocation.invoke();
        return result;
    }
}

