package com.akto.interceptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.akto.MongoBasedTest;
import com.akto.action.RoleAction;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.RBACDao;
import com.akto.dao.context.Context;
import com.akto.dto.CustomRole;
import com.akto.dto.RBAC;
import com.akto.dto.RBAC.Role;
import com.akto.dto.User;
import com.akto.dto.rbac.RbacEnums;
import com.akto.dto.rbac.RbacEnums.Feature;
import com.akto.dto.rbac.RbacEnums.ReadWriteAccess;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

public class TestRbacGuards extends MongoBasedTest {

    /*
     * Actions that run without the role check: login / signup / SSO callbacks, health and metrics,
     * public tools, and a few reads not reviewed yet. Adding a new action here needs a reason.
     */
    private static final Set<String> ACTIONS_WITHOUT_ROLE_CHECK = new HashSet<>(Arrays.asList(
            "home", "verify-email", "metrics", "detailedMetrics", "mongo-error", "api/inventory/*/openapi", "api/health",
            "auth/login", "dashboard/accessToken", "api/me", "api/saveSubscription", "validate", "api/createNewTeam",
            "signup-email", "signup-google", "signup-github", "authorization-code/callback", "okta-initiate-login",
            "signup-azure-saml", "trigger-saml-sso", "callback-google-saml", "callback-jumpcloud-saml", "middleware/config",
            "api/deleteApisBasedOnHeader", "api/logout", "auth0-logout", "api/fetchAccountConfig", "tools/publicApi",
            "tools/fetchAllSubCategories", "api/fetchVulnerableRequests", "tools/fetchVulnerableRequests", "tools/fetchSampleData",
            "api/fetchParamsStatus", "api/fetchRemediationInfo", "api/fetchActiveTestRunsStatus", "copilot/oauth/callback",
            "api/fetchRuntimeInstances", "api/fetchRuntimeMetrics", "tools/runTestForGivenTemplate",
            "api/fetchTestingRunPlaygroundStatus", "tools/createSampleDataJson", "callback", "tools/addLLmData",
            "tools/convertSamleDataToBurpRequest", "tools/convertSampleDataToCurl", "api/downloadReportPDF",
            "auth/sendPasswordResetLink", "auth/resetPassword", "api/findSvcToSvcGraphEdges", "api/findSvcToSvcGraphNodes",
            "api/downloadSamplePdf", "api/wrapped"
    ));

    private static Map<String, String> roleCheckParams(Element action) {
        NodeList refs = action.getElementsByTagName("interceptor-ref");
        for (int i = 0; i < refs.getLength(); i++) {
            Element ref = (Element) refs.item(i);
            if (!"roleAccessInterceptor".equals(ref.getAttribute("name"))) continue;
            Map<String, String> params = new HashMap<>();
            NodeList paramNodes = ref.getElementsByTagName("param");
            for (int j = 0; j < paramNodes.getLength(); j++) {
                Element param = (Element) paramNodes.item(j);
                params.put(param.getAttribute("name"), param.getTextContent().trim());
            }
            return params;
        }
        return null;
    }

    @Test
    public void testEveryActionHasAValidRoleCheck() throws Exception {
        Document struts = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File("src/main/resources/struts.xml"));
        NodeList actions = struts.getElementsByTagName("action");
        Set<String> features = new HashSet<>();
        for (RbacEnums.Feature feature : RbacEnums.Feature.values()) features.add(feature.name());
        List<String> problems = new ArrayList<>();

