package com.akto.action;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.struts2.dispatcher.Dispatcher;

import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.interceptor.RoleAccessInterceptor;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.DashboardMode;
import com.opensymphony.xwork2.config.entities.ActionConfig;
import com.opensymphony.xwork2.config.entities.InterceptorMapping;

import lombok.Getter;

/*
 * What the signed-in user can do in the current product, so the UI hides what they can't open and disables what they
 * can't change instead of failing after a click. Decided by RoleAccessInterceptor.accessError, the same check the
 * server enforces, over the role checks in struts.xml.
 */
public class UserPermissionsAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(UserPermissionsAction.class, LogDb.DASHBOARD);

    // action name -> {featureLabel, accessType, collectionScope}, read once from the running Struts configuration
    private static volatile Map<String, String[]> roleChecks;

    @Getter
    private Map<String, String> featureAccess;

    // actions (e.g. "api/addSplunkIntegration") the user can't call in this product
    @Getter
    private List<String> deniedActions;

    public String fetchUserPermissions() {
        int accountId = Context.accountId.get();
        User user = getSUser();
        this.featureAccess = featureAccess(user, accountId);
        this.deniedActions = deniedActions(roleChecks(), user, accountId);
        return SUCCESS.toUpperCase();
    }

    public static Map<String, String> featureAccess(User user, int accountId) {
        Map<String, String> access = new HashMap<>();
        boolean enforced = DashboardMode.isMetered();
        boolean rbacFeature = enforced && UsageMetricCalculator.isRbacFeatureAvailable(accountId);
        Role role = enforced ? RBACDao.getCurrentRoleForUser(user.getId(), accountId) : Role.ADMIN;
        for (Feature feature : Feature.values()) {
            ReadWriteAccess given;
            if (feature == Feature.ADMIN_ACTIONS) {
                given = role == Role.ADMIN ? ReadWriteAccess.READ_WRITE : ReadWriteAccess.NO_ACCESS;
            } else if (role == Role.NO_ACCESS) {
                given = ReadWriteAccess.NO_ACCESS;
            } else if (!rbacFeature) {
                given = ReadWriteAccess.READ_WRITE;
            } else {
                given = RBACDao.resolveFeatureAccess(user.getId(), accountId, feature, role.getReadWriteAccessForFeature(feature));
            }
            access.put(feature.name(), given.name());
        }
        return access;
    }

    public static List<String> deniedActions(Map<String, String[]> checks, User user, int accountId) {
        List<String> denied = new ArrayList<>();
        if (!DashboardMode.isMetered()) {
            return denied;
        }
        Role role = RBACDao.getCurrentRoleForUser(user.getId(), accountId);
        for (Map.Entry<String, String[]> entry : checks.entrySet()) {
            String[] check = entry.getValue();
            try {
                if (role == Role.NO_ACCESS || RoleAccessInterceptor.accessError(check[0], check[1], check[2], null, user, accountId, role) != null) {
                    denied.add(entry.getKey());
                }
            } catch (Exception e) {
                // an unknown label is reported by the interceptor itself; the UI just leaves the action as is
            }
        }
        Collections.sort(denied);
        return denied;
    }

    private static Map<String, String[]> roleChecks() {
        if (roleChecks == null) {
            Map<String, String[]> checks = new HashMap<>();
            try {
                Map<String, Map<String, ActionConfig>> configs = Dispatcher.getInstance().getConfigurationManager()
                        .getConfiguration().getRuntimeConfiguration().getActionConfigs();
                for (Map<String, ActionConfig> namespace : configs.values()) {
                    for (Map.Entry<String, ActionConfig> action : namespace.entrySet()) {
                        for (InterceptorMapping mapping : action.getValue().getInterceptors()) {
                            if (mapping.getInterceptor() instanceof RoleAccessInterceptor) {
                                RoleAccessInterceptor check = (RoleAccessInterceptor) mapping.getInterceptor();
                                if (check.getFeatureLabel() != null && check.getAccessType() != null) {
                                    checks.put(action.getKey(), new String[]{check.getFeatureLabel(), check.getAccessType(), check.getCollectionScope()});
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error reading role checks: " + e.getMessage());
                return Collections.emptyMap(); // nothing hidden; the server still enforces every check
            }
            roleChecks = checks;
        }
        return roleChecks;
    }
}
