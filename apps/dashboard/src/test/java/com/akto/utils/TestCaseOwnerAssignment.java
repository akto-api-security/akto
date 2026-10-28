package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.akto.action.TeamAction;
import com.akto.action.threat_detection.SuspectSampleDataAction;
import com.akto.dao.RBACDao;
import com.akto.dao.UsersDao;
import com.akto.dto.RBAC;
import com.akto.dto.User;
import com.akto.dto.UserAccountEntry;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;

/*
 * Who can assign a guardrail case and who it can be assigned to: only Admin and Threat Engineer base roles
 * (custom roles count as the role they are based on), where roles are enforced. The role is the one in the
 * dashboard being viewed: an Argus (AGENTIC) admin is not an Atlas (ENDPOINT) admin. Uses the Argus test users:
 * 102 Admin, 103 Security Engineer (MEMBER), 104 Threat Engineer, 101 custom role on Threat Engineer,
 * 105 custom role on Security Engineer, plus users with roles in one product or different ones in each.
 */
public class TestCaseOwnerAssignment extends ArgusScopeTestBase {

    private static final int ARGUS_ADMIN = 106, ATLAS_ADMIN = 107, ARGUS_ADMIN_ATLAS_MEMBER = 108;
    private static final CONTEXT_SOURCE ARGUS = CONTEXT_SOURCE.AGENTIC, ATLAS = CONTEXT_SOURCE.ENDPOINT;

    private static final String NOT_ALLOWED = "Only Admin and Threat Engineer roles can assign a case";
    private static final String OWNER_ROLE = "The owner must have the Admin or Threat Engineer role";
    private static final String NOT_A_MEMBER = "The owner must be a member of this account";

    private static String login(int userId) {
        return user(userId).getLogin();
    }

