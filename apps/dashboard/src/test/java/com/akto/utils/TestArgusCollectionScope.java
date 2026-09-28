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
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.Config.ConfigType;
import com.akto.dto.CustomRole;
import com.akto.dto.GuardrailPolicies;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.RBAC;
import com.akto.dto.Setup;
import com.akto.dto.User;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.sso.SAMLConfig;
import com.akto.dto.traffic.CollectionTags;
import com.akto.interceptor.RoleAccessInterceptor;
import com.akto.util.Constants;
import com.akto.util.DashboardMode;
import com.akto.util.Pair;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;

public class TestArgusCollectionScope extends MongoBasedTest {

    private static final int ZAIDYN = 101, ADMIN = 102, MEMBER = 103, THREAT_ENGINEER_ALL = 104, ZAIDYN_MEMBER = 105;
    private static final String OWN_HOST = "zaidyn-chatbot.zs.com";
    private static final String OWN_CLAUDE_HOST = "laptop1.claude";
    private static final String OTHER_HOST = "finance-bot.zs.com";

    // Roles are only enforced on metered (SaaS / on-prem) dashboards; DashboardMode caches this, so reset it
    private static void setDashboardMode(String mode) throws Exception {
        SetupDao.instance.getMCollection().drop();
        if (mode != null) SetupDao.instance.insertOne(new Setup(mode));
        java.lang.reflect.Field lastFetched = DashboardMode.class.getDeclaredField("lastSaasFetched");
        lastFetched.setAccessible(true);
        lastFetched.setInt(null, 0);
    }

    @Before
    public void setup() throws Exception {
        setDashboardMode("SAAS");
        Context.accountId.set(ACCOUNT_ID);
        Context.contextSource.set(CONTEXT_SOURCE.AGENTIC);
        ApiCollectionsDao.instance.getMCollection().drop();
        RBACDao.instance.getMCollection().drop();
        CustomRoleDao.instance.getMCollection().drop();
        McpAuditInfoDao.instance.getMCollection().drop();
        UsersDao.instance.getMCollection().drop();
        SSOConfigsDao.instance.getMCollection().drop();

        insertAgentCollection(1, OWN_HOST);
        insertAgentCollection(2, OWN_CLAUDE_HOST);
        insertAgentCollection(3, OTHER_HOST);

        insertCustomRole("ZAIDYN_ADMIN", "THREAT_ENGINEER");
        insertCustomRole("ZAIDYN_MEMBER", "MEMBER");

        insertUser(ZAIDYN, "ZAIDYN_ADMIN");
        insertUser(ADMIN, "ADMIN");
        insertUser(MEMBER, "MEMBER");
        insertUser(THREAT_ENGINEER_ALL, "THREAT_ENGINEER");
        insertUser(ZAIDYN_MEMBER, "ZAIDYN_MEMBER");
    }

    private void insertAgentCollection(int id, String host) {
        ApiCollection collection = ApiCollection.createManualCollection(id, host);
        collection.setHostName(host);
        CollectionTags tag = new CollectionTags();
        tag.setKeyName(Constants.AKTO_GEN_AI_TAG);
        tag.setValue("Gen AI");
        collection.setTagsList(Collections.singletonList(tag));
        ApiCollectionsDao.instance.insertOne(collection);
    }

    private void insertCustomRole(String name, String baseRole) {
        CustomRole role = new CustomRole();
        role.setName(name);
        role.setBaseRole(baseRole);
        role.setApiCollectionsId(Arrays.asList(1, 2));
        CustomRoleDao.instance.insertOne(role);
    }

    private void insertUser(int userId, String agenticRole) {
        Map<String, String> scopeRoleMapping = new HashMap<>();
        scopeRoleMapping.put(CONTEXT_SOURCE.AGENTIC.name(), agenticRole);
        RBACDao.instance.insertOne(new RBAC(userId, null, ACCOUNT_ID, scopeRoleMapping));
        User user = user(userId);
        UsersDao.instance.insertOne(user);
        RBACDao.instance.deleteUserEntryFromCache(new Pair<>(userId, ACCOUNT_ID));
        UsersCollectionsList.deleteCollectionIdsFromCache(userId, ACCOUNT_ID);
    }

    private static User user(int userId) {
        User user = new User();
        user.setId(userId);
        user.setLogin("user" + userId + "@zs.com");
        return user;
    }

    private static Map<String, Object> session(int userId) {
        Map<String, Object> session = new HashMap<>();
        session.put("user", user(userId));
        return session;
    }

    private static void as(int userId, CONTEXT_SOURCE contextSource) {
        Context.userId.set(userId);
        Context.contextSource.set(contextSource);
    }

