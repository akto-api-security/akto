package com.akto.interceptor;

import com.akto.audit_logs_util.Audit;
import com.akto.audit_logs_util.AuditLogsUtil;
import com.akto.dao.RBACDao;
import com.akto.dao.audit_logs.ApiAuditLogsDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.audit_logs.ApiAuditLogs;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.rbac.RbacEnums;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.filter.UserDetailsFilter;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.runtime.policies.UserAgentTypePolicy;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.akto.utils.AlertUtils;
import com.akto.notifications.slack.SlackAlerts;
import com.akto.notifications.slack.UserBlockedNoScopeAccessAlert;
import com.akto.notifications.slack.SlackSender;
import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import com.opensymphony.xwork2.ActionInvocation;
import com.opensymphony.xwork2.ActionProxy;
import com.opensymphony.xwork2.ActionSupport;
import com.opensymphony.xwork2.config.entities.ActionConfig;
import com.opensymphony.xwork2.interceptor.AbstractInterceptor;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
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
            logger.debug("Found session in interceptor.");
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
            timeNow = Context.now();
            int userId = user.getId();

            CONTEXT_SOURCE contextSource = Context.contextSource.get();


            String requestUri = request.getRequestURI();
            boolean isOnboardingRequest = requestUri != null && requestUri.contains("/onboarding");

            Role userRoleRecord = RBACDao.getCurrentRoleForUser(userId, sessionAccId);

            String userRole = null;
            if (userRoleRecord != null) {
                userRole = userRoleRecord.getName().toUpperCase();
            }

            if (!isOnboardingRequest && userRoleRecord.equals(Role.NO_ACCESS)) {
                HttpServletResponse response = (HttpServletResponse) ServletActionContext.getResponse();
                response.setHeader("X-No-Access-Error", "true");
                ((ActionSupport) invocation.getAction()).addActionError("You do not have access to this product. Please ask Admin to grant access or navigate to accessible product");

                String contextSourceStr = contextSource.toString();
                logger.debug("Access denied for user " + user.getLogin() + " to product scope: " + contextSourceStr);
                loggerMaker.infoAndAddToDb("Access denied for user " + user.getLogin() + " to product scope: " + contextSourceStr);

                // Send Slack alert with caching to prevent duplicate alerts

                if (AlertUtils.shouldSendNoAccessAlert(user.getLogin(), contextSourceStr, String.valueOf(sessionAccId))) {
                    try {
                        SlackAlerts noScopeAccessAlert = new UserBlockedNoScopeAccessAlert(
                            user.getLogin(),
                            contextSourceStr,
                            contextSourceStr,
                            String.valueOf(sessionAccId)
                        );
                        SlackSender.sendAlert(sessionAccId, noScopeAccessAlert, null, true);
                        logger.infoAndAddToDb("Sent Slack alert for NO_ACCESS denial: " + user.getLogin() + " to scope " + contextSourceStr);
                    } catch (Exception e) {
                        logger.errorAndAddToDb(e, "Failed to send Slack alert for NO_ACCESS denial: " + e.getMessage());
                    }
                }  else {
                    logger.infoAndAddToDb("Skipped duplicate Slack alert for user " + user.getLogin() + " (cached)");
                }

                // Block the request - return FORBIDDEN instead of invoking
                return FORBIDDEN;
            }

            if (isOnboardingRequest) {
                logger.debug("Skipping all access validation for onboarding request from user " + user.getLogin());
                // Allow onboarding requests to proceed without access checks
                // This is a special flow where users may not have full access yet
                return invocation.invoke();
            }
            // ===== END PRODUCT SCOPE VALIDATION =====

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

                    /** Audit Annotation details **/
                    Resource resource = Resource.NOT_SPECIFIED;
                    Operation operation = Operation.NOT_SPECIFIED;
                    BasicDBObject metadata = new BasicDBObject();

                    ActionProxy proxy = invocation.getProxy();
                    ActionConfig config = proxy.getConfig();

                    try {
                        String actionClassName = config.getClassName();
                        String actionMethodName = proxy.getMethod();
                        if (actionMethodName == null || actionMethodName.isEmpty()) {
                            actionMethodName = "execute";
                        }

                        Class<?> actionClass = Class.forName(actionClassName);
                        Method actionMethod = actionClass.getMethod(actionMethodName);

                        Audit audit = actionMethod.getDeclaredAnnotation(Audit.class);
                        if (audit != null) {
                            String auditDescription = audit.description();
                            if (auditDescription != null && !auditDescription.isEmpty()) {
                                actionDescription = auditDescription;
                            }
                            resource = audit.resource();
                            operation = audit.operation();

                            Object actionObj = invocation.getAction();
                            String[] metadataGenerators = audit.metadataGenerators();
                            for (String metadataGenerator : metadataGenerators) {
                                if (metadataGenerator == null || metadataGenerator.isEmpty()) continue;
                                Object metadataValue = null;

                                String formattedMetadataKey = metadataGenerator;

                                String[] prefixes = { "get", "is" };
                                for (String prefix : prefixes) {
                                    if (metadataGenerator.startsWith(prefix) && metadataGenerator.length() > prefix.length()) {
                                        String withoutPrefix = metadataGenerator.substring(prefix.length());
                                        formattedMetadataKey = Character.toLowerCase(withoutPrefix.charAt(0)) + withoutPrefix.substring(1);
                                        break;
                                    }
                                }

                                try {
                                    Method metadataGeneratorMethod = actionClass.getMethod(metadataGenerator);
                                    metadataValue = metadataGeneratorMethod.invoke(actionObj);
                                } catch (Exception e) {
                                    loggerMaker.errorAndAddToDb(e, "Error while getting metadata value from method: " + metadataGenerator + " Error: " + e.getMessage());
                                }
                                metadata.put(formattedMetadataKey, metadataValue);
                            }
                        }
                    } catch (Exception e) {
                        loggerMaker.errorAndAddToDb(e, "Error while getting audit annotation details for action method: " + e.getMessage());
                    }
                    /** Audit Annotation details **/

                    apiAuditLogs = new ApiAuditLogs(timestamp, apiEndpoint, actionDescription, userEmail, userAgentType.name(), userIpAddress, userProxyIpAddresses, resource, operation, metadata);
                }
            } catch(Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error while inserting api audit logs: " + e.getMessage());
            }

        } catch(Exception e) {
            String api = invocation.getProxy().getActionName();
            String error = "Error in RoleInterceptor for api: " + api + " ERROR: " + e.getMessage();
            loggerMaker.errorAndAddToDb(e, error);
        }

        String result = invocation.invoke();

        if (apiAuditLogs != null && result.equalsIgnoreCase(Action.SUCCESS.toUpperCase())) {
            ApiAuditLogsDao.instance.insertOne(apiAuditLogs);
        }

        return result;
    }
}
