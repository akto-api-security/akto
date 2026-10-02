package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.action.ApiCollectionsAction;
import com.akto.action.RoleAction;
import com.akto.action.TeamAction;
import com.akto.action.user.AzureSsoAction;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.PendingInviteCodesDao;
import com.akto.dao.RBACDao;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.CustomRole;
import com.akto.dto.PendingInviteCode;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.sso.SAMLConfig;
import com.akto.interceptor.RoleAccessInterceptor;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

/*
 * Changes to users' access: who may change whom, what they may give, the last admin, deleted roles,
 * invites, and partial updates from older pages. Users and roles come from TestRoleAssignment.
 */
public class TestAccessChanges extends MongoBasedTest {

    static final int ADMIN = TestRoleAssignment.ADMIN, MEMBER = TestRoleAssignment.MEMBER, GUEST = TestRoleAssignment.GUEST,
            TEAM_ADMIN = TestRoleAssignment.TEAM_ADMIN, TEAM_USER = TestRoleAssignment.TEAM_USER,
            THREAT_ENGINEER = TestRoleAssignment.THREAT_ENGINEER, DEVELOPER = 11, SECOND_ADMIN = 12, LIMITED_ADMIN = 13;

    @Before
    public void setup() {
        PendingInviteCodesDao.instance.getMCollection().drop();
        SSOConfigsDao.instance.getMCollection().drop();
        new TestRoleAssignment().setup();
        TestRoleAssignment.insertUser(DEVELOPER, "DEVELOPER");
        TestRoleAssignment.insertRole("LIMITED_ADMIN_ROLE", "ADMIN", Arrays.asList(11), null);
        TestRoleAssignment.insertUser(LIMITED_ADMIN, "LIMITED_ADMIN_ROLE");
        clearCaches();
    }

    static void clearCaches() {
        TestRoleAssignment.clearCaches();
        for (int id = 11; id <= 20; id++) {
            RBACDao.instance.deleteUserEntryFromCache(new com.akto.util.Pair<>(id, ACCOUNT_ID));
            com.akto.dto.rbac.UsersCollectionsList.deleteCollectionIdsFromCache(id, ACCOUNT_ID);
        }
    }

    static Map<String, Object> session(int userId) {
        Map<String, Object> session = new HashMap<>();
        session.put("user", TestRoleAssignment.user(userId));
        return session;
    }

    static String email(int id) {
        return "user" + id + "@example.com";
    }

    static TeamAction teamAction(int caller, int target) {
        TeamAction action = new TeamAction();
        action.setSession(session(caller));
        action.setEmail(email(target));
        return action;
    }

    static String change(int caller, int target, Map<String, String> mapping, Integer expiresAt) {
        TeamAction action = teamAction(caller, target);
        action.setScopeRoleMapping(mapping);
        action.setAccessExpiresAt(expiresAt);
        String result = action.updateUserScopeRoleMapping();
        clearCaches();
        return result;
    }

    static String change(int caller, int target, String apiRole) {
        return change(caller, target, Collections.singletonMap("API", apiRole), null);
    }

    static String firstError(com.opensymphony.xwork2.ActionSupport action) {
        return action.getActionErrors().isEmpty() ? null : action.getActionErrors().iterator().next();
    }

    static void setMapping(int userId, Map<String, String> mapping, String legacyRole) {
        RBACDao.instance.updateOne(Filters.and(Filters.eq(RBAC.USER_ID, userId), Filters.eq(RBAC.ACCOUNT_ID, ACCOUNT_ID)),
                Updates.combine(Updates.set(RBAC.SCOPE_ROLE_MAPPING, mapping), Updates.set(RBAC.ROLE, legacyRole)));
        clearCaches();
    }

    // ── Who may change access ─────────────────────────────────────────────────

