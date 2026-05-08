package com.akto.interceptor;

import com.akto.audit_logs_util.AuditLogsUtil;
import com.akto.dao.RBACDao;
import com.akto.dao.audit_logs.ApiAuditLogsDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.audit_logs.ApiAuditLogs;
import com.akto.dto.rbac.RbacEnums;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.filter.UserDetailsFilter;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.policies.UserAgentTypePolicy;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;

public class RoleAccessInterceptor extends AbstractInterceptor {

    private static final LoggerMaker loggerMaker = new LoggerMaker(RoleAccessInterceptor.class, LoggerMaker.LogDb.DASHBOARD);
    private static final LoggerMaker logger = new LoggerMaker(RoleAccessInterceptor.class, LogDb.DASHBOARD);
    String featureLabel;
    String accessType;
    String actionDescription;

    public void setFeatureLabel(String featureLabel) {
        this.featureLabel = featureLabel;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public void setActionDescription(String actionDescription) {
        this.actionDescription = actionDescription;
    }

    public final static String FORBIDDEN = "FORBIDDEN";
    public final static String USER = "user";

    private int getUserAccountId (Map<String, Object> session) throws Exception{
        try {
            Object accountIdObj = session.get(UserDetailsFilter.ACCOUNT_ID);
            String accountIdStr = accountIdObj == null ? null : accountIdObj+"";
            if(accountIdStr == null){
                throw new Exception("found account id as null in interceptor");
            }
            int accountId = Integer.parseInt(accountIdStr);
            return accountId;
        } catch (Exception e) {
            throw new Exception("unable to parse account id: " + e.getMessage());
        }
    }

    @Override
    public String intercept(ActionInvocation invocation) throws Exception {
        ApiAuditLogs apiAuditLogs = null;
        int timeNow = Context.now();
        try {
            HttpServletRequest request = ServletActionContext.getRequest();
            if(featureLabel == null) {
                throw new Exception("Feature list is null or empty");
            }

            Map<String, Object> session = invocation.getInvocationContext().getSession();
            logger.debug("Found session from request in : " + (Context.now() - timeNow));
            timeNow = Context.now();
            
            if(session == null){
                throw new Exception("Found session null, returning from interceptor");
            }
            loggerMaker.debugAndAddToDb("Found session in interceptor.", LogDb.DASHBOARD);
            User user = (User) session.get(USER);

            if(user == null) {
                throw new Exception("User not found in session, returning from interceptor");
            }
            int sessionAccId = getUserAccountId(session);

            logger.debug("Found sessionId in : " + (Context.now() - timeNow));
            timeNow = Context.now();


            if(!DashboardMode.isMetered()){
                return invocation.invoke();
            }

            if(!(UsageMetricCalculator.isRbacFeatureAvailable(sessionAccId) || featureLabel.equalsIgnoreCase(RbacEnums.Feature.ADMIN_ACTIONS.toString()))){
                logger.debug("Time by feature label check in: " + (Context.now() - timeNow));
                return invocation.invoke();
            }

            logger.debug("Time by feature label check in: " + (Context.now() - timeNow));
            timeNow = Context.now();

            loggerMaker.debugAndAddToDb("Found user in interceptor: " + user.getLogin(), LogDb.DASHBOARD);
            int userId = user.getId();

            Role userRoleRecord = RBACDao.getCurrentRoleForUser(userId, sessionAccId);
            logger.debug("Found user role in: " + (Context.now() - timeNow));
            String userRole = userRoleRecord != null ? userRoleRecord.getName().toUpperCase() : "";

            if(userRole == null || userRole.isEmpty()) {
                throw new Exception("User role not found");
            }

            Feature featureType = Feature.valueOf(this.featureLabel.toUpperCase());

            ReadWriteAccess accessGiven = userRoleRecord.getReadWriteAccessForFeature(featureType);
            boolean hasRequiredAccess = false;

            if(this.accessType.equalsIgnoreCase(ReadWriteAccess.READ.toString()) || this.accessType.equalsIgnoreCase(accessGiven.toString())){
                hasRequiredAccess = !accessGiven.equals(ReadWriteAccess.NO_ACCESS);
            }
            if(featureLabel.equals(Feature.ADMIN_ACTIONS.name())){
                hasRequiredAccess = userRole.equals(Role.ADMIN.name());
            }

            if(!hasRequiredAccess) {
                ((ActionSupport) invocation.getAction()).addActionError("The role '" + userRole + "' does not have access.");
                return FORBIDDEN;
            }

            try {
                if (this.accessType.equalsIgnoreCase(ReadWriteAccess.READ_WRITE.toString())) {
                    long timestamp = Context.now();
                    String apiEndpoint = invocation.getProxy().getActionName();
                    String actionDescription = this.actionDescription == null ? "Error: Description not available" : this.actionDescription;
                    String userEmail = user.getLogin();
                    String userAgent = request.getHeader("User-Agent") == null ? "Unknown User-Agent" : request.getHeader("User-Agent");
                    UserAgentTypePolicy.ClientType userAgentType = UserAgentTypePolicy.findUserAgentType(userAgent);
                    List<String> userProxyIpAddresses = AuditLogsUtil.getClientIpAddresses(request);
                    String userIpAddress = userProxyIpAddresses.get(0);

                    apiAuditLogs = new ApiAuditLogs(timestamp, apiEndpoint, actionDescription, userEmail, userAgentType.name(), userIpAddress, userProxyIpAddresses);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while inserting api audit logs: " + e.getMessage(), LogDb.DASHBOARD);
            }

        } catch(Exception e) {
            String api = invocation.getProxy().getActionName();
            String error = "Error in RoleInterceptor for api: " + api + " ERROR: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, error, LoggerMaker.LogDb.DASHBOARD);
        }

        String result = invocation.invoke();

        if (apiAuditLogs != null && result.equalsIgnoreCase(Action.SUCCESS.toUpperCase())) {
            ApiAuditLogsDao.instance.insertOne(apiAuditLogs);
        }

        return result;
    }
}
