package com.akto.action;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.password_reset.PasswordResetUtils;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Pair;
import com.akto.utils.Utils;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.opensymphony.xwork2.Action;

import org.apache.struts2.interceptor.ServletRequestAware;
import org.apache.struts2.interceptor.ServletResponseAware;
import org.bson.conversions.Bson;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.*;

import static com.akto.util.Constants.TWO_HOURS_TIMESTAMP;

public class TeamAction extends UserAction implements ServletResponseAware, ServletRequestAware {

    int id;
    BasicDBList users;

    private static final LoggerMaker loggerMaker = new LoggerMaker(TeamAction.class, LogDb.DASHBOARD);
    public static final String INVALID_PRODUCT_SCOPE = "Invalid product scope: user account does not have access to this scope";

    /**
     * Maps scope values to display labels
     */
    private static String getScopeDisplayLabel(String scope) {
        switch (scope) {
            case "API":
                return "API Security";
            case "AGENTIC":
                return "Akto ARGUS";
            case "ENDPOINT":
                return "Akto ATLAS";
            case "DAST":
                return "DAST";
            default:
                return scope;
        }
    }

    /**
     * Formats scope-role mapping for display in pending invitations.
     * Format: "Invitation sent for Developer on API, Security Engineer on Akto ATLAS"
     */
    private String formatScopeRoleMapping(Map<String, String> scopeRoleMapping) {
        if (scopeRoleMapping == null || scopeRoleMapping.isEmpty()) {
            return "Invitation sent";
        }

        StringBuilder roleText = new StringBuilder("Invitation sent for ");
        boolean first = true;
        for (Map.Entry<String, String> entry : scopeRoleMapping.entrySet()) {
            if (!first) {
                roleText.append(", ");
            }
            roleText.append(entry.getValue())
                    .append(" on ")
                    .append(getScopeDisplayLabel(entry.getKey()));
            first = false;
        }
        return roleText.toString();
    }

    /**
     * Validates that a product scope is accessible for the current account.
     * Based on STIGG feature grants for the account.
     *
     * @param scope the scope to validate
     * @return true if scope is accessible, false otherwise
     */
    private boolean isValidProductScope(String scope) {
        if (scope == null || scope.isEmpty()) {
            return false;
        }
        Set<String> accessibleScopes = UsageMetricCalculator.getAccessibleProductScopes(Context.accountId.get());
        return accessibleScopes.contains(scope);
    }

    public String fetchTeamData() {
        int accountId = Context.accountId.get();
        List<RBAC> allRoles = RBACDao.instance.findAll(Filters.or(
                Filters.eq(RBAC.ACCOUNT_ID, accountId),
                Filters.exists(RBAC.ACCOUNT_ID, false)
        ));

        Map<Integer, RBAC> userToRBAC = new HashMap<>();
        for(RBAC rbac: allRoles) {
            if (rbac.getAccountId() == 0) {//case where account id doesn't exists belonged to older 1_000_000 account
                rbac.setAccountId(1_000_000);
            }
            if (rbac.getAccountId() == accountId) {
                userToRBAC.put(rbac.getUserId(), rbac);
            }
        }

        users = UsersDao.instance.getAllUsersInfoForTheAccount(Context.accountId.get());
        Set<String> userSet = new HashSet<>();
        for(Object obj: users) {
            BasicDBObject userObj = (BasicDBObject) obj;
            RBAC rbac = userToRBAC.get(userObj.getInt("id"));
            String status = (rbac == null || rbac.getRole() == null) ? Role.MEMBER.getName() : rbac.getRole();
            userObj.append("role", status);

            // Add scopeRoleMapping to the user object for n:n scope-role display
            if (rbac != null && rbac.getScopeRoleMapping() != null && !rbac.getScopeRoleMapping().isEmpty()) {
                userObj.append("scopeRoleMapping", rbac.getScopeRoleMapping());
            }

            try {
                String login = userObj.getString(User.LOGIN);
                if (login != null) {
                    userSet.add(login);
                }
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb(e, "Error in fetchTeamData " + e.getMessage());
            }
        }

        List<PendingInviteCode> pendingInviteCodes = PendingInviteCodesDao.instance.findAll(Filters.or(
                Filters.eq(RBAC.ACCOUNT_ID, Context.accountId.get()),
                Filters.exists(RBAC.ACCOUNT_ID, false)
        ));

        for(PendingInviteCode pendingInviteCode: pendingInviteCodes) {
            if (pendingInviteCode.getAccountId() == 0) {//case where account id doesn't exists belonged to older 1_000_000 account
                pendingInviteCode.setAccountId(1_000_000);
            }

            // Use new scopeRoleMapping if available, otherwise fall back to old inviteeRole for backward compatibility
            String roleText;
            if (pendingInviteCode.getScopeRoleMapping() != null && !pendingInviteCode.getScopeRoleMapping().isEmpty()) {
                roleText = formatScopeRoleMapping(pendingInviteCode.getScopeRoleMapping());
            } else {
                // Backward compatibility: use old inviteeRole field
                String inviteeRole = pendingInviteCode.getInviteeRole();
                roleText = "Invitation sent ";
                if (inviteeRole == null) {
                    roleText += "for Security Engineer";
                } else {
                    roleText += "for " + inviteeRole;
                }
            }
            /*
             * Do not send invitation code, if already a member.
             */
            if (pendingInviteCode.getAccountId() == accountId &&
                    !userSet.contains(pendingInviteCode.getInviteeEmailId())) {
                users.add(
                        new BasicDBObject("id", pendingInviteCode.getIssuer())
                                .append("login", pendingInviteCode.getInviteeEmailId())
                                .append("name", "-")
                                .append("role", roleText)
                                .append("isInvitation", true)
                );
            }
        }
        return SUCCESS.toUpperCase();
    }
    private enum ActionType {
        REMOVE_USER,
        UPDATE_USER_ROLE
    }
    