    @Test
    public void testOnlyRolesWithInvitePermissionChangeOthers() {
        assertEquals("ERROR", change(GUEST, DEVELOPER, "NO_ACCESS"));      // guests can't change anyone, not even to no access
        assertEquals("ERROR", change(DEVELOPER, GUEST, "GUEST"));          // nor developers
        assertEquals("SUCCESS", change(MEMBER, GUEST, "DEVELOPER"));       // security engineers can, within their hierarchy
        assertEquals("SUCCESS", change(THREAT_ENGINEER, GUEST, "MEMBER"));
    }

    @Test
    public void testUsefulErrors() {
        TeamAction self = teamAction(ADMIN, ADMIN);
        self.setScopeRoleMapping(Collections.singletonMap("API", "GUEST"));
        assertEquals("ERROR", self.updateUserScopeRoleMapping());
        assertEquals("You can't change your own access. Ask another admin.", firstError(self));

        TeamAction missing = teamAction(ADMIN, 99);
        missing.setScopeRoleMapping(Collections.singletonMap("API", "GUEST"));
        assertEquals("ERROR", missing.updateUserScopeRoleMapping());
        assertEquals("This user is not in your account.", firstError(missing));

        TeamAction empty = teamAction(ADMIN, MEMBER);
        empty.setScopeRoleMapping(new HashMap<>());
        assertEquals("ERROR", empty.updateUserScopeRoleMapping()); // used to give the default role everywhere
        assertEquals("MEMBER", RBACDao.instance.findOne(Filters.eq(RBAC.USER_ID, MEMBER)).getScopeRoleMapping().get("API"));

        TeamAction deleted = teamAction(ADMIN, MEMBER);
        deleted.setScopeRoleMapping(Collections.singletonMap("API", "NO_SUCH_ROLE"));
        assertEquals("ERROR", deleted.updateUserScopeRoleMapping());
        assertEquals("The role NO_SUCH_ROLE doesn't exist anymore. Pick another role.", firstError(deleted));

        TeamAction above = teamAction(MEMBER, GUEST);
        above.setScopeRoleMapping(Collections.singletonMap("API", "ADMIN"));
        assertEquals("ERROR", above.updateUserScopeRoleMapping());
        assertEquals("You can't give the ADMIN role.", firstError(above));
    }

    @Test
    public void testExplicitNoAccessEverywhereIsAllowed() {
        // team admins can't remove users, so taking all access away goes through the roles
        assertEquals("SUCCESS", change(TEAM_ADMIN, TEAM_USER, "NO_ACCESS"));
        assertEquals(Role.NO_ACCESS, RBACDao.getCurrentRoleForUser(TEAM_USER, ACCOUNT_ID));
    }

    @Test
    public void testAdminOfOneProductManagesUsersInOthers() {
        // an admin of API with no role in Argus can still change a user's Argus role, as before
        Map<String, String> argusMember = new HashMap<>();
        argusMember.put("AGENTIC", "MEMBER");
        setMapping(GUEST, argusMember, null);
        Map<String, String> argusGuest = new HashMap<>();
        argusGuest.put("AGENTIC", "GUEST");
        assertEquals("SUCCESS", change(ADMIN, GUEST, argusGuest, null));
        // a security engineer of API only cannot: they have no role in Argus
        assertEquals("ERROR", change(MEMBER, GUEST, argusMember, null));
    }

    // ── What may be given ─────────────────────────────────────────────────────