        for (int i = 0; i < actions.getLength(); i++) {
            Element action = (Element) actions.item(i);
            String name = action.getAttribute("name");
            Map<String, String> params = roleCheckParams(action);
            if (params == null) {
                if (!ACTIONS_WITHOUT_ROLE_CHECK.contains(name)) problems.add(name + ": no role check");
                continue;
            }
            String label = params.get("featureLabel");
            if (!features.contains(label)) problems.add(name + ": unknown featureLabel " + label);
            String accessType = params.get("accessType");
            if (!"READ".equals(accessType) && !"READ_WRITE".equals(accessType)) problems.add(name + ": bad accessType " + accessType);
            String collectionScope = params.get("collectionScope");
            if (collectionScope != null) {
                try {
                    RbacEnums.CollectionScope.valueOf(collectionScope);
                } catch (IllegalArgumentException e) {
                    problems.add(name + ": bad collectionScope " + collectionScope);
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    @Test
    public void testRoleFromName() {
        assertEquals(Role.MEMBER, Role.fromName("MEMBER"));
        assertEquals(Role.MEMBER, Role.fromName("SECURITY ENGINEER"));
        assertEquals(Role.THREAT_ENGINEER, Role.fromName("THREAT ENGINEER"));
        assertEquals(Role.NO_ACCESS, Role.fromName("NO_ACCESS"));
        assertNull(Role.fromName("SOME_DELETED_ROLE"));
        assertNull(Role.fromName(null));
    }

    private static Role storedRole(int userId, String role) {
        Map<String, String> scopeRoleMapping = new HashMap<>();
        scopeRoleMapping.put(CONTEXT_SOURCE.API.name(), role);
        RBACDao.instance.insertOne(new RBAC(userId, null, ACCOUNT_ID, scopeRoleMapping));
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userId, ACCOUNT_ID));
        return RBACDao.getCurrentRoleForUser(userId, ACCOUNT_ID);
    }

    @Test
    public void testStoredRoleResolution() {
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        RBACDao.instance.getMCollection().drop();
        CustomRoleDao.instance.getMCollection().drop();
        CustomRoleDao.instance.insertOne(new CustomRole("TEAM_ROLE", Role.THREAT_ENGINEER.name(), new ArrayList<>(), false, false, new ArrayList<>()));

        assertEquals(Role.ADMIN, storedRole(201, "ADMIN"));
        assertEquals(Role.MEMBER, storedRole(202, "SECURITY ENGINEER")); // display name stored by older signups
        assertEquals(Role.THREAT_ENGINEER, storedRole(203, "TEAM_ROLE"));
        assertEquals(Role.GUEST, storedRole(204, "SOME_DELETED_ROLE")); // least privilege, not an exception
    }

    private static void insertRole(String name, Role baseRole, boolean threatToggle, Map<String, String> overrides) {
        CustomRole role = new CustomRole(name, baseRole.name(), new ArrayList<>(), false, threatToggle, new ArrayList<>());
        role.setPermissionOverrides(overrides);
        CustomRoleDao.instance.insertOne(role);
        CustomRoleDao.clearRoleCache();
    }

    private static ReadWriteAccess access(int userId, Feature feature) {
        Role role = RBACDao.getCurrentRoleForUser(userId, ACCOUNT_ID);
        return RBACDao.resolveFeatureAccess(userId, ACCOUNT_ID, feature, role.getReadWriteAccessForFeature(feature));
    }

    @Test
    public void testPermissionOverrides() {
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.API);
        RBACDao.instance.getMCollection().drop();
        CustomRoleDao.instance.getMCollection().drop();

        Map<String, String> runtimeAdmin = new HashMap<>();
        runtimeAdmin.put(Feature.THREAT_SETTINGS.name(), ReadWriteAccess.NO_ACCESS.name());
        runtimeAdmin.put(Feature.INVITE_MEMBERS.name(), ReadWriteAccess.READ.name());
        runtimeAdmin.put(Feature.ADMIN_ACTIONS.name(), ReadWriteAccess.READ_WRITE.name()); // never applied
        insertRole("RUNTIME_ADMIN", Role.THREAT_ENGINEER, false, runtimeAdmin);
        insertRole("MEMBER_WITH_THREAT", Role.MEMBER, true, null);
        insertRole("PLAIN_MEMBER", Role.MEMBER, false, null);

        storedRole(301, "RUNTIME_ADMIN");
        assertEquals(ReadWriteAccess.READ_WRITE, access(301, Feature.THREAT_PROTECTION));
        assertEquals(ReadWriteAccess.NO_ACCESS, access(301, Feature.THREAT_SETTINGS));
        assertEquals(ReadWriteAccess.READ, access(301, Feature.INVITE_MEMBERS));
        assertEquals(ReadWriteAccess.READ, access(301, Feature.ADMIN_ACTIONS));

        // threat settings follow threat protection, including the existing toggle, so nothing changes by default
        storedRole(302, "MEMBER_WITH_THREAT");
        assertEquals(ReadWriteAccess.READ_WRITE, access(302, Feature.THREAT_SETTINGS));
        storedRole(303, "PLAIN_MEMBER");
        assertEquals(ReadWriteAccess.NO_ACCESS, access(303, Feature.THREAT_SETTINGS));
        storedRole(304, "THREAT_ENGINEER");
        assertEquals(ReadWriteAccess.READ_WRITE, access(304, Feature.THREAT_SETTINGS));

        for (Role role : Role.values()) {
            assertEquals(role.getReadWriteAccessForFeature(Feature.THREAT_PROTECTION), role.getReadWriteAccessForFeature(Feature.THREAT_SETTINGS));
        }
    }

    private static RoleAction roleAction(String name, Map<String, String> overrides) {
        RoleAction action = new RoleAction();
        Map<String, Object> session = new HashMap<>();
        User admin = new User();
        admin.setId(1);
        session.put("user", admin);
        action.setSession(session);
        action.setRoleName(name);
        action.setBaseRole(Role.MEMBER.name());
        action.setApiCollectionIds(new ArrayList<>());
        action.setPermissionOverrides(overrides);
        return action;
    }

    @Test
    public void testRoleActionValidation() {
        Context.accountId.set(ACCOUNT_ID);
        CustomRoleDao.instance.getMCollection().drop();
        assertEquals("ERROR", roleAction("admin", null).createCustomRole()); // reserved name in any case

        Map<String, String> bad = new HashMap<>();
        bad.put(Feature.ADMIN_ACTIONS.name(), ReadWriteAccess.READ_WRITE.name());
        assertEquals("ERROR", roleAction("TEAM_X", bad).createCustomRole());
        bad.clear();
        bad.put("NOT_A_FEATURE", ReadWriteAccess.READ.name());
        assertEquals("ERROR", roleAction("TEAM_X", bad).createCustomRole());

        Map<String, String> good = new HashMap<>();
        good.put(Feature.INVITE_MEMBERS.name(), ReadWriteAccess.NO_ACCESS.name());
        assertEquals("SUCCESS", roleAction("TEAM_X", good).createCustomRole());
        assertEquals(good, CustomRoleDao.instance.findRoleByName("TEAM_X").getPermissionOverrides());
    }
}