    String email;
    public String performAction(ActionType action, String reqUserRole) {
        int currUserId = getSUser().getId();
        int accId = Context.accountId.get();

        Bson findQ = Filters.eq(User.LOGIN, email);
        User userDetails = UsersDao.instance.findOne(findQ);
        boolean userExists =  userDetails != null;

        Bson filterRbac = Filters.and(
            Filters.eq(RBAC.USER_ID, userDetails.getId()),
            Filters.eq(RBAC.ACCOUNT_ID, accId));

        if (userExists && userDetails.getId() == currUserId) {
            addActionError("You cannot perform this action on yourself");
            return Action.ERROR.toUpperCase();
        }

        Role currentUserRole = RBACDao.getCurrentRoleForUser(currUserId, accId);
        Role userRole = RBACDao.getCurrentRoleForUser(userDetails.getId(), accId); // current role of the user whose role is changing

        CustomRole customRole = CustomRoleDao.instance.findRoleByName(reqUserRole);

        Role requestedRole = null;
        try {
            if (customRole != null) {
                requestedRole = Role.valueOf(customRole.getBaseRole());
            } else {
                if(reqUserRole != null && !action.equals(ActionType.REMOVE_USER)){
                    requestedRole = Role.valueOf(reqUserRole);
                }
            }
        } catch (Exception e) {
            addActionError("Invalid user role");
            return Action.ERROR.toUpperCase();
        }

        switch (action) {
            case REMOVE_USER:
                if (userExists) {
                    UsersDao.instance.updateOne(findQ, Updates.unset("accounts." + accId));
                    RBACDao.instance.deleteAll(filterRbac);
                    return Action.SUCCESS.toUpperCase();
                } else {
                    DeleteResult delResult = PendingInviteCodesDao.instance.getMCollection().deleteMany(Filters.eq("inviteeEmailId", email));
                    if (delResult.getDeletedCount() > 0) {
                        return Action.SUCCESS.toUpperCase();
                    } else {
                        return Action.ERROR.toUpperCase();
                    }
                }

            case UPDATE_USER_ROLE:
                if (userExists) {
                    try {
                        Role[] rolesHierarchy = currentUserRole.getRoleHierarchy();
                        boolean isValidUpdateRole = false; // cannot change for a user role higher than yourself
                        boolean shouldChangeRole = false; // cannot change to a role higher than yourself

                        for(Role role: rolesHierarchy){
                            if(role.equals(userRole)){
                                isValidUpdateRole = true;
                            }
                            if(role.equals(requestedRole)){
                                shouldChangeRole = true;
                            }
                        }
                        if(isValidUpdateRole && shouldChangeRole){
                            /*
                             * Update the role only. Product scopes are managed through scope-role mapping.
                             */
                            RBACDao.instance.updateOneNoUpsert(
                                filterRbac,
                                Updates.set(RBAC.ROLE, reqUserRole)
                            );

                            RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userDetails.getId(), accId));
                            UsersCollectionsList.deleteCollectionIdsFromCache(userDetails.getId(), accId);
                            return Action.SUCCESS.toUpperCase();
                        }else{
                            addActionError("User doesn't have access to modify this role.");
                            return Action.ERROR.toUpperCase();
                        }
                    } catch (Exception e) {
                        addActionError("User role doesn't exist.");
                        return Action.ERROR.toUpperCase();
                    }

                } else {
                    addActionError("User doesn't exist");
                    return Action.ERROR.toUpperCase();
                }

