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
import com.akto.action.user.AzureSsoAction;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.CustomRoleDao;
import com.akto.dao.GuardrailPoliciesDao;
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
import com.akto.dto.rbac.CollectionRule;
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

    private static List<String> visiblePolicies(int userId, int skip, int limit, long expectedTotal) {
        as(userId, CONTEXT_SOURCE.AGENTIC);
        GuardrailPoliciesAction action = new GuardrailPoliciesAction();
        action.setSession(session(userId));
        action.setSkip(skip);
        action.setLimit(limit);
        assertEquals("SUCCESS", action.fetchGuardrailPolicies());
        assertEquals(expectedTotal, action.getTotal());
        List<String> names = new ArrayList<>();
        for (GuardrailPolicies p : action.getGuardrailPolicies()) names.add(p.getName());
        return names;
    }

    @Test
    public void testPolicyListHidesOtherTeamsPolicies() {
        GuardrailPoliciesDao.instance.getMCollection().drop();
        GuardrailPolicies own = policy(false, OWN_HOST), other = policy(false, OTHER_HOST), global = policy(true),
                mixed = policy(false, OWN_HOST, OTHER_HOST), excludeOther = policy(false, OTHER_HOST);
        excludeOther.setNegatedAgentServers(true);
        GuardrailPolicies legacyOther = new GuardrailPolicies();
        legacyOther.setSelectedMcpServers(Collections.singletonList(OTHER_HOST));
        GuardrailPolicies[] all = {own, other, global, mixed, excludeOther, legacyOther};
        String[] names = {"own", "other", "global", "mixed", "excludeOther", "legacyOther"};
        for (int i = 0; i < all.length; i++) {
            all[i].setName(names[i]);
            all[i].setCreatedTimestamp(100 - i);
            GuardrailPoliciesDao.instance.insertOne(all[i]);
        }

        // limited user: own, global and exclude-mode policies, plus ones that also cover an own agent
        assertEquals(Arrays.asList("own", "global", "mixed", "excludeOther"), visiblePolicies(TEAM_A, 0, 20, 4));
        assertEquals(Arrays.asList("mixed", "excludeOther"), visiblePolicies(TEAM_A, 2, 2, 4));
        assertTrue(visiblePolicies(TEAM_A, 10, 20, 4).isEmpty());

        // admin and unlimited users: everything, unchanged
        assertEquals(Arrays.asList(names), visiblePolicies(ADMIN, 0, 20, 6));
        assertEquals(Arrays.asList(names), visiblePolicies(THREAT_ENGINEER_ALL, 0, 20, 6));
        GuardrailPoliciesDao.instance.getMCollection().drop();
    }

    // ── Account-wide settings ──────────────────────────────────────────────────

    private static String collectionScopeError(String collectionScope, Object action, int userId) throws Exception {
        as(userId, CONTEXT_SOURCE.AGENTIC);
        RoleAccessInterceptor interceptor = new RoleAccessInterceptor();
        interceptor.setCollectionScope(collectionScope);
        Method method = RoleAccessInterceptor.class.getDeclaredMethod("checkCollectionScope", Object.class, User.class, int.class);
        method.setAccessible(true);
        return (String) method.invoke(interceptor, action, user(userId), ACCOUNT_ID);
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

    // ── SSO group -> role (shared by every SSO provider) ───────────────────────

    private static Map<String, String> rolesForLogin(Map<String, String> mapping, List<String> groups) {
        return SsoRoleMapping.rolesForLogin("new-user@example.com", ACCOUNT_ID, mapping, groups, false, true);
    }

    @Test
    public void testSsoGroupRole() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("g-guest", "GUEST");
        mapping.put("g-team-a", "TEAM_A_ADMIN");
        mapping.put("g-dev", "DEVELOPER");
        mapping.put("g-deleted-role", "NO_SUCH_ROLE");

        // most privileged wins; custom role ranked by its base role (Threat Engineer > Developer > Guest)
        Map<String, String> result = rolesForLogin(mapping, Arrays.asList("g-guest", "g-team-a", "g-dev"));
        assertFalse(result.isEmpty());
        assertTrue(result.values().stream().allMatch("TEAM_A_ADMIN"::equals));
        assertTrue(rolesForLogin(mapping, Arrays.asList("g-guest", "g-dev")).values().stream().allMatch("DEVELOPER"::equals));

        // no match, unknown role, no groups, no mapping -> null (login unchanged)
        assertNull(rolesForLogin(mapping, Collections.singletonList("g-unknown")));
        assertNull(rolesForLogin(mapping, Collections.singletonList("g-deleted-role")));
        assertNull(rolesForLogin(mapping, new ArrayList<>()));
        assertNull(rolesForLogin(new HashMap<>(), Collections.singletonList("g-team-a")));
        assertNull(rolesForLogin(null, Collections.singletonList("g-team-a")));

        // existing admins are never changed
        assertNull(SsoRoleMapping.rolesForLogin(user(ADMIN).getLogin(), ACCOUNT_ID, mapping, Collections.singletonList("g-guest"), true, true));
        assertTrue(SsoRoleMapping.isExistingAdmin(user(ADMIN).getLogin(), ACCOUNT_ID));
        assertFalse(SsoRoleMapping.isExistingAdmin(user(TEAM_A).getLogin(), ACCOUNT_ID));
        assertFalse(SsoRoleMapping.isExistingAdmin("new-user@example.com", ACCOUNT_ID));
    }

    @Test
    public void testSsoRemoveAccessWithoutGroup() {
        Map<String, String> mapping = Collections.singletonMap("group-a", "MEMBER");
        List<String> noMappedGroup = Collections.singletonList("some-other-group");
        String email = user(TEAM_A).getLogin();

        // off by default: users keep their role
        assertNull(SsoRoleMapping.rolesForLogin(email, ACCOUNT_ID, mapping, noMappedGroup, false, true));
        // on, with the full group list: no access in every product (never an empty mapping, which means the old single role)
        Map<String, String> removed = SsoRoleMapping.rolesForLogin(email, ACCOUNT_ID, mapping, noMappedGroup, true, true);
        assertFalse(removed.isEmpty());
        for (String role : removed.values()) assertEquals("NO_ACCESS", role);
        // on, but the IdP did not send the full group list (claim missing or too many groups): keep the role
        assertNull(SsoRoleMapping.rolesForLogin(email, ACCOUNT_ID, mapping, noMappedGroup, true, false));
        // on, but no mapping set: nothing to reconcile against
        assertNull(SsoRoleMapping.rolesForLogin(email, ACCOUNT_ID, new HashMap<>(), noMappedGroup, true, true));
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
        assertFalse(SSOConfigsDao.getSAMLConfigByAccountId(ACCOUNT_ID).isRemoveAccessWithoutGroup());
        action.setRemoveAccessWithoutGroup(true);
        assertEquals("SUCCESS", action.saveSamlGroupRoleMapping());
        assertTrue(SSOConfigsDao.getSAMLConfigByAccountId(ACCOUNT_ID).isRemoveAccessWithoutGroup());

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

    // ── Collection rules on custom roles ───────────────────────────────────────

    private void insertRuleRole(String name, CollectionRule rule) {
        CustomRole role = new CustomRole();
        role.setName(name);
        role.setBaseRole("THREAT_ENGINEER");
        role.setApiCollectionsId(new ArrayList<>());
        role.setCollectionRules(Collections.singletonList(rule));
        CustomRoleDao.instance.insertOne(role);
        CustomRoleDao.clearRoleCache();
    }

    @Test
    public void testCollectionRules() {
        insertAgentCollection(4, "team-a-new-agent.example.com"); // added later, matches the host rule
        insertRuleRole("TEAM_A_BY_HOST", new CollectionRule("^team-a-", null, null));
        insertUser(106, "TEAM_A_BY_HOST");
        as(106, CONTEXT_SOURCE.AGENTIC);
        assertEquals(new HashSet<>(Arrays.asList(1, 4)), new HashSet<>(ArgusCollectionScope.getRestrictedCollectionIds(user(106))));
        assertEquals(new HashSet<>(Arrays.asList(OWN_HOST, "team-a-new-agent.example.com")), ArgusCollectionScope.getRestrictedHosts(user(106)));

        insertRuleRole("TEAM_BY_TAG", new CollectionRule(null, Constants.AKTO_GEN_AI_TAG, "Gen AI"));
        insertUser(107, "TEAM_BY_TAG");
        as(107, CONTEXT_SOURCE.AGENTIC);
        assertEquals(new HashSet<>(Arrays.asList(1, 2, 3, 4)), new HashSet<>(ArgusCollectionScope.getRestrictedCollectionIds(user(107))));

        // rules that match nothing yet: the user sees nothing, never everything
        insertRuleRole("TEAM_NONE_YET", new CollectionRule("^no-such-agent-", null, null));
        insertUser(108, "TEAM_NONE_YET");
        as(108, CONTEXT_SOURCE.AGENTIC);
        assertEquals(Collections.singletonList(RBACDao.NO_COLLECTION_ID), ArgusCollectionScope.getRestrictedCollectionIds(user(108)));
        assertTrue(ArgusCollectionScope.getRestrictedHosts(user(108)).isEmpty());

        // screens that edit per-user grants only see explicit grants, never rule matches or the sentinel
        for (int userId : new int[]{106, 108}) {
            UsersDao.instance.updateOne(com.mongodb.client.model.Filters.eq("_id", userId),
                    com.mongodb.client.model.Updates.set(User.ACCOUNTS + "." + ACCOUNT_ID, new com.akto.dto.UserAccountEntry(ACCOUNT_ID, "account")));
        }
        assertTrue(RBACDao.instance.getAllUsersCollections(ACCOUNT_ID).get(106).isEmpty());
        assertTrue(RBACDao.instance.getAllUsersCollections(ACCOUNT_ID).get(108).isEmpty());

        // a pattern Mongo cannot run matches nothing: the user stays limited instead of seeing everything
        insertRuleRole("TEAM_BAD_PATTERN", new CollectionRule("\\p{javaLowerCase}+", null, null));
        insertUser(109, "TEAM_BAD_PATTERN");
        as(109, CONTEXT_SOURCE.AGENTIC);
        assertEquals(Collections.singletonList(RBACDao.NO_COLLECTION_ID), ArgusCollectionScope.getRestrictedCollectionIds(user(109)));
    }

    @Test
    public void testCollectionRuleValidation() {
        assertNull(new CollectionRule("^team-a-", null, null).validate());
        assertNull(new CollectionRule(null, "team", "a").validate());
        assertNotNull(new CollectionRule("([bad", null, null).validate());
        assertNotNull(new CollectionRule(null, null, null).validate());
        assertNotNull(new CollectionRule("^x", "team", "a").validate());
        assertNotNull(new CollectionRule(null, "team", "").validate()); // a tag rule needs a value
        assertNotNull(new CollectionRule(new String(new char[201]).replace('\0', 'a'), null, null).validate());
    }
}
