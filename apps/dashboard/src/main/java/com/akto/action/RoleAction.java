package com.akto.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.akto.audit_logs_util.Audit;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.rbac.CollectionRule;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.util.Pair;
import com.akto.utils.RoleAssignment;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import lombok.Setter;

public class RoleAction extends UserAction {

    /*
     * Create Role.
     * Update Role.
     * Delete Role. -> If no user is associated with the role.
     * Get Roles.
     */

    List<CustomRole> roles;

    public List<CustomRole> getRoles() {
        return roles;
    }

    public String getCustomRoles() {
        /*
         * Need all data for a role, 
         * thus no projections being used.
         */
        roles = CustomRoleDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    List<Integer> apiCollectionIds;

    public void setApiCollectionIds(List<Integer> apiCollectionIds) {
        this.apiCollectionIds = apiCollectionIds;
    }

    String roleName;

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    String baseRole;

    public void setBaseRole(String baseRole) {
        this.baseRole = baseRole;
    }

    boolean defaultInviteRole;

    public void setDefaultInviteRole(boolean defaultInviteRole) {
        this.defaultInviteRole = defaultInviteRole;
    }

    private static final int MAX_ROLE_NAME_LENGTH = 50;

    public boolean validateRoleName() {
        if (this.roleName == null || this.roleName.isEmpty()) {
            addActionError("Role names cannot be empty.");
            return false;
        }

        if (this.roleName.length() > MAX_ROLE_NAME_LENGTH) {
            addActionError("Role names cannot be more than " + MAX_ROLE_NAME_LENGTH + " characters.");
            return false;
        }

        for (char c : this.roleName.toCharArray()) {
            boolean alphabets = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean numbers = c >= '0' && c <= '9';
            boolean specialChars = c == '-' || c == '_';

            if (!(alphabets || numbers || specialChars)) {
                addActionError("Role names can only be alphanumeric and contain '-'and '_'");
                return false;
            }
        }

        try {
            /*
             * We do not want role name from the reserved names.
             */
            Role.valueOf(this.roleName.toUpperCase());
            addActionError(this.roleName + " is a reserved keyword.");
            return false;
        } catch(Exception e){
        }

        return true;
    }

    private boolean defaultInviteCheck(){
        if(defaultInviteRole){
            List<CustomRole> roles = CustomRoleDao.instance.findAll(new BasicDBObject());
            for(CustomRole role: roles){
                if(role.getDefaultInviteRole()){
                    addActionError("Default invite role already exists.");
                    return false;
                }
            }
        }
        return true;
    }

    @Setter
    private boolean threatProtectionEnabled;

    @Setter
    private Map<String, String> permissionOverrides;

    @Setter
    private List<CollectionRule> collectionRules;

    private boolean validateCollectionRules() {
        if (collectionRules == null) {
            return true;
        }
        for (CollectionRule rule : collectionRules) {
            String error = rule == null ? "Invalid collection rule" : rule.validate();
            if (error != null) {
                addActionError(error);
                return false;
            }
        }
        return true;
    }

    @Setter
    private List<String> assignableRoles;

    // a team admin may only give scoped, non-admin custom roles, so it can never hand out access beyond its team
    private boolean validateAssignableRoles() {
        if (assignableRoles == null) {
            return true;
        }
        for (String name : assignableRoles) {
            if (!RoleAssignment.isGivableByTeamAdmin(CustomRoleDao.instance.findRoleByName(name))) {
                addActionError("Role " + name + " cannot be given by a team admin: it must exist, be limited to collections and not be based on Admin.");
                return false;
            }
        }
        return true;
    }

    // users of a role cache their collections; clear them so a role change applies right away
    private void clearRoleCaches() {
        CustomRoleDao.clearRoleCache();
        UsersCollectionsList.deleteAccountCollectionIdsFromCache(Context.accountId.get());
    }

    private boolean validatePermissionOverrides() {
        if (permissionOverrides == null) {
            return true;
        }
        for (Map.Entry<String, String> entry : permissionOverrides.entrySet()) {
            try {
                Feature feature = Feature.valueOf(entry.getKey());
                ReadWriteAccess.valueOf(entry.getValue());
                if (!CustomRole.isOverridable(feature)) {
                    addActionError(entry.getKey() + " cannot be changed for a role.");
                    return false;
                }
            } catch (Exception e) {
                addActionError("Invalid permission: " + entry.getKey() + " = " + entry.getValue());
                return false;
            }
        }
        return true;
    }

    // audit: the role as it was before this request and what was asked for
    public String auditRoleChange() {
        CustomRole existing = roleName == null ? null : CustomRoleDao.instance.findRoleByName(roleName.toUpperCase());
        String before = existing == null ? "none" : describeRole(existing.getBaseRole(), existing.getApiCollectionsId(),
                existing.getCollectionRules(), existing.getPermissionOverrides(), existing.getAssignableRoles());
        return "role=" + roleName + " before=" + before + " requested="
                + describeRole(baseRole, apiCollectionIds, collectionRules, permissionOverrides, assignableRoles);
    }

    private static String describeRole(String baseRole, List<Integer> collections, List<CollectionRule> rules,
                                        Map<String, String> overrides, List<String> assignable) {
        return "{base=" + baseRole + ", collections=" + collections + ", rules=" + rules
                + ", overrides=" + (overrides == null ? null : new TreeMap<>(overrides)) + ", canGive=" + assignable + "}";
    }

    @Audit(description = "User created a custom role", resource = Resource.CUSTOM_ROLE, operation = Operation.CREATE, metadataGenerators = {"auditRoleChange"})
    public String createCustomRole() {

        if (!validateRoleName()) {
            return ERROR.toUpperCase();
        }

        // Always save Upper-case.
        roleName = roleName.toUpperCase();

        CustomRole existingRole = CustomRoleDao.instance.findRoleByName(roleName);

        if (existingRole != null) {
            addActionError("Existing role with same name exists.");
            return ERROR.toUpperCase();
        }
        try {
            Role.valueOf(baseRole);
        } catch (Exception e) {
            addActionError("Base role does not exist");
            return ERROR.toUpperCase();
        }

        if(!defaultInviteCheck() || !validatePermissionOverrides() || !validateCollectionRules() || !validateAssignableRoles()){
            return ERROR.toUpperCase();
        }

        CustomRole role = new CustomRole(roleName, baseRole, apiCollectionIds, defaultInviteRole, threatProtectionEnabled, new ArrayList<>());
        role.setPermissionOverrides(permissionOverrides);
        role.setCollectionRules(collectionRules);
        role.setAssignableRoles(assignableRoles);
        CustomRoleDao.instance.insertOne(role);
        clearRoleCaches();
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(getSUser().getId(), Context.accountId.get()));
        return SUCCESS.toUpperCase();
    }