    @Test
    public void testCustomRoleCannotGiveMoreThanTheCallerHas() {
        CustomRole ops = new CustomRole("OPS", "GUEST", new ArrayList<>(), false, false, new ArrayList<>());
        ops.setPermissionOverrides(Collections.singletonMap("INTEGRATIONS", "READ_WRITE")); // security engineers only read integrations
        CustomRoleDao.instance.insertOne(ops);
        CustomRole viewer = new CustomRole("VIEWER", "GUEST", new ArrayList<>(), false, false, new ArrayList<>());
        viewer.setPermissionOverrides(Collections.singletonMap("SAMPLE_DATA", "READ")); // security engineers have this
        CustomRoleDao.instance.insertOne(viewer);
        CustomRole threat = new CustomRole("THREAT_GUESTS", "DEVELOPER", new ArrayList<>(), false, true, new ArrayList<>());
        CustomRoleDao.instance.insertOne(threat); // threat toggle on: security engineers have no threat access
        clearCaches();

        assertFalse(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "API", "OPS"));
        assertTrue(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "API", "VIEWER"));
        assertFalse(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "API", "THREAT_GUESTS"));
        assertTrue(RoleAssignment.canAssign(THREAT_ENGINEER, ACCOUNT_ID, "API", "THREAT_GUESTS"));
        assertTrue(RoleAssignment.canAssign(ADMIN, ACCOUNT_ID, "API", "OPS"));
        // built-in roles keep the hierarchy as it was, even where a lower role has more of something (developers write settings)
        assertTrue(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "API", "DEVELOPER"));
    }

    @Test
    public void testLimitedUsersInviteOnlyTheirListedRoles() {
        // a security engineer limited to some collections on their own user can't give unlimited roles any more
        RBACDao.instance.updateOne(Filters.eq(RBAC.USER_ID, MEMBER), Updates.set(RBAC.API_COLLECTIONS_ID, Arrays.asList(11)));
        clearCaches();
        assertFalse(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "API", "GUEST"));
        assertTrue(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "API", "NO_ACCESS"));
    }

    // ── Collections ───────────────────────────────────────────────────────────

    @Test
    public void testDemotedAdminLosesAllCollections() {
        // the older single role field still says ADMIN, but the product role decides
        TestRoleAssignment.insertUser(SECOND_ADMIN, "TEAM_A_USER");
        RBACDao.instance.updateOne(Filters.eq(RBAC.USER_ID, SECOND_ADMIN), Updates.set(RBAC.ROLE, "ADMIN"));
        clearCaches();
        assertEquals(new java.util.HashSet<>(Arrays.asList(11, 12)),
                new java.util.HashSet<>(RBACDao.instance.getUserCollectionsById(SECOND_ADMIN, ACCOUNT_ID)));
        // a user with only the older single role keeps it
        RBACDao.instance.updateOne(Filters.eq(RBAC.USER_ID, SECOND_ADMIN), Updates.unset(RBAC.SCOPE_ROLE_MAPPING));
        clearCaches();
        assertNull(RBACDao.instance.getUserCollectionsById(SECOND_ADMIN, ACCOUNT_ID));
    }

    @Test
    public void testSavingUserCollections() {
        ApiCollectionsAction action = new ApiCollectionsAction();
        action.setSession(session(ADMIN));
        Map<String, List<Integer>> grants = new HashMap<>();
        // the users list shows a "no collections" placeholder; it is never saved as a grant
        grants.put(String.valueOf(TEAM_USER), Arrays.asList(11, 12, 13, RBACDao.NO_COLLECTION_ID));
        action.setUserCollectionMap(grants);
        assertEquals("SUCCESS", action.updateUserCollections());
        // the role already gives 11 and 12; only the extra collection is kept on the user
        assertEquals(Collections.singletonList(13), RBACDao.instance.findOne(Filters.eq(RBAC.USER_ID, TEAM_USER)).getApiCollectionsId());

        ApiCollectionsAction self = new ApiCollectionsAction();
        self.setSession(session(ADMIN));
        self.setUserCollectionMap(Collections.singletonMap(String.valueOf(ADMIN), Collections.singletonList(11)));
        assertEquals("ERROR", self.updateUserCollections());
    }

    // ── Last admin ────────────────────────────────────────────────────────────

    @Test
    public void testLastAdminIsKept() {
        // the only admin with no expiry, changed by an admin whose own access expires
        TestRoleAssignment.insertUser(SECOND_ADMIN, "ADMIN");
        RBACDao.instance.updateOne(Filters.eq(RBAC.USER_ID, SECOND_ADMIN), Updates.set(RBAC.ACCESS_EXPIRES_AT, Context.now() + 3600));
        clearCaches();

        TeamAction demote = teamAction(SECOND_ADMIN, ADMIN);
        demote.setScopeRoleMapping(Collections.singletonMap("API", "MEMBER"));
        assertEquals("ERROR", demote.updateUserScopeRoleMapping());
        assertEquals(email(ADMIN) + " is the only admin of API Security. Make someone else an admin first.", firstError(demote));

        TeamAction remove = teamAction(SECOND_ADMIN, ADMIN);
        assertEquals("ERROR", remove.removeUser());

        // once there is another admin without expiry, the change goes through
        RBACDao.instance.updateOne(Filters.eq(RBAC.USER_ID, SECOND_ADMIN), Updates.set(RBAC.ACCESS_EXPIRES_AT, 0));
        clearCaches();
        assertEquals("SUCCESS", change(SECOND_ADMIN, ADMIN, "MEMBER"));
    }

    @Test
    public void testLastAdminCannotGetAnExpiry() {
        TestRoleAssignment.insertUser(SECOND_ADMIN, "ADMIN");
        clearCaches();
        assertEquals("SUCCESS", change(ADMIN, SECOND_ADMIN, Collections.singletonMap("API", "ADMIN"), Context.now() + 3600));
        // now ADMIN is the only admin without an expiry
        assertEquals("ERROR", change(SECOND_ADMIN, ADMIN, Collections.singletonMap("API", "ADMIN"), Context.now() + 3600));
    }

    // ── Removing users and invites ────────────────────────────────────────────

    @Test
    public void testRevokingInvitesStaysInTheAccount() {
        PendingInviteCodesDao.instance.insertOne(new PendingInviteCode("code-1", ADMIN, "new@example.com", 0, ACCOUNT_ID, "MEMBER"));
        PendingInviteCodesDao.instance.insertOne(new PendingInviteCode("code-2", 77, "new@example.com", 0, 999, "MEMBER"));

        TeamAction other = teamAction(ADMIN, 0);
        other.setEmail("nobody@example.com");
        assertEquals("ERROR", other.removeInvitation());
        assertEquals("This invite was already accepted or revoked.", firstError(other));

        TeamAction revoke = teamAction(ADMIN, 0);
        revoke.setEmail("new@example.com");
        assertEquals("SUCCESS", revoke.removeUser()); // not a member yet: revokes the invite, used to crash
        assertEquals(0, PendingInviteCodesDao.instance.count(Filters.eq(PendingInviteCode.ACCOUNT_ID, ACCOUNT_ID)));
        assertEquals(1, PendingInviteCodesDao.instance.count(Filters.eq(PendingInviteCode.ACCOUNT_ID, 999)));
    }

    @Test
    public void testRemoveUser() {
        TeamAction remove = teamAction(ADMIN, GUEST);
        assertEquals("SUCCESS", remove.removeUser());
        assertNull(RBACDao.instance.findOne(Filters.eq(RBAC.USER_ID, GUEST)));
        assertFalse(UsersDao.instance.findOne(Filters.eq(User.LOGIN, email(GUEST))).getAccounts().containsKey(String.valueOf(ACCOUNT_ID)));
    }

    @Test
    public void testOlderMakeAdminApiUsesTheSameChecks() {
        TeamAction raise = teamAction(MEMBER, GUEST);
        raise.setUserRole("admin");
        assertEquals("ERROR", raise.makeAdmin());
        TeamAction ok = teamAction(ADMIN, GUEST);
        ok.setUserRole("member");
        assertEquals("SUCCESS", ok.makeAdmin());
        clearCaches();
        assertEquals(Role.MEMBER, RBACDao.getCurrentRoleForUser(GUEST, ACCOUNT_ID)); // the per-product role changed, not just the older field
    }

    // ── Users, roles and SSO need access to all collections ───────────────────

    static String allCollectionsError(int userId) throws Exception {
        Context.userId.set(userId);
        RoleAccessInterceptor interceptor = new RoleAccessInterceptor();
        interceptor.setCollectionScope("ALL_COLLECTIONS");
        Method method = RoleAccessInterceptor.class.getDeclaredMethod("checkCollectionScope", Object.class, User.class, int.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, null, TestRoleAssignment.user(userId), ACCOUNT_ID);
    }

    @Test
    public void testAdminLimitedToCollectionsCannotManageAccess() throws Exception {
        assertEquals(Role.ADMIN, RBACDao.getCurrentRoleForUser(LIMITED_ADMIN, ACCOUNT_ID));
        assertEquals("Only admins with access to all collections can manage users, roles and SSO.", allCollectionsError(LIMITED_ADMIN));
        assertNull(allCollectionsError(ADMIN));
        assertFalse(RoleAssignment.isUnlimitedAdmin(LIMITED_ADMIN, ACCOUNT_ID));
        assertTrue(RoleAssignment.isUnlimitedAdmin(ADMIN, ACCOUNT_ID));
    }

    @Test
    public void testSessionWithoutAccountStillChecksRoles() throws Exception {
        // a session used straight from the API has no account yet; the request's account (chosen by UserDetailsFilter) is used
        Method method = RoleAccessInterceptor.class.getDeclaredMethod("getUserAccountId", Map.class);
        method.setAccessible(true);
        Context.accountId.set(ACCOUNT_ID);
        assertEquals(ACCOUNT_ID, method.invoke(new RoleAccessInterceptor(), new HashMap<String, Object>()));
        Map<String, Object> withAccount = new HashMap<>();
        withAccount.put("accountId", "999");
        assertEquals(999, method.invoke(new RoleAccessInterceptor(), withAccount));
        Context.accountId.remove();
        try {
            method.invoke(new RoleAccessInterceptor(), new HashMap<String, Object>());
            assertTrue("no account at all must fail", false);
        } catch (java.lang.reflect.InvocationTargetException expected) {
        } finally {
            Context.accountId.set(ACCOUNT_ID);
        }
    }

    // ── Custom roles ──────────────────────────────────────────────────────────

    static RoleAction roleAction(int caller, String name) {
        RoleAction action = new RoleAction();
        action.setSession(session(caller));
        action.setRoleName(name);
        action.setBaseRole("MEMBER");
        action.setApiCollectionIds(Arrays.asList(11));
        return action;
    }

    @Test
    public void testNobodyEditsTheirOwnRole() {
        RoleAction own = roleAction(LIMITED_ADMIN, "LIMITED_ADMIN_ROLE");
        own.setBaseRole("ADMIN");
        own.setApiCollectionIds(new ArrayList<>());
        assertEquals("ERROR", own.updateCustomRole());
        assertEquals("You can't change a role you have. Ask another admin.", firstError(own));
        assertEquals(Arrays.asList(11), CustomRoleDao.instance.findRoleByName("LIMITED_ADMIN_ROLE").getApiCollectionsId());
    }

    @Test
    public void testOlderPageKeepsNewRoleFields() {
        CustomRoleDao.instance.updateOne(Filters.eq(CustomRole._NAME, "TEAM_A_USER"), Updates.combine(
                Updates.set(CustomRole.PERMISSION_OVERRIDES, Collections.singletonMap("ISSUES", "READ")),
                Updates.set(CustomRole.COLLECTION_RULES, Collections.singletonList(new com.akto.dto.rbac.CollectionRule("^team-a", null, null)))));
        RoleAction older = roleAction(ADMIN, "TEAM_A_USER"); // sends no overrides, rules or assignable roles
        assertEquals("SUCCESS", older.updateCustomRole());
        CustomRole saved = CustomRoleDao.instance.findRoleByName("TEAM_A_USER");
        assertEquals(Collections.singletonMap("ISSUES", "READ"), saved.getPermissionOverrides());
        assertEquals(1, saved.getCollectionRules().size());
        assertEquals(Arrays.asList(11), saved.getApiCollectionsId());
    }

    @Test
    public void testDeletingARoleInUse() {
        RoleAction inUse = roleAction(ADMIN, "TEAM_A_USER");
        assertEquals("ERROR", inUse.deleteCustomRole());
        assertEquals("1 user has this role. Give them another role first.", firstError(inUse));

        // pending invites with per-product roles count too
        TestRoleAssignment.insertRole("INVITED_ROLE", "GUEST", Arrays.asList(11), null);
        PendingInviteCode invite = new PendingInviteCode("code-3", ADMIN, "new@example.com", 0, ACCOUNT_ID);
        invite.setScopeRoleMapping(Collections.singletonMap("API", "INVITED_ROLE"));
        PendingInviteCodesDao.instance.insertOne(invite);
        RoleAction invited = roleAction(ADMIN, "INVITED_ROLE");
        assertEquals("ERROR", invited.deleteCustomRole());
        assertEquals("1 pending invite uses this role. Give them another role first.", firstError(invited));
        PendingInviteCodesDao.instance.getMCollection().drop();

        // so do SSO group mappings
        SAMLConfig saml = new SAMLConfig(ConfigType.AZURE, ACCOUNT_ID);
        saml.setGroupRoleMapping(Collections.singletonMap("g-invited", "INVITED_ROLE"));
        SSOConfigsDao.instance.insertOne(saml);
        RoleAction mapped = roleAction(ADMIN, "INVITED_ROLE");
        assertEquals("ERROR", mapped.deleteCustomRole());
        assertTrue(firstError(mapped).contains("group mapping uses this role"));
        SSOConfigsDao.instance.getMCollection().drop();

        // a user of another account with a role of the same name does not count
        RBACDao.instance.insertOne(new RBAC(50, null, 999, Collections.singletonMap("API", "INVITED_ROLE")));
        assertEquals("SUCCESS", roleAction(ADMIN, "INVITED_ROLE").deleteCustomRole());
        assertNull(CustomRoleDao.instance.findRoleByName("INVITED_ROLE"));
    }

    @Test
    public void testRolesListShowsUsageAndBaseDefaults() {
        RoleAction action = new RoleAction();
        action.setSession(session(ADMIN));
        assertEquals("SUCCESS", action.getCustomRoles());
        assertEquals(Integer.valueOf(1), action.getRoleUsage().get("TEAM_A_USER").get("users"));
        assertEquals("READ", action.getBaseRolePermissions().get("MEMBER").get("INTEGRATIONS"));
        assertEquals("READ_WRITE", action.getBaseRolePermissions().get("ADMIN").get("INTEGRATIONS"));
    }

    @Test
    public void testRoleNameMessages() {
        RoleAction builtIn = roleAction(ADMIN, "admin");
        assertEquals("ERROR", builtIn.createCustomRole());
        assertEquals("admin is a built-in role name. Pick another name.", firstError(builtIn));
        RoleAction duplicate = roleAction(ADMIN, "team_a_user");
        assertEquals("ERROR", duplicate.createCustomRole());
        assertEquals("A role named TEAM_A_USER already exists.", firstError(duplicate));
        RoleAction noBase = roleAction(ADMIN, "NEW_ROLE");
        noBase.setBaseRole("NO_ACCESS");
        assertEquals("ERROR", noBase.createCustomRole());
        assertEquals("Pick a base role.", firstError(noBase));
    }

    // ── Roles that no longer exist ────────────────────────────────────────────

    @Test
    public void testDeletedRoleGivesNoAccess() {
        TestRoleAssignment.insertUser(SECOND_ADMIN, "DELETED_ROLE");
        clearCaches();
        assertEquals(Role.NO_ACCESS, RBACDao.getCurrentRoleForUser(SECOND_ADMIN, ACCOUNT_ID));
        assertTrue(RBACDao.hasMissingRole(SECOND_ADMIN, ACCOUNT_ID));
        assertFalse(RBACDao.hasMissingRole(MEMBER, ACCOUNT_ID));
        assertEquals(Collections.singletonList(RBACDao.NO_COLLECTION_ID), RBACDao.instance.getUserCollectionsById(SECOND_ADMIN, ACCOUNT_ID));
    }

    @Test
    public void testStoredRoleNamesInAnyCase() {
        assertEquals(Role.ADMIN, Role.fromName("admin"));
        assertEquals(Role.MEMBER, Role.fromName("Security Engineer"));
        assertEquals(Role.MEMBER, Role.fromName(" MEMBER "));
        assertNull(Role.fromName("TEAM_A_USER"));
    }

    // ── SSO ───────────────────────────────────────────────────────────────────

    @Test
    public void testSsoRoleDoesNotDependOnGroupOrder() {
        TestRoleAssignment.insertRole("ALL_SEC", "MEMBER", new ArrayList<>(), null);
        Map<String, String> mapping = new HashMap<>();
        mapping.put("team-a", "TEAM_A_USER");    // security engineer, limited to collections
        mapping.put("all-sec", "ALL_SEC");       // security engineer, all collections
        mapping.put("devs", "DEVELOPER");
        assertEquals("ALL_SEC", SsoRoleMapping.highestPriorityRole(mapping, Arrays.asList("team-a", "all-sec", "devs")));
        assertEquals("ALL_SEC", SsoRoleMapping.highestPriorityRole(mapping, Arrays.asList("devs", "all-sec", "team-a")));
        assertEquals("TEAM_A_USER", SsoRoleMapping.highestPriorityRole(mapping, Arrays.asList("devs", "team-a")));
    }

    @Test
    public void testSsoMappingFromOlderPageKeepsRemovalSetting() {
        SAMLConfig saml = new SAMLConfig(ConfigType.AZURE, ACCOUNT_ID);
        saml.setGroupRoleMapping(Collections.singletonMap("g-a", "MEMBER"));
        saml.setRemoveAccessWithoutGroup(true);
        SSOConfigsDao.instance.insertOne(saml);

        AzureSsoAction older = new AzureSsoAction();
        older.setSession(session(ADMIN));
        older.setConfigType(ConfigType.AZURE);
        older.setGroupRoleMapping(Collections.singletonMap("g-b", "MEMBER"));
        assertEquals("SUCCESS", older.saveSamlGroupRoleMapping()); // removeAccessWithoutGroup not sent
        assertTrue(SSOConfigsDao.getSAMLConfigByAccountId(ACCOUNT_ID).isRemoveAccessWithoutGroup());

        AzureSsoAction noMapping = new AzureSsoAction();
        noMapping.setSession(session(ADMIN));
        noMapping.setConfigType(ConfigType.AZURE);
        noMapping.setGroupRoleMapping(new HashMap<>());
        noMapping.setRemoveAccessWithoutGroup(true);
        assertEquals("ERROR", noMapping.saveSamlGroupRoleMapping()); // would remove everyone's access
    }

    @Test
    public void testOlderInviteKeepsItsRole() throws Exception {
        Method method = com.akto.action.SignupAction.class.getDeclaredMethod("inviteRoleOrMember", PendingInviteCode.class);
        method.setAccessible(true);
        assertEquals("DEVELOPER", method.invoke(null, new PendingInviteCode("c", ADMIN, "x@example.com", 0, ACCOUNT_ID, "DEVELOPER")));
        PendingInviteCode noRole = new PendingInviteCode("c", ADMIN, "x@example.com", 0, ACCOUNT_ID);
        noRole.setInviteeRole(null);
        assertEquals("MEMBER", method.invoke(null, noRole));
    }
}
