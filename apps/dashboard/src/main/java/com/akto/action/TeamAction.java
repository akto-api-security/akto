package com.akto.action;

import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.RbacCacheVersionDao;
import com.akto.dao.UsersDao;
import com.akto.audit_logs_util.Audit;
import com.akto.dao.context.Context;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.User;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.password_reset.PasswordResetUtils;
import com.akto.usage.UsageMetricCalculator;
import com.akto.util.Pair;
import com.akto.utils.RoleAssignment;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
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
            if (rbac != null && rbac.getAccessExpiresAt() > 0) {
                userObj.append("accessExpiresAt", rbac.getAccessExpiresAt());
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
    String email;

    private User findAccountUser(int accountId) {
        return email == null ? null : UsersDao.instance.findOne(Filters.and(Filters.eq(User.LOGIN, email), Filters.exists(User.ACCOUNTS + "." + accountId)));
    }

    private static Bson rbacFilter(int userId, int accountId) {
        return Filters.and(Filters.eq(RBAC.USER_ID, userId), Filters.eq(RBAC.ACCOUNT_ID, accountId));
    }

    /*
     * Checks shared by every change to another user's access (role change, removal). newScopeRoleMapping null
     * means the user is removed. Returns the message to show, or null when the change is allowed.
     */
    private String validateAccessChange(User target, int accountId, Map<String, String> newScopeRoleMapping, int newExpiresAt) {
        int callerId = getSUser().getId();
        if (target == null) {
            return "This user is not in your account.";
        }
        if (target.getId() == callerId) {
            return "You can't change your own access. Ask another admin.";
        }
        if (!RoleAssignment.canChangeOthersAccess(callerId, accountId)) {
            return "Your role can't change other users' access.";
        }
        // fresh read, not the cached entry, so a role given moments ago is respected
        RBAC targetRbac = RBACDao.instance.findOne(rbacFilter(target.getId(), accountId));
        if (!RoleAssignment.canManage(callerId, accountId, targetRbac)) {
            return "You can't change access for " + email + ": they have a role you can't give.";
        }
        if (newScopeRoleMapping != null) {
            for (Map.Entry<String, String> entry : newScopeRoleMapping.entrySet()) {
                String error = validateGivenRole(callerId, accountId, entry.getKey(), entry.getValue());
                if (error != null) {
                    return error;
                }
            }
        }
        List<String> losingAdmin = RoleAssignment.productsLosingLastAdmin(accountId, target.getId(), newScopeRoleMapping, newExpiresAt);
        if (!losingAdmin.isEmpty()) {
            return email + " is the only admin of " + getScopeDisplayLabel(losingAdmin.get(0)) + ". Make someone else an admin first.";
        }
        return null;
    }

    private String validateGivenRole(int callerId, int accountId, String scope, String role) {
        if (role == null || !RoleAssignment.isExistingRole(role)) {
            return "The role " + role + " doesn't exist anymore. Pick another role.";
        }
        if (Role.fromName(role) == Role.NO_ACCESS) {
            return null;
        }
        if (!isValidProductScope(scope)) {
            return "Your account doesn't include " + getScopeDisplayLabel(scope) + ".";
        }
        if (!RoleAssignment.canAssign(callerId, accountId, scope, role)) {
            return "You can't give the " + role + " role.";
        }
        return null;
    }

    private void accessChanged(int userId, int accountId) {
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userId, accountId));
        UsersCollectionsList.deleteCollectionIdsFromCache(userId, accountId);
        RbacCacheVersionDao.accessChanged(accountId);
    }

    @Audit(description = "User removed a user from the account", resource = Resource.USER_ACCESS, operation = Operation.DELETE, metadataGenerators = {"auditAccessChange"})
    public String removeUser() {
        int accountId = Context.accountId.get();
        User target = findAccountUser(accountId);
        if (target == null) {
            // not a member yet: revoke the pending invite in this account only
            DeleteResult deleted = PendingInviteCodesDao.instance.getMCollection().deleteMany(Filters.and(
                    Filters.eq(PendingInviteCode.ACCOUNT_ID, accountId), Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, email)));
            if (deleted.getDeletedCount() > 0) {
                return Action.SUCCESS.toUpperCase();
            }
            addActionError("This user is not in your account.");
            return Action.ERROR.toUpperCase();
        }
        String error = validateAccessChange(target, accountId, null, 0);
        if (error != null) {
            addActionError(error);
            return Action.ERROR.toUpperCase();
        }
        UsersDao.instance.updateOne(Filters.eq(User.LOGIN, email), Updates.unset("accounts." + accountId));
        RBACDao.instance.deleteAll(rbacFilter(target.getId(), accountId));
        accessChanged(target.getId(), accountId);
        return Action.SUCCESS.toUpperCase();
    }

    private String userRole;

    // older API: one role for every product (or per-product roles when scopeRoleMapping is sent)
    @Audit(description = "User changed another user's role", resource = Resource.USER_ACCESS, operation = Operation.UPDATE, metadataGenerators = {"auditAccessChange"})
    public String makeAdmin(){
        int accountId = Context.accountId.get();
        User target = findAccountUser(accountId);
        boolean perProduct = this.scopeRoleMapping != null && !this.scopeRoleMapping.isEmpty();
        if (!perProduct && (this.userRole == null || this.userRole.trim().isEmpty())) {
            addActionError("Pick a role.");
            return Action.ERROR.toUpperCase();
        }
        Map<String, String> newMapping = new HashMap<>();
        if (perProduct) {
            newMapping.putAll(this.scopeRoleMapping);
        } else {
            for (String scope : RoleAssignment.productScopes(accountId)) {
                newMapping.put(scope, this.userRole.toUpperCase());
            }
        }
        RBAC current = target == null ? null : RBACDao.instance.findOne(rbacFilter(target.getId(), accountId));
        String error = validateAccessChange(target, accountId, newMapping, current == null ? 0 : current.getAccessExpiresAt());
        if (error != null) {
            addActionError(error);
            return Action.ERROR.toUpperCase();
        }
        // per-product roles win over the single role, so users who have them get the role in every product
        boolean hasPerProductRoles = current != null && current.getScopeRoleMapping() != null && !current.getScopeRoleMapping().isEmpty();
        RBACDao.instance.updateOneNoUpsert(rbacFilter(target.getId(), accountId), perProduct || hasPerProductRoles
                ? Updates.set(RBAC.SCOPE_ROLE_MAPPING, newMapping)
                : Updates.set(RBAC.ROLE, this.userRole.toUpperCase()));
        accessChanged(target.getId(), accountId);
        return Action.SUCCESS.toUpperCase();
    }

    private Map<String, String> scopeRoleMapping;

    // optional: epoch seconds when the user's access ends (0 = never, null = unchanged)
    private Integer accessExpiresAt;

    public void setAccessExpiresAt(Integer accessExpiresAt) {
        this.accessExpiresAt = accessExpiresAt;
    }

    // audit: the user's roles before this request and what was asked for (read before the action runs)
    public String auditAccessChange() {
        String before = "none";
        User target = email == null ? null : UsersDao.instance.findOne(Filters.eq(User.LOGIN, email));
        if (target != null) {
            RBAC rbac = RBACDao.instance.findOne(Filters.and(Filters.eq(RBAC.USER_ID, target.getId()), Filters.eq(RBAC.ACCOUNT_ID, Context.accountId.get())));
            before = rbac == null ? "none" : rbac.accessSummary();
        }
        String after = scopeRoleMapping != null && !scopeRoleMapping.isEmpty() ? new TreeMap<>(scopeRoleMapping).toString() : String.valueOf(userRole);
        return "user=" + email + " before=" + before + " requested=" + after + (accessExpiresAt != null ? " accessExpiresAt=" + accessExpiresAt : "");
    }

    @Audit(description = "User changed another user's product roles", resource = Resource.USER_ACCESS, operation = Operation.UPDATE, metadataGenerators = {"auditAccessChange"})
    public String updateUserScopeRoleMapping() {
        int accId = Context.accountId.get();
        int callerId = getSUser().getId();
        User userDetails = findAccountUser(accId);

        // products left out get no access; an empty request used to give the default role everywhere instead
        if (this.scopeRoleMapping == null || this.scopeRoleMapping.isEmpty()) {
            addActionError("Pick at least one product, or remove the user.");
            return Action.ERROR.toUpperCase();
        }

        RBAC current = userDetails == null ? null : RBACDao.instance.findOne(rbacFilter(userDetails.getId(), accId));
        // null keeps the current expiry, 0 removes it; only admins of all collections set or remove it
        boolean changeExpiry = accessExpiresAt != null && RoleAssignment.isUnlimitedAdmin(callerId, accId);
        int newExpiresAt = changeExpiry ? Math.max(accessExpiresAt, 0) : (current == null ? 0 : current.getAccessExpiresAt());
        if (changeExpiry && newExpiresAt > 0 && newExpiresAt <= Context.now()) {
            addActionError("Pick an expiry date in the future.");
            return Action.ERROR.toUpperCase();
        }

        String error = validateAccessChange(userDetails, accId, this.scopeRoleMapping, newExpiresAt);
        if (error != null) {
            addActionError(error);
            return Action.ERROR.toUpperCase();
        }

        try {
            List<Bson> updates = new ArrayList<>(Arrays.asList(
                    Updates.set(RBAC.SCOPE_ROLE_MAPPING, scopeRoleMapping),
                    Updates.setOnInsert(RBAC.USER_ID, userDetails.getId()),
                    Updates.setOnInsert(RBAC.ACCOUNT_ID, accId)
            ));
            if (changeExpiry) {
                updates.add(Updates.set(RBAC.ACCESS_EXPIRES_AT, newExpiresAt));
            }
            RBACDao.instance.getMCollection().updateOne(
                    rbacFilter(userDetails.getId(), accId),
                    Updates.combine(updates),
                    new UpdateOptions().upsert(true)
            );
            accessChanged(userDetails.getId(), accId);
            return Action.SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error updating scope-role mapping: " + e.getMessage());
            addActionError("Couldn't save access. Please try again.");
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

    private List<String> assignableRoles;

    // roles a team admin may give in this product; null for everyone else (the role hierarchy applies)
    public String fetchAssignableRoles() {
        Set<String> assignable = RoleAssignment.limitedAssignableRoles(getSUser().getId(), Context.accountId.get());
        this.assignableRoles = assignable == null ? null : new ArrayList<>(assignable);
        return Action.SUCCESS.toUpperCase();
    }

    public List<String> getAssignableRoles() {
        return assignableRoles;
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

        User forgotPasswordUser = UsersDao.instance.findOne(Filters.and(Filters.eq(User.LOGIN, userEmail), Filters.exists(User.ACCOUNTS + "." + Context.accountId.get())));
        if(forgotPasswordUser == null) {
            addActionError("User not found.");
            return Action.ERROR.toUpperCase();
        }

        // passwords are shared across accounts, so an admin of one account must not get a login to the user's other accounts
        if (forgotPasswordUser.getAccounts() != null && forgotPasswordUser.getAccounts().size() > 1) {
            addActionError("This user belongs to other accounts too. Ask them to use 'Forgot password' on the login page.");
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
            addActionError("Couldn't create the reset link. Please try again.");
            return Action.ERROR.toUpperCase();
        }

        return Action.SUCCESS.toUpperCase();
    }



    public String removeInvitation() {
        int accountId = Context.accountId.get();
        PendingInviteCode pendingInviteCode = PendingInviteCodesDao.instance.findOne(Filters.and(
                Filters.eq(PendingInviteCode.ACCOUNT_ID, accountId), Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, email)));
        if (pendingInviteCode == null) {
            addActionError("This invite was already accepted or revoked.");
            return Action.ERROR.toUpperCase();
        }

        int callerId = getSUser().getId();
        boolean isIssuer = pendingInviteCode.getIssuer() == callerId;
        boolean canRevoke = isIssuer || RoleAssignment.isUnlimitedAdmin(callerId, accountId)
                || RoleAssignment.canManage(callerId, accountId, pendingInviteCode.getScopeRoleMapping(), pendingInviteCode.getInviteeRole());
        if (!canRevoke) {
            addActionError("You can't revoke this invite: it gives a role you can't give.");
            return Action.ERROR.toUpperCase();
        }
        PendingInviteCodesDao.instance.getMCollection().deleteOne(Filters.and(
                Filters.eq(PendingInviteCode.ACCOUNT_ID, accountId), Filters.eq(PendingInviteCode.INVITEE_EMAIL_ID, email)));
        return SUCCESS.toUpperCase();
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
