package com.akto.action;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.akto.audit_logs_util.Audit;
import com.akto.dao.ConfigsDao;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.RbacCacheVersionDao;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config.OktaConfig;
import com.akto.dto.CustomRole;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.dto.rbac.CollectionRule;
import com.akto.dto.sso.SAMLConfig;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.util.Pair;
import com.akto.utils.RoleAssignment;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;

import lombok.Setter;

public class RoleAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(RoleAction.class, LogDb.DASHBOARD);

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

    // role name -> {users, invites} holding it in this account, for the roles screen and the delete check
    private Map<String, Map<String, Integer>> roleUsage;

    public Map<String, Map<String, Integer>> getRoleUsage() {
        return roleUsage;
    }

    // base role -> feature -> access, so the roles screen can show what "same as base role" means
    private Map<String, Map<String, String>> baseRolePermissions;

    public Map<String, Map<String, String>> getBaseRolePermissions() {
        return baseRolePermissions;
    }

    public String getCustomRoles() {
        /*
         * Need all data for a role, 
         * thus no projections being used.
         */
        // rule matches can be thousands of ids and the page doesn't use them
        roles = CustomRoleDao.instance.findAll(new BasicDBObject(), Projections.exclude(CustomRole.RULE_COLLECTION_IDS));
        roleUsage = countRoleUsage(Context.accountId.get());
        baseRolePermissions = new HashMap<>();
        for (Role role : Role.values()) {
            Map<String, String> access = new HashMap<>();
            for (Feature feature : Feature.values()) {
                access.put(feature.name(), role.getReadWriteAccessForFeature(feature).name());
            }
            baseRolePermissions.put(role.name(), access);
        }
        return SUCCESS.toUpperCase();
    }

    private static void addUsage(Map<String, Map<String, Integer>> usage, String role, String kind) {
        if (role == null || Role.fromName(role) != null) {
            return;
        }
        usage.computeIfAbsent(role, r -> new HashMap<>()).merge(kind, 1, Integer::sum);
    }

    private static Map<String, Map<String, Integer>> countRoleUsage(int accountId) {
        Map<String, Map<String, Integer>> usage = new HashMap<>();
        for (RBAC rbac : RBACDao.instance.findAll(Filters.eq(RBAC.ACCOUNT_ID, accountId))) {
            for (String role : rolesHeld(rbac.getScopeRoleMapping(), rbac.getRole())) {
                addUsage(usage, role, "users");
            }
        }
        for (PendingInviteCode invite : PendingInviteCodesDao.instance.findAll(Filters.eq(PendingInviteCode.ACCOUNT_ID, accountId))) {
            for (String role : rolesHeld(invite.getScopeRoleMapping(), invite.getInviteeRole())) {
                addUsage(usage, role, "invites");
            }
        }
        return usage;
    }

    // distinct roles held: the per-product roles, or the older single role
    private static java.util.Set<String> rolesHeld(Map<String, String> scopeRoleMapping, String singleRole) {
        java.util.Set<String> held = new java.util.HashSet<>();
        if (scopeRoleMapping != null && !scopeRoleMapping.isEmpty()) {
            held.addAll(scopeRoleMapping.values());
        } else if (singleRole != null) {
            held.add(singleRole);
        }
        return held;
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
                addActionError("Use only letters, numbers, - and _ in role names.");
                return false;
            }
        }

        try {
            /*
             * We do not want role name from the reserved names.
             */
            Role.valueOf(this.roleName.toUpperCase());
            addActionError(this.roleName + " is a built-in role name. Pick another name.");
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
                    addActionError(role.getName() + " is already the default invite role. Turn it off there first.");
                    return false;
                }
            }
        }
        return true;
    }

    @Setter
    private boolean threatProtectionEnabled;

    /** Labels used in messages, matching the roles screen. */
    private static String featureLabel(String feature) {
        switch (feature) {
            case "INVITE_MEMBERS": return "Invite users and change their roles";
            case "THREAT_PROTECTION": return "Threat protection";
            case "THREAT_SETTINGS": return "Threat settings";
            case "AI_AGENTS": return "AI agents";
            case "API_COLLECTIONS": return "API collections";
            case "SENSITIVE_DATA": return "Sensitive data";
            case "SAMPLE_DATA": return "Request and response samples";
            case "START_TEST_RUN": return "Run tests";
            case "TEST_RESULTS": return "Test results";
            case "ISSUES": return "Issues";
            case "INTEGRATIONS": return "Integrations";
            case "API_TOKENS": return "API tokens";
            default: return feature;
        }
    }

    @Setter
    private Map<String, String> permissionOverrides;

    @Setter
    private List<CollectionRule> collectionRules;

    // what the rules match, worked out once while validating and saved with the role
    private List<Integer> ruleMatches;

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
        try {
            ruleMatches = new ArrayList<>(RBACDao.matchRules(collectionRules));
            java.util.Collections.sort(ruleMatches);
        } catch (Exception e) {
            // valid in Java but not in Mongo, e.g. \p{javaLowerCase}
            addActionError("This host pattern can't be used. Use a standard regular expression.");
            return false;
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
                addActionError(name + " can't be given by team admins: limit it to collections and don't base it on Admin.");
                return false;
            }
        }
        return true;
    }

    // users of a role cache their access; clear it on every dashboard instance so a role change applies right away
    private void clearRoleCaches() {
        CustomRoleDao.clearRoleCache();
        UsersCollectionsList.deleteAccountCollectionIdsFromCache(Context.accountId.get());
        RbacCacheVersionDao.accessChanged(Context.accountId.get());
    }

    // nobody edits or deletes a role they hold, so a role can never be used to widen its own access
    private boolean isOwnRole(String name) {
        RBAC rbac = RBACDao.instance.findOne(Filters.and(Filters.eq(RBAC.USER_ID, getSUser().getId()), Filters.eq(RBAC.ACCOUNT_ID, Context.accountId.get())));
        return rbac != null && rolesHeld(rbac.getScopeRoleMapping(), rbac.getRole()).contains(name);
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
                    addActionError(featureLabel(entry.getKey()) + " can't be changed for a role.");
                    return false;
                }
            } catch (Exception e) {
                addActionError("Unknown permission " + featureLabel(entry.getKey()) + ". Refresh the page and try again.");
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
            addActionError("A role named " + roleName + " already exists.");
            return ERROR.toUpperCase();
        }
        if (baseRole == null || Role.fromName(baseRole) == null || Role.fromName(baseRole) == Role.NO_ACCESS) {
            addActionError("Pick a base role.");
            return ERROR.toUpperCase();
        }
        baseRole = Role.fromName(baseRole).name();

        if(!defaultInviteCheck() || !validatePermissionOverrides() || !validateCollectionRules() || !validateAssignableRoles()){
            return ERROR.toUpperCase();
        }

        CustomRole role = new CustomRole(roleName, baseRole, apiCollectionIds, defaultInviteRole, threatProtectionEnabled, new ArrayList<>());
        role.setPermissionOverrides(permissionOverrides);
        role.setCollectionRules(collectionRules);
        role.setAssignableRoles(assignableRoles);
        if (collectionRules != null && !collectionRules.isEmpty()) {
            role.setRuleCollectionIds(ruleMatches);
        }
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
            addActionError("This role was deleted. Refresh the page.");
            return ERROR.toUpperCase();
        }
        if (isOwnRole(roleName)) {
            addActionError("You can't change a role you have. Ask another admin.");
            return ERROR.toUpperCase();
        }

        if (baseRole == null || Role.fromName(baseRole) == null || Role.fromName(baseRole) == Role.NO_ACCESS) {
            addActionError("Pick a base role.");
            return ERROR.toUpperCase();
        }
        baseRole = Role.fromName(baseRole).name();

        if(!defaultInviteCheck() && !existingRole.getDefaultInviteRole()){
            return ERROR.toUpperCase();
        }
        if (!validatePermissionOverrides() || !validateCollectionRules() || !validateAssignableRoles()) {
            return ERROR.toUpperCase();
        }
        if (assignableRoles != null && assignableRoles.contains(roleName)) {
            addActionError("A role can't give itself.");
            return ERROR.toUpperCase();
        }

        // fields left out of the request (e.g. from an older page) keep their saved values
        List<org.bson.conversions.Bson> updates = new ArrayList<>(java.util.Arrays.asList(
            Updates.set(CustomRole.BASE_ROLE, baseRole),
            Updates.set(CustomRole.DEFAULT_INVITE_ROLE, defaultInviteRole),
            Updates.set(CustomRole.THREAT_PROTECTION_ENABLED, threatProtectionEnabled)
        ));
        if (apiCollectionIds != null) updates.add(Updates.set(CustomRole.API_COLLECTIONS_ID, apiCollectionIds));
        if (permissionOverrides != null) updates.add(Updates.set(CustomRole.PERMISSION_OVERRIDES, permissionOverrides));
        if (collectionRules != null) {
            updates.add(Updates.set(CustomRole.COLLECTION_RULES, collectionRules));
            updates.add(Updates.set(CustomRole.RULE_COLLECTION_IDS, ruleMatches));
        }
        if (assignableRoles != null) updates.add(Updates.set(CustomRole.ASSIGNABLE_ROLES, assignableRoles));
        CustomRoleDao.instance.updateOne(Filters.eq(CustomRole._NAME, roleName), Updates.combine(updates));
        clearRoleCaches();
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(getSUser().getId(), Context.accountId.get()));

        return SUCCESS.toUpperCase();
    }

    // the SSO provider whose group mapping gives this role, or null
    private static String ssoMappingUsing(String roleName) {
        int accountId = Context.accountId.get();
        try {
            for (Object config : SSOConfigsDao.instance.findAll(Filters.eq(Constants.ID, String.valueOf(accountId)))) {
                if (config instanceof SAMLConfig) {
                    Map<String, String> mapping = ((SAMLConfig) config).getGroupRoleMapping();
                    if (mapping != null && mapping.containsValue(roleName)) {
                        return "SAML SSO";
                    }
                }
            }
            Object okta = ConfigsDao.instance.findOne(Constants.ID, OktaConfig.getOktaId(accountId));
            if (okta instanceof OktaConfig) {
                Map<String, String> mapping = ((OktaConfig) okta).getOktaGroupToAktoUserRoleMap();
                if (mapping != null && mapping.containsValue(roleName)) {
                    return "Okta";
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb(e, "Error checking SSO mappings for role " + roleName + ": " + e.getMessage());
        }
        return null;
    }

    @Audit(description = "User deleted a custom role", resource = Resource.CUSTOM_ROLE, operation = Operation.DELETE, metadataGenerators = {"auditRoleChange"})
    public String deleteCustomRole(){
        CustomRole existingRole = CustomRoleDao.instance.findRoleByName(roleName);

        if (existingRole == null) {
            addActionError("This role was already deleted. Refresh the page.");
            return ERROR.toUpperCase();
        }

        // users and pending invites of this account only; a role with the same name in another account is unrelated
        Map<String, Integer> usage = countRoleUsage(Context.accountId.get()).getOrDefault(roleName, new HashMap<>());
        int users = usage.getOrDefault("users", 0);
        int invites = usage.getOrDefault("invites", 0);
        if (users > 0 || invites > 0) {
            String who = users > 0 ? users + (users == 1 ? " user has" : " users have") : invites + (invites == 1 ? " pending invite uses" : " pending invites use");
            addActionError(who + " this role. Give them another role first.");
            return ERROR.toUpperCase();
        }
        String ssoUse = ssoMappingUsing(roleName);
        if (ssoUse != null) {
            addActionError("The " + ssoUse + " group mapping uses this role. Remove it from the SSO settings first.");
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
