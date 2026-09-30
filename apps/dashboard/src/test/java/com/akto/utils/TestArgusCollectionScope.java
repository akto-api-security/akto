package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.action.AuditDataAction;
import com.akto.action.GuardrailPoliciesAction;
import com.akto.action.SignupAction;
import com.akto.action.user.AzureSsoAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.RBACDao;
import com.akto.dao.SSOConfigsDao;
import com.akto.dao.SetupDao;
import com.akto.dao.UsersDao;
import com.akto.dao.billing.OrganizationsDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.CustomRole;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.RBAC;
import com.akto.dto.Setup;
import com.akto.dto.User;
import com.akto.dto.billing.FeatureAccess;
import com.akto.dto.billing.Organization;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.sso.SAMLConfig;
import com.akto.dto.traffic.CollectionTags;
import com.akto.interceptor.RoleAccessInterceptor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

public class TestArgusCollectionScope extends ArgusScopeTestBase {


    // ── Who is limited ─────────────────────────────────────────────────────────

    @Test
    public void testOnlyCollectionLimitedArgusUsersAreLimited() {
        as(TEAM_A, CONTEXT_SOURCE.AGENTIC);
        assertTrue(ArgusCollectionScope.isLimited(user(TEAM_A)));
        assertEquals(new HashSet<>(Arrays.asList(1, 2)), new HashSet<>(ArgusCollectionScope.getRestrictedCollectionIds(user(TEAM_A))));
        assertEquals(new HashSet<>(Arrays.asList(OWN_HOST, OWN_CLAUDE_HOST)), ArgusCollectionScope.getRestrictedHosts(user(TEAM_A)));

        // not limited: admin, built-in roles without assigned collections
        for (int userId : new int[]{ADMIN, MEMBER, THREAT_ENGINEER_ALL}) {
            as(userId, CONTEXT_SOURCE.AGENTIC);
            assertFalse("user " + userId, ArgusCollectionScope.isLimited(user(userId)));
            assertNull(ArgusCollectionScope.getRestrictedHosts(user(userId)));
        }

        // not limited outside Argus (Atlas, API) or without a user/product
        for (CONTEXT_SOURCE other : new CONTEXT_SOURCE[]{CONTEXT_SOURCE.ENDPOINT, CONTEXT_SOURCE.API, CONTEXT_SOURCE.DAST}) {
            as(TEAM_A, other);
            assertFalse(other.name(), ArgusCollectionScope.isLimited(user(TEAM_A)));
        }
        as(TEAM_A, null);
        assertFalse(ArgusCollectionScope.isLimited(user(TEAM_A)));
        as(TEAM_A, CONTEXT_SOURCE.AGENTIC);
        assertFalse(ArgusCollectionScope.isLimited(null));
    }

