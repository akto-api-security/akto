package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.action.RoleAction;
import com.akto.action.TeamAction;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;

public class TestRoleAssignment extends MongoBasedTest {

    static final int ADMIN = 1, MEMBER = 2, GUEST = 3, TEAM_ADMIN = 4, TEAM_USER = 5, THREAT_ENGINEER = 6, SCOPED_NO_LIST = 7;

    static User user(int id) {
        User user = new User();
        user.setId(id);
        user.setLogin("user" + id + "@example.com");
        Map<String, UserAccountEntry> accounts = new HashMap<>();
        accounts.put(ACCOUNT_ID + "", new UserAccountEntry(ACCOUNT_ID, "account"));
        user.setAccounts(accounts);
        return user;
    }

    static void insertUser(int id, String role) {
        Map<String, String> scopeRoleMapping = new HashMap<>();
        scopeRoleMapping.put(CONTEXT_SOURCE.API.name(), role);
        RBACDao.instance.insertOne(new RBAC(id, null, ACCOUNT_ID, scopeRoleMapping));
        UsersDao.instance.insertOne(user(id));
    }

    static void insertRole(String name, String baseRole, List<Integer> collections, List<String> assignable) {
        CustomRole role = new CustomRole(name, baseRole, collections, false, false, new ArrayList<>());
        role.setAssignableRoles(assignable);
        CustomRoleDao.instance.insertOne(role);
    }

    static void clearCaches() {
        CustomRoleDao.clearRoleCache();
        for (int id = 1; id <= 7; id++) {
            RBACDao.instance.deleteUserEntryFromCache(new Pair<>(id, ACCOUNT_ID));
            UsersCollectionsList.deleteCollectionIdsFromCache(id, ACCOUNT_ID);
        }
    }

    @Before
    public void setup() {
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        RBACDao.instance.getMCollection().drop();
        UsersDao.instance.getMCollection().drop();
        CustomRoleDao.instance.getMCollection().drop();
        insertRole("TEAM_A_USER", "MEMBER", Arrays.asList(11, 12), null);
        insertRole("TEAM_A_ADMIN", "THREAT_ENGINEER", Arrays.asList(11, 12), Collections.singletonList("TEAM_A_USER"));
        insertRole("TEAM_A_NO_LIST", "THREAT_ENGINEER", Arrays.asList(11, 12), null);
        insertUser(ADMIN, "ADMIN");
        insertUser(MEMBER, "MEMBER");
        insertUser(GUEST, "GUEST");
        insertUser(TEAM_ADMIN, "TEAM_A_ADMIN");
        insertUser(TEAM_USER, "TEAM_A_USER");
        insertUser(THREAT_ENGINEER, "THREAT_ENGINEER");
        insertUser(SCOPED_NO_LIST, "TEAM_A_NO_LIST");
        clearCaches();
    }

    static RBAC rbac(int id) {
        return RBACDao.getCurrentRBACForUser(id, ACCOUNT_ID);
    }

