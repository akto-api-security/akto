package com.akto.interceptor;

import com.akto.dao.RBACDao;
import com.akto.dto.User;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.log.LoggerMaker;
import com.mongodb.client.model.Filters;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

import java.util.Map;

public class RoleAccessInterceptor extends AbstractInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(RoleAccessInterceptor.class, LoggerMaker.LogDb.DASHBOARD);

    String featureLabel;
    String accessType;

    public void setFeatureLabel(String featureLabel) {
        this.featureLabel = featureLabel;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public final static String FORBIDDEN = "FORBIDDEN";
    private final static String USER_ID = "userId";
    private final static String USER = "user";


    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        try {
            if(featureLabel == null) {
                throw new Exception("Feature list is null or empty");
            }

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

            Role userRoleType = Role.valueOf(userRole.toUpperCase());
            Feature featureType = Feature.valueOf(this.featureLabel.toUpperCase());

            ReadWriteAccess accessGiven = userRoleType.getReadWriteAccessForFeature(featureType);
            boolean hasRequiredAccess = false;

            if(this.accessType.equalsIgnoreCase(ReadWriteAccess.READ.toString()) || this.accessType.equalsIgnoreCase(accessGiven.toString())){
                hasRequiredAccess = true;
            }
            
            if(!hasRequiredAccess) {
                ((ActionSupport) invocation.getAction()).addActionError("The role '" + userRoleType.getName() + "' does not have access.");
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