    @Audit(description = "User updated a custom role", resource = Resource.CUSTOM_ROLE, operation = Operation.UPDATE, metadataGenerators = {"auditRoleChange"})
    public String updateCustomRole(){
        if (!validateRoleName()) {
            return ERROR.toUpperCase();
        }
        CustomRole existingRole = CustomRoleDao.instance.findRoleByName(roleName);

        if (existingRole == null) {
            addActionError("Role does not exist.");
            return ERROR.toUpperCase();
        }

        try {
            Role.valueOf(baseRole);
        } catch (Exception e) {
            addActionError("Base role does not exist");
            return ERROR.toUpperCase();
        }

        if(!defaultInviteCheck() && !existingRole.getDefaultInviteRole()){
            return ERROR.toUpperCase();
        }
        if (!validatePermissionOverrides() || !validateCollectionRules() || !validateAssignableRoles()) {
            return ERROR.toUpperCase();
        }

        CustomRoleDao.instance.updateOne(Filters.eq(CustomRole._NAME, roleName),Updates.combine(
            Updates.set(CustomRole.BASE_ROLE, baseRole),
            Updates.set(CustomRole.API_COLLECTIONS_ID, apiCollectionIds),
            Updates.set(CustomRole.DEFAULT_INVITE_ROLE, defaultInviteRole),
            Updates.set(CustomRole.THREAT_PROTECTION_ENABLED, threatProtectionEnabled),
            Updates.set(CustomRole.PERMISSION_OVERRIDES, permissionOverrides),
            Updates.set(CustomRole.COLLECTION_RULES, collectionRules),
            Updates.set(CustomRole.ASSIGNABLE_ROLES, assignableRoles)
        ));
        clearRoleCaches();
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(getSUser().getId(), Context.accountId.get()));

        return SUCCESS.toUpperCase();
    }

    @Audit(description = "User deleted a custom role", resource = Resource.CUSTOM_ROLE, operation = Operation.DELETE, metadataGenerators = {"auditRoleChange"})
    public String deleteCustomRole(){
        CustomRole existingRole = CustomRoleDao.instance.findRoleByName(roleName);

        if (existingRole == null) {
            addActionError("Role does not exist.");
            return ERROR.toUpperCase();
        }

        List<RBAC> usersWithRole = RBACDao.instance.findAll(Filters.eq(RBAC.ROLE, roleName));

        List<RBAC> newUsersWithRole = RBACDao.instance.findAll(
                        Filters.exists(RBAC.SCOPE_ROLE_MAPPING)
                ).stream()
                .filter(rbac -> rbac.getScopeRoleMapping() != null &&
                        rbac.getScopeRoleMapping().containsValue(roleName))
                .collect(Collectors.toList());

        if(!usersWithRole.isEmpty() || !newUsersWithRole.isEmpty()){
            addActionError("Role is associated with users. Cannot delete.");
            return ERROR.toUpperCase();
        }

        /*
         * Alt. approach: Delete all pending invites associated with the role.
         */
        List<PendingInviteCode> pendingInviteCodes = PendingInviteCodesDao.instance.findAll(Filters.eq(PendingInviteCode.INVITEE_ROLE, roleName));
        if(!pendingInviteCodes.isEmpty()){
            addActionError("Role is associated with pending invites. Cannot delete.");
            return ERROR.toUpperCase();
        }

        CustomRoleDao.instance.deleteAll(Filters.eq(CustomRole._NAME, roleName));
        // a role created later with the same name must not become givable by team admins on its own
        CustomRoleDao.instance.updateMany(Filters.eq(CustomRole.ASSIGNABLE_ROLES, roleName), Updates.pull(CustomRole.ASSIGNABLE_ROLES, roleName));
        clearRoleCaches();
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(getSUser().getId(), Context.accountId.get()));

        return SUCCESS.toUpperCase();
    }   

}