    @Test
    public void testUnlimitedCallersFollowTheHierarchy() {
        assertTrue(RoleAssignment.canAssign(ADMIN, ACCOUNT_ID, "ADMIN"));
        assertTrue(RoleAssignment.canAssign(ADMIN, ACCOUNT_ID, "TEAM_A_ADMIN"));
        assertTrue(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "GUEST"));
        assertTrue(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "TEAM_A_USER")); // custom role on a base in the hierarchy
        assertFalse(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "ADMIN"));
        assertFalse(RoleAssignment.canAssign(MEMBER, ACCOUNT_ID, "THREAT_ENGINEER"));
        assertFalse(RoleAssignment.canManage(MEMBER, ACCOUNT_ID, rbac(ADMIN)));
        assertTrue(RoleAssignment.canManage(MEMBER, ACCOUNT_ID, rbac(GUEST)));
        assertTrue(RoleAssignment.canAssign(GUEST, ACCOUNT_ID, "NO_ACCESS"));
    }

    @Test
    public void testTeamAdminOnlyGivesItsRoles() {
        assertEquals(Collections.singleton("TEAM_A_USER"), RoleAssignment.limitedAssignableRoles(TEAM_ADMIN, ACCOUNT_ID));
        assertTrue(RoleAssignment.canAssign(TEAM_ADMIN, ACCOUNT_ID, "TEAM_A_USER"));
        assertTrue(RoleAssignment.canAssign(TEAM_ADMIN, ACCOUNT_ID, "NO_ACCESS"));
        // built-in roles see every collection, so a team admin can never give them
        assertFalse(RoleAssignment.canAssign(TEAM_ADMIN, ACCOUNT_ID, "THREAT_ENGINEER"));
        assertFalse(RoleAssignment.canAssign(TEAM_ADMIN, ACCOUNT_ID, "GUEST"));
        assertFalse(RoleAssignment.canAssign(TEAM_ADMIN, ACCOUNT_ID, "TEAM_A_ADMIN"));
        assertTrue(RoleAssignment.canManage(TEAM_ADMIN, ACCOUNT_ID, rbac(TEAM_USER)));
        assertFalse(RoleAssignment.canManage(TEAM_ADMIN, ACCOUNT_ID, rbac(THREAT_ENGINEER)));
        assertFalse(RoleAssignment.canManage(TEAM_ADMIN, ACCOUNT_ID, rbac(ADMIN)));

        // a scoped role without a list gives nothing (previously it could give any role in its hierarchy)
        assertTrue(RoleAssignment.limitedAssignableRoles(SCOPED_NO_LIST, ACCOUNT_ID).isEmpty());
        assertFalse(RoleAssignment.canAssign(SCOPED_NO_LIST, ACCOUNT_ID, "THREAT_ENGINEER"));
        assertEquals(null, RoleAssignment.limitedAssignableRoles(THREAT_ENGINEER, ACCOUNT_ID));
    }

    static String updateRole(int caller, int target, String role) {
        TeamAction action = new TeamAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", user(caller));
        action.setSession(session);
        action.setEmail("user" + target + "@example.com");
        Map<String, String> scopeRoleMapping = new HashMap<>();
        scopeRoleMapping.put(CONTEXT_SOURCE.API.name(), role);
        action.setScopeRoleMapping(scopeRoleMapping);
        String result = action.updateUserScopeRoleMapping();
        clearCaches();
        return result;
    }

    static String storedRole(int id) {
        return RBACDao.instance.findOne(Filters.and(Filters.eq(RBAC.USER_ID, id), Filters.eq(RBAC.ACCOUNT_ID, ACCOUNT_ID)))
                .getScopeRoleMapping().get(CONTEXT_SOURCE.API.name());
    }

    @Test
    public void testRoleChangesThroughTheApi() {
        assertEquals("ERROR", updateRole(GUEST, GUEST, "ADMIN"));             // never yourself
        assertEquals("ERROR", updateRole(MEMBER, GUEST, "ADMIN"));            // above your own role
        assertEquals("SUCCESS", updateRole(MEMBER, GUEST, "MEMBER"));         // hierarchy still works
        assertEquals("ERROR", updateRole(TEAM_ADMIN, TEAM_USER, "THREAT_ENGINEER")); // team admin cannot widen access
        assertEquals("TEAM_A_USER", storedRole(TEAM_USER));
        assertEquals("SUCCESS", updateRole(TEAM_ADMIN, TEAM_USER, "NO_ACCESS")); // can take its users' access away
        assertEquals("SUCCESS", updateRole(TEAM_ADMIN, TEAM_USER, "TEAM_A_USER"));
        assertEquals("ERROR", updateRole(TEAM_ADMIN, THREAT_ENGINEER, "TEAM_A_USER")); // not one of its users
        assertEquals("THREAT_ENGINEER", storedRole(THREAT_ENGINEER));
        assertEquals("SUCCESS", updateRole(ADMIN, THREAT_ENGINEER, "TEAM_A_ADMIN"));
    }

    @Test
    public void testRoleHierarchyApiForTeamAdmins() {
        TeamAction action = new TeamAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", user(TEAM_ADMIN));
        action.setSession(session);
        assertEquals("SUCCESS", action.getRoleHierarchy());
        assertEquals(Collections.singletonList("TEAM_A_USER"), action.getUserRoleHierarchy());

        action = new TeamAction();
        session = new HashMap<>();
        session.put("user", user(MEMBER));
        action.setSession(session);
        assertEquals("SUCCESS", action.getRoleHierarchy());
        assertEquals(Arrays.asList("MEMBER", "DEVELOPER", "GUEST"), action.getUserRoleHierarchy());
    }

    static RoleAction roleAction(String name, List<String> assignable) {
        RoleAction action = new RoleAction();
        Map<String, Object> session = new HashMap<>();
        session.put("user", user(ADMIN));
        action.setSession(session);
        action.setRoleName(name);
        action.setBaseRole("THREAT_ENGINEER");
        action.setApiCollectionIds(Arrays.asList(11));
        action.setAssignableRoles(assignable);
        return action;
    }

    @Test
    public void testAssignableRolesValidation() {
        insertRole("UNSCOPED_ROLE", "MEMBER", new ArrayList<>(), null);
        insertRole("SCOPED_ADMIN_ROLE", "ADMIN", Arrays.asList(11), null);
        assertEquals("ERROR", roleAction("TEAM_B_ADMIN", Collections.singletonList("UNSCOPED_ROLE")).createCustomRole());
        assertEquals("ERROR", roleAction("TEAM_B_ADMIN", Collections.singletonList("SCOPED_ADMIN_ROLE")).createCustomRole());
        assertEquals("ERROR", roleAction("TEAM_B_ADMIN", Collections.singletonList("MISSING_ROLE")).createCustomRole());
        assertEquals("SUCCESS", roleAction("TEAM_B_ADMIN", Collections.singletonList("TEAM_A_USER")).createCustomRole());
        assertEquals(Collections.singletonList("TEAM_A_USER"), CustomRoleDao.instance.findRoleByName("TEAM_B_ADMIN").getAssignableRoles());
    }
}