            default:
                break;
        }
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userDetails.getId(), accId));
        UsersCollectionsList.deleteCollectionIdsFromCache(userDetails.getId(), accId);
        return Action.SUCCESS.toUpperCase();
    }

    public String removeUser() {
        return performAction(ActionType.REMOVE_USER, null);
    }

    private String userRole;

    public String makeAdmin(){
        return performAction(ActionType.UPDATE_USER_ROLE, this.userRole.toUpperCase());
    }

    private Map<String, String> scopeRoleMapping;

    public String updateUserScopeRoleMapping() {
        int accId = Context.accountId.get();
        Bson findQ = Filters.eq(User.LOGIN, email);
        User userDetails = UsersDao.instance.findOne(findQ);

        if (userDetails == null) {
            addActionError("User not found");
            return Action.ERROR.toUpperCase();
        }

        loggerMaker.debugAndAddToDb("scopeRoleMapping before init: " + scopeRoleMapping);
        if (this.scopeRoleMapping == null || this.scopeRoleMapping.isEmpty()) {

            String defaultRole = RBAC.Role.MEMBER.name();
            if (UsageMetricCalculator.isRbacFeatureAvailable(Context.accountId.get())) {
                defaultRole = Utils.fetchDefaultInviteRole(Context.accountId.get(), Role.NO_ACCESS.name());
            }
            this.scopeRoleMapping = RBAC.initializeScopeRoleMapping(this.scopeRoleMapping, defaultRole);
        }
        loggerMaker.debugAndAddToDb("scopeRoleMapping after init: " + scopeRoleMapping);

        // Validate that all scopes in scopeRoleMappingToSave are valid product scopes
        // Allow NO_ACCESS assignments for any scope (NO_ACCESS is explicit deny, not a privilege)
        if (scopeRoleMapping != null && !scopeRoleMapping.isEmpty()) {
            for (Map.Entry<String, String> entry : scopeRoleMapping.entrySet()) {
                String scope = entry.getKey();
                String roleStr = entry.getValue();

                // Allow NO_ACCESS assignments for any scope (NO_ACCESS is explicit deny, not a privilege)
                // Only validate scope accessibility for actual role assignments (non-NO_ACCESS)
                try {
                    Role baseRole = RBAC.Role.valueOf(roleStr);
                    if (!baseRole.equals(RBAC.Role.NO_ACCESS) && !isValidProductScope(scope)) {
                        addActionError(INVALID_PRODUCT_SCOPE + scope);
                        loggerMaker.errorAndAddToDb("Invalid product scope attempted in scope-role mapping: " + scope + " for user: " + email);
                        return Action.ERROR.toUpperCase();
                    }
                } catch (IllegalArgumentException e) {
                    // roleStr is not a standard role, check if it's a custom role
                    CustomRole customRole = CustomRoleDao.instance.findRoleByName(roleStr);
                    if (customRole != null) {
                        Role baseRole = RBAC.Role.valueOf(customRole.getBaseRole());
                        if (!baseRole.equals(RBAC.Role.NO_ACCESS) && !isValidProductScope(scope)) {
                            addActionError(INVALID_PRODUCT_SCOPE + scope);
                            loggerMaker.errorAndAddToDb("Invalid product scope attempted in scope-role mapping: " + scope + " for user: " + email);
                            return Action.ERROR.toUpperCase();
                        }
                    }
                }
            }
        }

        try {
            Bson filterRbac = Filters.and(
                Filters.eq(RBAC.USER_ID, userDetails.getId()),
                Filters.eq(RBAC.ACCOUNT_ID, accId)
            );

            // Update the scopeRoleMapping in RBAC
            RBACDao.instance.updateOneNoUpsert(
                filterRbac,
                Updates.set(RBAC.SCOPE_ROLE_MAPPING, scopeRoleMapping)
            );

            // Clear cache
            RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userDetails.getId(), accId));
            UsersCollectionsList.deleteCollectionIdsFromCache(userDetails.getId(), accId);

            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error updating scope-role mapping: " + e.getMessage());
            addActionError("Failed to update scope-role mapping");
            return Action.ERROR.toUpperCase();
        }
    }

    private Role[] userRoleHierarchy;

    public String getRoleHierarchy(){
        try {
            Role currentRole = RBACDao.getCurrentRoleForUser(getSUser().getId(), Context.accountId.get());
            this.userRoleHierarchy = currentRole.getRoleHierarchy();
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            addActionError("User role doesn't exist.");
            return Action.ERROR.toUpperCase();
        }
    }

    String userEmail;
    String passwordResetToken;
    public String resetUserPassword() {
        if(userEmail == null || userEmail.isEmpty()) {
            addActionError("Email cannot be null or empty");
            return Action.ERROR.toUpperCase();
        }

        User user = getSUser();
        if(user == null) {
            addActionError("User cannot be null or empty");
            return Action.ERROR.toUpperCase();
        }

        User forgotPasswordUser = UsersDao.instance.findOne(Filters.eq(User.LOGIN, userEmail));
        if(forgotPasswordUser == null) {
            addActionError("User not found.");
            return Action.ERROR.toUpperCase();
        }

        int lastPasswordResetToken = forgotPasswordUser.getLastPasswordResetToken();
        int timeElapsed = Context.now() - lastPasswordResetToken;
        if(timeElapsed < TWO_HOURS_TIMESTAMP) {
            int remainingTime = (TWO_HOURS_TIMESTAMP - timeElapsed) / 60;
            addActionError("Please wait " + remainingTime + " minute" + (remainingTime > 1 ? "s" : "") + " for another password reset.");
            return Action.ERROR.toUpperCase();
        }

        String scheme = servletRequest.getScheme();
        String serverName = servletRequest.getServerName();
        int serverPort = servletRequest.getServerPort();
        String websiteHostName;
        if (serverPort == 80 || serverPort == 443) {
            websiteHostName = scheme + "://" + serverName;
        } else {
            websiteHostName = scheme + "://" + serverName + ":" + serverPort;
        }

        passwordResetToken = PasswordResetUtils.insertPasswordResetToken(userEmail, websiteHostName);

        if(passwordResetToken == null || passwordResetToken.isEmpty()) {
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }



    public String removeInvitation() {
        User sUser = getSUser();

        PendingInviteCode pendingInviteCode = PendingInviteCodesDao.instance.findOne(Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, email));
        Role currentRole = RBACDao.getCurrentRoleForUser(getSUser().getId(), Context.accountId.get());

        boolean isIssuer = pendingInviteCode.getIssuer() == sUser.getId();
        boolean isAdmin = Role.ADMIN.name().equals(currentRole.name());

        if(isIssuer || isAdmin) {
            Bson filters = Filters.and(
                    Filters.eq(PendingInviteCode.ACCOUNT_ID, Context.accountId.get()),
                    Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, email)
            );
            PendingInviteCodesDao.instance.getMCollection().deleteOne(filters);
            return SUCCESS.toUpperCase();
        } else {
            addActionError("User is not allowed to remove this invitation.");
            return Action.ERROR.toUpperCase();
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public BasicDBList getUsers() {
        return users;
    }

    public void setUsers(BasicDBList users) {
        this.users = users;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getEmail() {
        return this.email;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public Role[] getUserRoleHierarchy() {
        return userRoleHierarchy;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public String getPasswordResetToken() {
        return passwordResetToken;
    }

    public void setScopeRoleMapping(Map<String, String> scopeRoleMapping) {
        this.scopeRoleMapping = scopeRoleMapping;
    }

    public Map<String, String> getScopeRoleMapping() {
        return this.scopeRoleMapping;
    }

    protected HttpServletResponse servletResponse;
    @Override
    public void setServletResponse(HttpServletResponse httpServletResponse) {
        this.servletResponse= httpServletResponse;
    }

    protected HttpServletRequest servletRequest;
    @Override
    public void setServletRequest(HttpServletRequest httpServletRequest) {
        this.servletRequest = httpServletRequest;
    }
}
