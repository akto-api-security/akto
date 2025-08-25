package com.akto.action;

import java.util.List;
import java.util.stream.Collectors;

import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.util.Pair;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import lombok.Getter;
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
            Role.valueOf(this.roleName);
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
    private List<String> allowedFeaturesForUser;

    @Getter
    List<String> allowedFeaturesForRBAC;

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

        if(!defaultInviteCheck()){
            return ERROR.toUpperCase();
        }

        if(allowedFeaturesForUser != null && !allowedFeaturesForUser.isEmpty()) {
            allowedFeaturesForUser = allowedFeaturesForUser.stream()
                .filter(feature -> RBAC.SPECIAL_FEATURES_FOR_RBAC.contains(feature))
                .collect(Collectors.toList());
        }

        CustomRole role = new CustomRole(roleName, baseRole, apiCollectionIds, defaultInviteRole, allowedFeaturesForUser);
        CustomRoleDao.instance.insertOne(role);
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(getSUser().getId(), Context.accountId.get()));
        return SUCCESS.toUpperCase();
    }

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

        if(allowedFeaturesForUser != null && !allowedFeaturesForUser.isEmpty()) {
            allowedFeaturesForUser = allowedFeaturesForUser.stream()
                .filter(feature -> RBAC.SPECIAL_FEATURES_FOR_RBAC.contains(feature))
                .collect(Collectors.toList());
        }

        CustomRoleDao.instance.updateOne(Filters.eq(CustomRole._NAME, roleName),Updates.combine(
            Updates.set(CustomRole.BASE_ROLE, baseRole),
            Updates.set(CustomRole.API_COLLECTIONS_ID, apiCollectionIds),
            Updates.set(CustomRole.DEFAULT_INVITE_ROLE, defaultInviteRole),
            Updates.set(RBAC.ALLOWED_FEATURES_FOR_USER, allowedFeaturesForUser)
        ));
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(getSUser().getId(), Context.accountId.get()));

        return SUCCESS.toUpperCase();
    }

    public String deleteCustomRole(){
        CustomRole existingRole = CustomRoleDao.instance.findRoleByName(roleName);

        if (existingRole == null) {
            addActionError("Role does not exist.");
            return ERROR.toUpperCase();
        }

        List<RBAC> usersWithRole = RBACDao.instance.findAll(Filters.eq(RBAC.ROLE, roleName));

        if(!usersWithRole.isEmpty()){
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
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(getSUser().getId(), Context.accountId.get()));

        return SUCCESS.toUpperCase();
    }

    public String allowedFeaturesForRBAC(){
        this.allowedFeaturesForRBAC = RBAC.SPECIAL_FEATURES_FOR_RBAC;
        return SUCCESS.toUpperCase();
    }

}