    @Test
    public void testNothingEnforcedOnLocalDeploy() throws Exception {
        // self-hosted (not SaaS / on-prem): roles are not enforced, so nothing new applies
        setDashboardMode(null);
        as(TEAM_A, CONTEXT_SOURCE.AGENTIC);
        assertFalse(ArgusCollectionScope.isLimited(user(TEAM_A)));
        assertNull(policyAccessError(MEMBER, CONTEXT_SOURCE.AGENTIC, policy(true)));
        assertNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, policy(true)));
        setDashboardMode("SAAS");
    }

    @Test
    public void testCollectionsCachedPerProduct() {
        // Role only in Argus: the first request in API Security (NO_ACCESS there) must not decide what Argus shows
        final int argusOnlyUser = 106;
        Map<String, String> scopeRoleMapping = new HashMap<>();
        scopeRoleMapping.put(CONTEXT_SOURCE.AGENTIC.name(), "TEAM_A_ADMIN");
        RBACDao.instance.insertOne(new RBAC(argusOnlyUser, null, ACCOUNT_ID, scopeRoleMapping));
        HashMap<String, FeatureAccess> features = new HashMap<>();
        features.put(UsersCollectionsList.RBAC_FEATURE, new FeatureAccess(true));
        Organization org = new Organization("org-scope-test", "org", "admin@example.com", new HashSet<>(Collections.singletonList(ACCOUNT_ID)), false);
        org.setFeatureWiseAllowed(features);
        OrganizationsDao.instance.insertOne(org);
        try {
            UsersCollectionsList.deleteCollectionIdsFromCache(argusOnlyUser, ACCOUNT_ID);
            as(argusOnlyUser, CONTEXT_SOURCE.API);
            UsersCollectionsList.getCollectionsIdForUser(argusOnlyUser, ACCOUNT_ID);
            as(argusOnlyUser, CONTEXT_SOURCE.AGENTIC);
            assertEquals(new HashSet<>(Arrays.asList(1, 2)),
                new HashSet<>(UsersCollectionsList.getCollectionsIdForUser(argusOnlyUser, ACCOUNT_ID)));
        } finally {
            OrganizationsDao.instance.getMCollection().deleteMany(new org.bson.Document("_id", "org-scope-test"));
            UsersCollectionsList.deleteCollectionIdsFromCache(argusOnlyUser, ACCOUNT_ID);
        }
    }

    // ── Guardrail activity filter ──────────────────────────────────────────────

    @Test
    public void testActivityFiltersUnchangedForOthers() {
        as(ADMIN, CONTEXT_SOURCE.AGENTIC);
        Map<String, Object> filters = new HashMap<>();
        filters.put("hosts", Collections.singletonList(OTHER_HOST));
        filters.put("matchClaudeConfig", true);
        assertTrue(ArgusCollectionScope.scopeActivityFilters(user(ADMIN), filters));
        assertEquals(Collections.singletonList(OTHER_HOST), filters.get("hosts"));
        assertEquals(true, filters.get("matchClaudeConfig"));
        assertFalse(filters.containsKey("looseHostKeys"));
    }

    @Test
    public void testActivityFiltersForLimitedUser() {
        as(TEAM_A, CONTEXT_SOURCE.AGENTIC);

        // nothing requested -> all own hosts, loose keys and claude device ids
        Map<String, Object> filters = new HashMap<>();
        assertTrue(ArgusCollectionScope.scopeActivityFilters(user(TEAM_A), filters));
        assertEquals(new HashSet<>(Arrays.asList(OWN_HOST, OWN_CLAUDE_HOST)), new HashSet<>((List<?>) filters.get("hosts")));
        assertEquals(new HashSet<>(Arrays.asList("team-a-chatbot com", "laptop1 claude")), new HashSet<>((List<?>) filters.get("looseHostKeys")));
        assertEquals(Collections.singletonList("laptop1"), filters.get("claudeDeviceIds"));

        // another team's host -> nothing visible
        filters = new HashMap<>();
        filters.put("hosts", Collections.singletonList(OTHER_HOST));
        filters.put("looseHostKeys", Collections.singletonList("team-b-bot com"));
        assertFalse(ArgusCollectionScope.scopeActivityFilters(user(TEAM_A), filters));

        // own host requested -> only that host
        filters = new HashMap<>();
        filters.put("hosts", Arrays.asList(OWN_HOST, OTHER_HOST));
        assertTrue(ArgusCollectionScope.scopeActivityFilters(user(TEAM_A), filters));
        assertEquals(Collections.singletonList(OWN_HOST), filters.get("hosts"));
        assertTrue(((List<?>) filters.get("looseHostKeys")).isEmpty());
        assertTrue(((List<?>) filters.get("claudeDeviceIds")).isEmpty());

        // "all claude config" -> only own claude devices, never the account-wide match
        filters = new HashMap<>();
        filters.put("matchClaudeConfig", true);
        assertTrue(ArgusCollectionScope.scopeActivityFilters(user(TEAM_A), filters));
        assertFalse(filters.containsKey("matchClaudeConfig"));
        assertEquals(Collections.singletonList("laptop1"), filters.get("claudeDeviceIds"));
    }

    @Test
    public void testScopeValues() {
        assertEquals(Arrays.asList("a", "b"), ArgusCollectionScope.scopeValues(null, Arrays.asList("a", "b")));
        assertEquals(Collections.singletonList("a"), ArgusCollectionScope.scopeValues(Arrays.asList("a", "x"), Arrays.asList("a", "b")));
        assertTrue(ArgusCollectionScope.scopeValues(Collections.singletonList("x"), Arrays.asList("a", "b")).isEmpty());
    }

    // ── Guardrail policies ─────────────────────────────────────────────────────

    private static GuardrailPolicies policy(boolean applyToAll, String... targets) {
        GuardrailPolicies policy = new GuardrailPolicies();
        policy.setApplyToAllServers(applyToAll);
        List<GuardrailPolicies.SelectedServer> servers = new ArrayList<>();
        for (String target : targets) servers.add(new GuardrailPolicies.SelectedServer(target, target));
        policy.setSelectedAgentServersV2(servers);
        return policy;
    }

    private static String policyAccessError(int userId, CONTEXT_SOURCE contextSource, GuardrailPolicies... policies) throws Exception {
        as(userId, contextSource);
        GuardrailPoliciesAction action = new GuardrailPoliciesAction();
        action.setSession(session(userId));
        Method method = GuardrailPoliciesAction.class.getDeclaredMethod("validatePolicyAccess", Supplier.class);
        method.setAccessible(true);
        Supplier<List<GuardrailPolicies>> supplier = () -> Arrays.asList(policies);
        return (String) method.invoke(action, supplier);
    }

    @Test
    public void testPolicyRoleRule() throws Exception {
        // Argus: only Admin and Threat Engineer (and custom roles on them)
        assertNull(policyAccessError(ADMIN, CONTEXT_SOURCE.AGENTIC, policy(true)));
        assertNull(policyAccessError(THREAT_ENGINEER_ALL, CONTEXT_SOURCE.AGENTIC, policy(true)));
        assertNotNull(policyAccessError(MEMBER, CONTEXT_SOURCE.AGENTIC, policy(true)));
        assertNotNull(policyAccessError(TEAM_A_MEMBER, CONTEXT_SOURCE.AGENTIC, policy(false, OWN_HOST)));

        // Atlas, API: unchanged
        assertNull(policyAccessError(MEMBER, CONTEXT_SOURCE.ENDPOINT, policy(true)));
        assertNull(policyAccessError(MEMBER, CONTEXT_SOURCE.API, policy(true)));
    }

    @Test
    public void testPolicyCollectionRule() throws Exception {
        // own collection: by host name or by collection id
        assertNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, policy(false, OWN_HOST)));
        assertNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, policy(false, "1", OWN_CLAUDE_HOST)));

        // apply to all, another team's agent, no target, exclude mode, or replacing a global policy
        assertNotNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, policy(true)));
        assertNotNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, policy(false, OWN_HOST, OTHER_HOST)));
        assertNotNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, policy(false)));
        GuardrailPolicies excludeOwn = policy(false, OWN_HOST);
        excludeOwn.setNegatedAgentServers(true);
        assertNotNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, excludeOwn));
        assertNotNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, policy(false, OWN_HOST), policy(true)));
        GuardrailPolicies legacy = new GuardrailPolicies();
        legacy.setSelectedMcpServers(Collections.singletonList(OTHER_HOST));
        assertNotNull(policyAccessError(TEAM_A, CONTEXT_SOURCE.AGENTIC, legacy));

        // unlimited Threat Engineer and admin: no collection rule
        assertNull(policyAccessError(THREAT_ENGINEER_ALL, CONTEXT_SOURCE.AGENTIC, policy(false, OTHER_HOST)));
    }

    // ── Account-wide settings ──────────────────────────────────────────────────

    private static String collectionScopeError(String collectionScope, Object action, int userId) throws Exception {
        as(userId, CONTEXT_SOURCE.AGENTIC);
        RoleAccessInterceptor interceptor = new RoleAccessInterceptor();
        interceptor.setCollectionScope(collectionScope);
        Method method = RoleAccessInterceptor.class.getDeclaredMethod("checkCollectionScope", Object.class, User.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, action, user(userId));
    }

    @Test
    public void testAccountWideSettingActions() throws Exception {
        assertNotNull(collectionScopeError("ACCOUNT_WIDE", null, TEAM_A));
        assertNull(collectionScopeError("ACCOUNT_WIDE", null, ADMIN));
        assertNull(collectionScopeError("ACCOUNT_WIDE", null, THREAT_ENGINEER_ALL));
        assertNull(collectionScopeError(null, null, TEAM_A));

        // every account-wide setting action is marked in struts.xml
        String struts = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/resources/struts.xml")));
        for (String action : new String[]{"modifyThreatConfiguration", "toggleArchivalEnabled", "deleteAllMaliciousEvents",
                "modifyThreatActorStatus", "startPolicyBackfillReplay", "generateThreatReport", "addTestRoles",
                "addAuthMechanism", "addCustomAuthType", "updateUrlSettings", "saveDefaultPayload", "addApiToken", "deleteApiToken"}) {
            int start = struts.indexOf("<action name=\"api/" + action + "\"");
            assertTrue(action, start >= 0);
            String block = struts.substring(start, struts.indexOf("</action>", start));
            assertTrue(action, block.contains("<param name=\"collectionScope\">ACCOUNT_WIDE</param>"));
        }
    }

    // ── Azure group -> role ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private static Map<String, String> resolveGroupRole(Map<String, String> mapping, List<String> groups) throws Exception {
        SAMLConfig samlConfig = new SAMLConfig(ConfigType.AZURE, ACCOUNT_ID);
        samlConfig.setGroupRoleMapping(mapping);
        Method method = SignupAction.class.getDeclaredMethod("resolveSamlGroupScopeRoleMapping", SAMLConfig.class, List.class, int.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(new SignupAction(), samlConfig, groups, ACCOUNT_ID);
    }

    @Test
    public void testAzureGroupRole() throws Exception {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("g-guest", "GUEST");
        mapping.put("g-team-a", "TEAM_A_ADMIN");
        mapping.put("g-dev", "DEVELOPER");
        mapping.put("g-deleted-role", "NO_SUCH_ROLE");

        // most privileged wins; custom role ranked by its base role (Threat Engineer > Developer > Guest)
        Map<String, String> result = resolveGroupRole(mapping, Arrays.asList("g-guest", "g-team-a", "g-dev"));
        assertFalse(result.isEmpty());
        assertTrue(result.values().stream().allMatch("TEAM_A_ADMIN"::equals));
        assertTrue(resolveGroupRole(mapping, Arrays.asList("g-guest", "g-dev")).values().stream().allMatch("DEVELOPER"::equals));

        // no match, unknown role, no groups, no mapping -> null (login unchanged)
        assertNull(resolveGroupRole(mapping, Collections.singletonList("g-unknown")));
        assertNull(resolveGroupRole(mapping, Collections.singletonList("g-deleted-role")));
        assertNull(resolveGroupRole(mapping, new ArrayList<>()));
        assertNull(resolveGroupRole(new HashMap<>(), Collections.singletonList("g-team-a")));
        assertNull(resolveGroupRole(null, Collections.singletonList("g-team-a")));
    }

    @Test
    public void testExistingAdminNotChangedByGroups() throws Exception {
        Method method = SignupAction.class.getDeclaredMethod("isExistingAdmin", String.class, int.class);
        method.setAccessible(true);
        SignupAction action = new SignupAction();
        assertTrue((Boolean) method.invoke(action, user(ADMIN).getLogin(), ACCOUNT_ID));
        assertFalse((Boolean) method.invoke(action, user(TEAM_A).getLogin(), ACCOUNT_ID));
        assertFalse((Boolean) method.invoke(action, "new-user@example.com", ACCOUNT_ID));
    }

    @Test
    public void testSaveAzureGroupRoleMapping() {
        as(ADMIN, CONTEXT_SOURCE.AGENTIC);
        AzureSsoAction action = new AzureSsoAction();
        action.setSession(session(ADMIN));
        action.setConfigType(ConfigType.AZURE);

        Map<String, String> mapping = new HashMap<>();
        mapping.put("g-team-a", "TEAM_A_ADMIN");
        action.setGroupRoleMapping(mapping);
        assertEquals("ERROR", action.saveSamlGroupRoleMapping()); // SSO not set up yet

        SSOConfigsDao.instance.insertOne(new SAMLConfig(ConfigType.AZURE, ACCOUNT_ID));
        assertEquals("SUCCESS", action.saveSamlGroupRoleMapping());
        assertEquals(mapping, SSOConfigsDao.getSAMLConfigByAccountId(ACCOUNT_ID).getGroupRoleMapping());

        for (Map.Entry<String, String> invalid : new HashMap<String, String>() {{
            put("g.dotted", "ADMIN");
            put("g-bad-role", "NO_SUCH_ROLE");
            put("g-no-access", "NO_ACCESS");
        }}.entrySet()) {
            action.setGroupRoleMapping(Collections.singletonMap(invalid.getKey(), invalid.getValue()));
            assertEquals(invalid.getKey(), "ERROR", action.saveSamlGroupRoleMapping());
        }
        assertEquals(mapping, SSOConfigsDao.getSAMLConfigByAccountId(ACCOUNT_ID).getGroupRoleMapping());
    }

    // ── Audit data ─────────────────────────────────────────────────────────────

    private static void insertAudit(int collectionId, String resourceName) {
        McpAuditInfo audit = new McpAuditInfo(Context.now(), "", Constants.AKTO_MCP_SERVER_TAG, 0, resourceName, "", new HashSet<>(), collectionId, resourceName);
        audit.setContextSource(CONTEXT_SOURCE.AGENTIC.name());
        McpAuditInfoDao.instance.insertOne(audit);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> fetchAudit(int userId) {
        as(userId, CONTEXT_SOURCE.AGENTIC);
        AuditDataAction action = new AuditDataAction();
        action.setSession(session(userId));
        assertEquals("SUCCESS", action.fetchAuditData());
        return (List<Object>) (List<?>) action.getAuditData();
    }

    @Test
    public void testAuditDataScoped() {
        insertAudit(1, "own-server");
        insertAudit(3, "other-team-server");
        assertEquals(1, fetchAudit(TEAM_A).size());
        assertEquals(2, fetchAudit(ADMIN).size());
    }
}