    // a user with a role in Argus, Atlas, or both (none: no role in that product)
    private static void insertScopedUser(int userId, String argusRole, String atlasRole) {
        Map<String, String> scopeRoleMapping = new HashMap<>();
        if (argusRole != null) scopeRoleMapping.put(ARGUS.name(), argusRole);
        if (atlasRole != null) scopeRoleMapping.put(ATLAS.name(), atlasRole);
        RBACDao.instance.insertOne(new RBAC(userId, null, ACCOUNT_ID, scopeRoleMapping));
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userId, ACCOUNT_ID));
    }

    // the Argus users are only roles; make them members of the account so they can be picked as owners
    @Before
    public void makeUsersAccountMembers() {
        insertScopedUser(ARGUS_ADMIN, "ADMIN", null);
        insertScopedUser(ATLAS_ADMIN, null, "ADMIN");
        insertScopedUser(ARGUS_ADMIN_ATLAS_MEMBER, "ADMIN", "MEMBER");
        UsersDao.instance.getMCollection().drop();
        for (int id : new int[] {TEAM_A, ADMIN, MEMBER, THREAT_ENGINEER_ALL, TEAM_A_MEMBER,
                ARGUS_ADMIN, ATLAS_ADMIN, ARGUS_ADMIN_ATLAS_MEMBER}) {
            Map<String, UserAccountEntry> accounts = new HashMap<>();
            accounts.put(String.valueOf(ACCOUNT_ID), new UserAccountEntry(ACCOUNT_ID, "test"));
            User user = user(id);
            user.setName("User " + id);
            user.setAccounts(accounts);
            UsersDao.instance.insertOne(user);
        }
    }

    private static String ownerError(int callerId, String owner) throws Exception {
        return ownerError(callerId, owner, ARGUS);
    }

    private static String ownerError(int callerId, String owner, CONTEXT_SOURCE dashboard) throws Exception {
        as(callerId, dashboard);
        SuspectSampleDataAction action = new SuspectSampleDataAction();
        action.setSession(session(callerId));
        action.setCaseOwner(owner);
        Method check = SuspectSampleDataAction.class.getDeclaredMethod("caseOwnerError");
        check.setAccessible(true);
        return (String) check.invoke(action);
    }

    @Test
    public void testAdminAndThreatEngineerCanAssignToEachOther() throws Exception {
        assertNull(ownerError(ADMIN, login(THREAT_ENGINEER_ALL)));
        assertNull(ownerError(THREAT_ENGINEER_ALL, login(ADMIN)));
        assertNull(ownerError(ADMIN, login(ADMIN)));
    }

    @Test
    public void testCustomRolesCountAsTheirBaseRole() throws Exception {
        assertNull(ownerError(TEAM_A, login(ADMIN)));
        assertNull(ownerError(ADMIN, login(TEAM_A)));
        assertEquals(OWNER_ROLE, ownerError(ADMIN, login(TEAM_A_MEMBER)));
        assertEquals(NOT_ALLOWED, ownerError(TEAM_A_MEMBER, login(ADMIN)));
    }

    @Test
    public void testOtherRolesCannotAssign() throws Exception {
        assertEquals(NOT_ALLOWED, ownerError(MEMBER, login(ADMIN)));
        assertEquals(NOT_ALLOWED, ownerError(MEMBER, login(MEMBER)));
        // clearing the owner is a change of owner too
        assertEquals(NOT_ALLOWED, ownerError(MEMBER, ""));
    }

    @Test
    public void testOwnerMustHaveAnAllowedRole() throws Exception {
        assertEquals(OWNER_ROLE, ownerError(ADMIN, login(MEMBER)));
        assertEquals(OWNER_ROLE, ownerError(THREAT_ENGINEER_ALL, login(MEMBER)));
    }

    @Test
    public void testOwnerMustBeAMember() throws Exception {
        assertEquals(NOT_A_MEMBER, ownerError(ADMIN, "nobody@example.com"));
    }

    @Test
    public void testUnassigningAndNotTouchingTheOwner() throws Exception {
        assertNull(ownerError(ADMIN, ""));
        assertNull(ownerError(ADMIN, "   "));
        // an update that does not carry an owner (notes, status) needs no role beyond the update access itself
        assertNull(ownerError(MEMBER, null));
        assertNull(ownerError(TEAM_A_MEMBER, null));
    }

    @Test
    public void testLoginMatchIgnoresCaseAndSpaces() throws Exception {
        assertNull(ownerError(ADMIN, "  " + login(THREAT_ENGINEER_ALL).toUpperCase() + " "));
        assertEquals(OWNER_ROLE, ownerError(ADMIN, " " + login(MEMBER).toUpperCase()));
    }

    @Test
    public void testRolesAreNotEnforcedWhereRbacIsNot() throws Exception {
        // not a metered dashboard (e.g. local): every member can assign, as for every other role check
        setDashboardMode(null);
        assertNull(ownerError(MEMBER, login(MEMBER)));
        assertNull(ownerError(MEMBER, ""));
        // still only to a member of the account
        assertEquals(NOT_A_MEMBER, ownerError(MEMBER, "nobody@example.com"));
    }

    private static Map<String, String> baseRoles(CONTEXT_SOURCE dashboard) {
        as(ADMIN, dashboard);
        TeamAction action = new TeamAction();
        action.setSession(session(ADMIN));
        action.fetchTeamData();
        Map<String, String> baseRoles = new HashMap<>();
        for (Object member : action.getUsers()) {
            BasicDBObject user = (BasicDBObject) member;
            baseRoles.put(user.getString("login"), user.getString("baseRole"));
        }
        return baseRoles;
    }

    @Test
    public void testTeamDataCarriesTheBaseRoleInTheCurrentProduct() {
        Map<String, String> baseRoles = baseRoles(ARGUS);
        assertEquals("ADMIN", baseRoles.get(login(ADMIN)));
        assertEquals("MEMBER", baseRoles.get(login(MEMBER)));
        assertEquals("THREAT_ENGINEER", baseRoles.get(login(THREAT_ENGINEER_ALL)));
        assertEquals("THREAT_ENGINEER", baseRoles.get(login(TEAM_A)));
        assertEquals("MEMBER", baseRoles.get(login(TEAM_A_MEMBER)));
    }

    @Test
    public void testTeamDataBaseRoleFollowsTheDashboard() {
        Map<String, String> inArgus = baseRoles(ARGUS);
        assertEquals("ADMIN", inArgus.get(login(ARGUS_ADMIN)));
        assertEquals("NO_ACCESS", inArgus.get(login(ATLAS_ADMIN)));
        assertEquals("ADMIN", inArgus.get(login(ARGUS_ADMIN_ATLAS_MEMBER)));

        Map<String, String> inAtlas = baseRoles(ATLAS);
        assertEquals("NO_ACCESS", inAtlas.get(login(ARGUS_ADMIN)));
        assertEquals("ADMIN", inAtlas.get(login(ATLAS_ADMIN)));
        assertEquals("MEMBER", inAtlas.get(login(ARGUS_ADMIN_ATLAS_MEMBER)));
        // the other Argus users have no Atlas role either
        assertEquals("NO_ACCESS", inAtlas.get(login(ADMIN)));
        assertEquals("NO_ACCESS", inAtlas.get(login(THREAT_ENGINEER_ALL)));
    }

    @Test
    public void testAnArgusAdminIsNotAnAtlasAdmin() throws Exception {
        assertNull(ownerError(ARGUS_ADMIN, login(ARGUS_ADMIN), ARGUS));
        assertEquals(NOT_ALLOWED, ownerError(ARGUS_ADMIN, login(ARGUS_ADMIN), ATLAS));
        assertNull(ownerError(ATLAS_ADMIN, login(ATLAS_ADMIN), ATLAS));
        assertEquals(NOT_ALLOWED, ownerError(ATLAS_ADMIN, login(ATLAS_ADMIN), ARGUS));
    }

    @Test
    public void testAdminInOneDashboardOnlyAssignsThere() throws Exception {
        assertNull(ownerError(ARGUS_ADMIN_ATLAS_MEMBER, login(ARGUS_ADMIN), ARGUS));
        assertEquals(NOT_ALLOWED, ownerError(ARGUS_ADMIN_ATLAS_MEMBER, login(ATLAS_ADMIN), ATLAS));
    }

    @Test
    public void testOwnerIsJudgedByTheirRoleInThatDashboard() throws Exception {
        // Atlas: the Atlas admin is a valid owner; an Argus-only admin and an Atlas member are not
        assertNull(ownerError(ATLAS_ADMIN, login(ATLAS_ADMIN), ATLAS));
        assertEquals(OWNER_ROLE, ownerError(ATLAS_ADMIN, login(ARGUS_ADMIN), ATLAS));
        assertEquals(OWNER_ROLE, ownerError(ATLAS_ADMIN, login(ARGUS_ADMIN_ATLAS_MEMBER), ATLAS));
        // Argus: the reverse
        assertNull(ownerError(ARGUS_ADMIN, login(ARGUS_ADMIN_ATLAS_MEMBER), ARGUS));
        assertEquals(OWNER_ROLE, ownerError(ARGUS_ADMIN, login(ATLAS_ADMIN), ARGUS));
    }
}
