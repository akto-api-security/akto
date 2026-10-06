package com.akto.interceptor;

import com.akto.action.TraceAction;
import com.akto.dao.tracing.TraceDao;
import com.akto.dto.tracing.model.Trace;
import com.akto.utils.ArgusCollectionScope;
import com.mongodb.client.model.Filters;
import com.akto.audit_logs_util.Audit;
import com.akto.audit_logs_util.AuditLogsUtil;
import com.akto.dao.RBACDao;
import com.akto.dao.RbacCacheVersionDao;
import com.akto.dao.audit_logs.ApiAuditLogsDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.audit_logs.ApiAuditLogs;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.rbac.RbacEnums;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.dto.rbac.UsersCollectionsList;
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
    String collectionScope;

    public void setFeatureLabel(String featureLabel) {
        this.featureLabel = featureLabel;
    }

    public void setAccessType(String accessType) {
        this.accessType = accessType;
    }

    public void setActionDescription(String actionDescription) {
        this.actionDescription = actionDescription;
    }

    public void setCollectionScope(String collectionScope) {
        this.collectionScope = collectionScope;
    }

    public String getFeatureLabel() {
        return featureLabel;
    }

    public String getAccessType() {
        return accessType;
    }

    public String getCollectionScope() {
        return collectionScope;
    }

    // Error for users limited to specific collections, or null if the request is allowed
    private String checkCollectionScope(Object action, User user, int accountId) {
        return collectionScopeError(collectionScope, action, user, accountId);
    }

    static String collectionScopeError(String collectionScope, Object action, User user, int accountId) {
        if (collectionScope == null) return null;
        RbacEnums.CollectionScope scope = RbacEnums.CollectionScope.valueOf(collectionScope.toUpperCase());
        if (scope == RbacEnums.CollectionScope.ALL_COLLECTIONS) {
            // users, roles and SSO change access to every collection, so they are for admins of all collections only.
            // Collection limits apply only with the RBAC feature.
            if (!UsageMetricCalculator.isRbacFeatureAvailable(accountId)) return null;
            List<Integer> userCollections = UsersCollectionsList.getAssignedCollectionIds(user.getId(), accountId);
            return userCollections == null || userCollections.isEmpty() ? null
                    : "Only admins with access to all collections can manage users, roles and SSO.";
        }
        List<Integer> restrictedIds = ArgusCollectionScope.getRestrictedCollectionIds(user);
        if (restrictedIds == null) return null;
        switch (scope) {
            case ACCOUNT_WIDE:
                return "Users limited to specific collections cannot change account-wide settings.";
            case OWN_COLLECTION:
                if (action instanceof TraceAction) {
                    TraceAction traceAction = (TraceAction) action;
                    int collectionId = traceAction.getApiCollectionId();
                    if (traceAction.getTraceId() != null && !traceAction.getTraceId().isEmpty()) {
                        Trace trace = TraceDao.instance.findOne(Filters.eq("_id", traceAction.getTraceId()));
                        collectionId = trace == null ? -1 : trace.getApiCollectionId();
                    }
                    if (!restrictedIds.contains(collectionId)) {
                        return "You can only view data of the collections assigned to you.";
                    }
                }
                return null;
            default:
                return null;
        }
    }

    public static final String ROLE_DENIED_MESSAGE = "Your role does not have access to this. Ask an admin if you need it.";

    /*
     * Why the user may not call an action with these role check settings, or null if they may. The interceptor and the
     * permissions sent to the UI both use this, so what the UI hides is exactly what the server refuses.
     * The product check (no role in the product) and non-metered dashboards are handled by the callers.
     */
    public static String accessError(String featureLabel, String accessType, String collectionScope, Object action,
                                     User user, int accountId, Role userRoleRecord) {
        // relaxed to full access for accounts without the paid RBAC feature, except admin actions
        if (!(UsageMetricCalculator.isRbacFeatureAvailable(accountId) || featureLabel.equalsIgnoreCase(Feature.ADMIN_ACTIONS.toString()))) {
            return null;
        }
        Feature featureType = Feature.valueOf(featureLabel.toUpperCase());
        // custom roles: threat toggle and per-feature overrides
        ReadWriteAccess accessGiven = RBACDao.resolveFeatureAccess(user.getId(), accountId, featureType,
                userRoleRecord.getReadWriteAccessForFeature(featureType));
        if (!hasRequiredAccess(featureLabel, accessType, accessGiven, userRoleRecord.getName().toUpperCase())) {
            return ROLE_DENIED_MESSAGE;
        }
        return collectionScopeError(collectionScope, action, user, accountId);
    }

    /** Whether a role with the given access to the action's feature may call it. Admin actions need the Admin role. */
    static boolean hasRequiredAccess(String featureLabel, String accessType, ReadWriteAccess accessGiven, String userRole) {
        if (featureLabel.equals(Feature.ADMIN_ACTIONS.name())) {
            return Role.ADMIN.name().equals(userRole);
        }
        if (accessType.equalsIgnoreCase(ReadWriteAccess.READ.toString()) || accessType.equalsIgnoreCase(accessGiven.toString())) {
            return !accessGiven.equals(ReadWriteAccess.NO_ACCESS);
        }
        return false;
    }

    private static final boolean DENY_ON_ERROR = "true".equalsIgnoreCase(System.getenv("AKTO_RBAC_DENY_ON_ERROR"));

    public final static String FORBIDDEN = "FORBIDDEN";
    public final static String USER = "user";

    private int getUserAccountId (Map<String, Object> session) throws Exception{
        try {
            Object accountIdObj = session.get(UserDetailsFilter.ACCOUNT_ID);
            String accountIdStr = accountIdObj == null ? null : accountIdObj+"";
            if(accountIdStr == null && Context.accountId.get() != null){
                // sessions used straight from the API (no page load) have no account yet; UserDetailsFilter already
                // picked one of the user's own accounts for this request. Without this the checks below were skipped.
                accountIdStr = String.valueOf(Context.accountId.get());
            }
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

            timeNow = Context.now();
            int userId = user.getId();
            // drop cached access if it was changed on another dashboard instance
            RbacCacheVersionDao.syncIfChanged(sessionAccId);

            CONTEXT_SOURCE contextSource = Context.contextSource.get();


            String requestUri = request.getRequestURI();
            boolean isOnboardingRequest = requestUri != null && requestUri.contains("/onboarding");

            Role userRoleRecord = RBACDao.getCurrentRoleForUser(userId, sessionAccId);

            String userRole = null;
            if (userRoleRecord != null) {
                userRole = userRoleRecord.getName().toUpperCase();
            }

            // Product-scope (NO_ACCESS) enforcement must run regardless of whether this account
            // has paid for the RBAC feature — it's a distinct, more fundamental boundary ("does
            // this user have any access to this product line at all") from the fine-grained,
            // per-feature read/write check further below, which the RBAC-paid-feature gate
            // legitimately relaxes to "full access" for accounts without the custom-roles add-on.
            // The isRbacFeatureAvailable() skip used to sit BEFORE this block, so on any account
            // without that add-on it skipped the NO_ACCESS denial too, for every endpoint except
            // the hardcoded ADMIN_ACTIONS ones — silently leaking product-scoped data to users
            // explicitly denied access to that product (e.g. a user with no Atlas access could
            // still read Atlas data through any non-ADMIN_ACTIONS-labeled endpoint).
            if (!isOnboardingRequest && userRoleRecord.equals(Role.NO_ACCESS)) {
                HttpServletResponse response = (HttpServletResponse) ServletActionContext.getResponse();
                response.setHeader("X-No-Access-Error", "true");
                ((ActionSupport) invocation.getAction()).addActionError(RBACDao.hasMissingRole(userId, sessionAccId)
                        ? "Your role was removed. Ask an admin to give you a new role."
                        : "You don't have access to this product. Ask an admin for access, or switch to another product.");

                String contextSourceStr = contextSource.toString();
                logger.debug("Access denied for user " + user.getLogin() + " to product scope: " + contextSourceStr);
                loggerMaker.infoAndAddToDb("Access denied for user " + user.getLogin() + " to product scope: " + contextSourceStr);
                try {
                    if (UsersCollectionsList.isRbacDebugAccount(sessionAccId)) {
                        RBAC rbac = RBACDao.getCurrentRBACForUser(userId, sessionAccId);
                        String storedRole = rbac == null ? "null" : rbac.getRole();
                        String mapping = (rbac == null || rbac.getScopeRoleMapping() == null || rbac.getScopeRoleMapping().isEmpty())
                                ? "none" : rbac.getScopeRoleMapping().toString();
                        int pinnedSize = (rbac == null || rbac.getApiCollectionsId() == null) ? -1 : rbac.getApiCollectionsId().size();
                        loggerMaker.infoAndAddToDb("Access denied details userId=" + userId + " accountId=" + sessionAccId
                                + " context=" + contextSourceStr
                                + " resolvedRole=" + userRole
                                + " storedRole=" + storedRole
                                + " mapping=" + mapping
                                + " pinnedSize=" + pinnedSize
                                + " uri=" + requestUri);
                    }
                } catch (Exception ignored) {
                }

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

            // Fine-grained, per-feature read/write check — relaxed to "full access" for accounts
            // that haven't paid for the RBAC feature (custom roles). ADMIN_ACTIONS is exempt from
            // this relaxation: it gates a genuinely dangerous capability and is always enforced
            // regardless of billing tier.
            if(!(UsageMetricCalculator.isRbacFeatureAvailable(sessionAccId) || featureLabel.equalsIgnoreCase(RbacEnums.Feature.ADMIN_ACTIONS.toString()))){
                logger.debug("Time by feature label check in: " + (Context.now() - timeNow));
                return invocation.invoke();
            }

            String accessError = accessError(featureLabel, accessType, collectionScope, invocation.getAction(), user, sessionAccId, userRoleRecord);
            if (accessError != null) {
                // app log only (no DB write), so a page repeatedly calling an API it cannot use adds no load
                logger.info("RBAC denied api: " + invocation.getProxy().getActionName() + " userId: " + userId + " role: " + userRole + " feature: " + featureLabel + " " + accessType);
                ((ActionSupport) invocation.getAction()).addActionError(accessError);
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
            // A failed access check must not grant access. Until AKTO_RBAC_DENY_ON_ERROR is on, it is only logged (report-only).
            boolean deny = DENY_ON_ERROR && DashboardMode.isMetered();
            String error = "Error in RoleInterceptor for api: " + api + " ERROR: " + e.getMessage() + (deny ? " (denied)" : " (allowed, report-only)");
            loggerMaker.errorAndAddToDb(e, error);
            if (deny) {
                ((ActionSupport) invocation.getAction()).addActionError("Unable to verify your access. Please try again or contact your admin.");
                return FORBIDDEN;
            }
        }

        String result = invocation.invoke();

        if (apiAuditLogs != null && result.equalsIgnoreCase(Action.SUCCESS.toUpperCase())) {
            ApiAuditLogsDao.instance.insertOne(apiAuditLogs);
        }

        return result;
    }
}