    // ── Who is limited ─────────────────────────────────────────────────────────

    @Test
    public void testOnlyCollectionLimitedArgusUsersAreLimited() {
        as(ZAIDYN, CONTEXT_SOURCE.AGENTIC);
        assertTrue(ArgusCollectionScope.isLimited(user(ZAIDYN)));
        assertEquals(new HashSet<>(Arrays.asList(1, 2)), new HashSet<>(ArgusCollectionScope.getRestrictedCollectionIds(user(ZAIDYN))));
        assertEquals(new HashSet<>(Arrays.asList(OWN_HOST, OWN_CLAUDE_HOST)), ArgusCollectionScope.getRestrictedHosts(user(ZAIDYN)));

        // not limited: admin, built-in roles without assigned collections
        for (int userId : new int[]{ADMIN, MEMBER, THREAT_ENGINEER_ALL}) {
            as(userId, CONTEXT_SOURCE.AGENTIC);
            assertFalse("user " + userId, ArgusCollectionScope.isLimited(user(userId)));
            assertNull(ArgusCollectionScope.getRestrictedHosts(user(userId)));
        }

        // not limited outside Argus (Atlas, API) or without a user/product
        for (CONTEXT_SOURCE other : new CONTEXT_SOURCE[]{CONTEXT_SOURCE.ENDPOINT, CONTEXT_SOURCE.API, CONTEXT_SOURCE.DAST}) {
            as(ZAIDYN, other);
            assertFalse(other.name(), ArgusCollectionScope.isLimited(user(ZAIDYN)));
        }
        as(ZAIDYN, null);
        assertFalse(ArgusCollectionScope.isLimited(user(ZAIDYN)));
        as(ZAIDYN, CONTEXT_SOURCE.AGENTIC);
        assertFalse(ArgusCollectionScope.isLimited(null));
    }

    @Test
    public void testNothingEnforcedOnLocalDeploy() throws Exception {
        // self-hosted (not SaaS / on-prem): roles are not enforced, so nothing new applies
        setDashboardMode(null);
        as(ZAIDYN, CONTEXT_SOURCE.AGENTIC);
        assertFalse(ArgusCollectionScope.isLimited(user(ZAIDYN)));
        assertNull(policyAccessError(MEMBER, CONTEXT_SOURCE.AGENTIC, policy(true)));
        assertNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, policy(true)));
        setDashboardMode("SAAS");
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
        as(ZAIDYN, CONTEXT_SOURCE.AGENTIC);

        // nothing requested -> all own hosts, loose keys and claude device ids
        Map<String, Object> filters = new HashMap<>();
        assertTrue(ArgusCollectionScope.scopeActivityFilters(user(ZAIDYN), filters));
        assertEquals(new HashSet<>(Arrays.asList(OWN_HOST, OWN_CLAUDE_HOST)), new HashSet<>((List<?>) filters.get("hosts")));
        assertEquals(new HashSet<>(Arrays.asList("zaidyn-chatbot com", "laptop1 claude")), new HashSet<>((List<?>) filters.get("looseHostKeys")));
        assertEquals(Collections.singletonList("laptop1"), filters.get("claudeDeviceIds"));

        // another team's host -> nothing visible
        filters = new HashMap<>();
        filters.put("hosts", Collections.singletonList(OTHER_HOST));
        filters.put("looseHostKeys", Collections.singletonList("finance-bot com"));
        assertFalse(ArgusCollectionScope.scopeActivityFilters(user(ZAIDYN), filters));

        // own host requested -> only that host
        filters = new HashMap<>();
        filters.put("hosts", Arrays.asList(OWN_HOST, OTHER_HOST));
        assertTrue(ArgusCollectionScope.scopeActivityFilters(user(ZAIDYN), filters));
        assertEquals(Collections.singletonList(OWN_HOST), filters.get("hosts"));
        assertTrue(((List<?>) filters.get("looseHostKeys")).isEmpty());
        assertTrue(((List<?>) filters.get("claudeDeviceIds")).isEmpty());

        // "all claude config" -> only own claude devices, never the account-wide match
        filters = new HashMap<>();
        filters.put("matchClaudeConfig", true);
        assertTrue(ArgusCollectionScope.scopeActivityFilters(user(ZAIDYN), filters));
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
        assertNotNull(policyAccessError(ZAIDYN_MEMBER, CONTEXT_SOURCE.AGENTIC, policy(false, OWN_HOST)));

        // Atlas, API: unchanged
        assertNull(policyAccessError(MEMBER, CONTEXT_SOURCE.ENDPOINT, policy(true)));
        assertNull(policyAccessError(MEMBER, CONTEXT_SOURCE.API, policy(true)));
    }

    @Test
    public void testPolicyCollectionRule() throws Exception {
        // own collection: by host name or by collection id
        assertNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, policy(false, OWN_HOST)));
        assertNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, policy(false, "1", OWN_CLAUDE_HOST)));

        // apply to all, another team's agent, no target, exclude mode, or replacing a global policy
        assertNotNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, policy(true)));
        assertNotNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, policy(false, OWN_HOST, OTHER_HOST)));
        assertNotNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, policy(false)));
        GuardrailPolicies excludeOwn = policy(false, OWN_HOST);
        excludeOwn.setNegatedAgentServers(true);
        assertNotNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, excludeOwn));
        assertNotNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, policy(false, OWN_HOST), policy(true)));
        GuardrailPolicies legacy = new GuardrailPolicies();
        legacy.setSelectedMcpServers(Collections.singletonList(OTHER_HOST));
        assertNotNull(policyAccessError(ZAIDYN, CONTEXT_SOURCE.AGENTIC, legacy));

        // unlimited Threat Engineer and admin: no collection rule
        assertNull(policyAccessError(THREAT_ENGINEER_ALL, CONTEXT_SOURCE.AGENTIC, policy(false, OTHER_HOST)));
    }

    // ── Account-wide settings ──────────────────────────────────────────────────

    @Test
    public void testAccountWideSettingActions() throws Exception {
        Method method = RoleAccessInterceptor.class.getDeclaredMethod("isAccountWideSettingAction", String.class);
        method.setAccessible(true);
        for (String action : new String[]{"api/modifyThreatConfiguration", "addApiToken", "api/addTestRoles", "api/saveDefaultPayload"}) {
            assertTrue(action, (Boolean) method.invoke(null, action));
        }
        for (String action : new String[]{"api/fetchSuspectSampleData", "api/createGuardrailPolicy", "api/updateMaliciousEventStatus", "api/startTest", null}) {
            assertFalse(String.valueOf(action), (Boolean) method.invoke(null, action));
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
        mapping.put("g-zaidyn", "ZAIDYN_ADMIN");
        mapping.put("g-dev", "DEVELOPER");
        mapping.put("g-deleted-role", "NO_SUCH_ROLE");

        // most privileged wins; custom role ranked by its base role (Threat Engineer > Developer > Guest)
        Map<String, String> result = resolveGroupRole(mapping, Arrays.asList("g-guest", "g-zaidyn", "g-dev"));
        assertFalse(result.isEmpty());
        assertTrue(result.values().stream().allMatch("ZAIDYN_ADMIN"::equals));
        assertTrue(resolveGroupRole(mapping, Arrays.asList("g-guest", "g-dev")).values().stream().allMatch("DEVELOPER"::equals));

        // no match, unknown role, no groups, no mapping -> null (login unchanged)
        assertNull(resolveGroupRole(mapping, Collections.singletonList("g-unknown")));
        assertNull(resolveGroupRole(mapping, Collections.singletonList("g-deleted-role")));
        assertNull(resolveGroupRole(mapping, new ArrayList<>()));
        assertNull(resolveGroupRole(new HashMap<>(), Collections.singletonList("g-zaidyn")));
        assertNull(resolveGroupRole(null, Collections.singletonList("g-zaidyn")));
    }

    @Test
    public void testExistingAdminNotChangedByGroups() throws Exception {
        Method method = SignupAction.class.getDeclaredMethod("isExistingAdmin", String.class, int.class);
        method.setAccessible(true);
        SignupAction action = new SignupAction();
        assertTrue((Boolean) method.invoke(action, user(ADMIN).getLogin(), ACCOUNT_ID));
        assertFalse((Boolean) method.invoke(action, user(ZAIDYN).getLogin(), ACCOUNT_ID));
        assertFalse((Boolean) method.invoke(action, "new-user@zs.com", ACCOUNT_ID));
    }

    @Test
    public void testSaveAzureGroupRoleMapping() {
        as(ADMIN, CONTEXT_SOURCE.AGENTIC);
        AzureSsoAction action = new AzureSsoAction();
        action.setSession(session(ADMIN));
        action.setConfigType(ConfigType.AZURE);

        Map<String, String> mapping = new HashMap<>();
        mapping.put("g-zaidyn", "ZAIDYN_ADMIN");
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
        assertEquals(1, fetchAudit(ZAIDYN).size());
        assertEquals(2, fetchAudit(ADMIN).size());
    }
}
