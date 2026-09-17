package com.akto.data_actor;

import com.akto.MongoBasedTest;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.EndpointAgentOrganizationDao;
import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.akto.dto.ApiCollection;
import com.akto.dto.EndpointAgentOrganization;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestDbLayer extends MongoBasedTest {
    
    @Test
    public void testCreateCollectionSimpleForVpc_NewCollection() {
        // Test creating a new collection with VPC
        int vxlanId = 123;
        String vpcId = "vpc-123";

        DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(Constants.ID, vxlanId);
        assertNotNull(collection);
        assertEquals(vxlanId, collection.getVxlanId());
        assertEquals(vpcId, collection.getUserSetEnvType());
        assertNotNull(collection.getStartTs());
        assertNotNull(collection.getUrls());
        assertTrue(collection.getUrls().isEmpty());
    }
    
    @Test
    public void testCreateCollectionSimpleForVpc_UpdateExistingCollection() {
        // Test updating an existing collection with new VPC
        int vxlanId = 456;
        String vpcId1 = "vpc-456";
        String vpcId2 = "vpc-789";

        // First create with vpcId1
        DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId1, null, null);

        // Then update with vpcId2
        DbLayer.createCollectionSimpleForVpc(vxlanId, vpcId2, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(Constants.ID, vxlanId);
        assertNotNull(collection);
        assertEquals(vxlanId, collection.getVxlanId());
        assertEquals(vpcId1 + ", " + vpcId2, collection.getUserSetEnvType());
    }
    
    @Test
    public void testCreateCollectionForHostAndVpc_NewCollection() {
        // Test creating a new collection with host and VPC
        String host = "example.com";
        int id = 789;
        String vpcId = "vpc-abc";

        DbLayer.createCollectionForHostAndVpc(host, id, vpcId, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(ApiCollection.HOST_NAME, host);
        assertNotNull(collection);
        assertEquals(id, collection.getId());
        assertEquals(host, collection.getHostName());
        assertEquals(vpcId, collection.getUserSetEnvType());
        assertNotNull(collection.getStartTs());
        assertNotNull(collection.getUrls());
        assertTrue(collection.getUrls().isEmpty());
    }
    
    @Test
    public void testCreateCollectionForHostAndVpc_UpdateExistingCollection() {
        // Test updating an existing collection with new VPC
        String host = "test.com";
        int id = 101;
        String vpcId1 = "vpc-xyz";
        String vpcId2 = "vpc-uvw";

        // First create with vpcId1
        DbLayer.createCollectionForHostAndVpc(host, id, vpcId1, null, null);

        // Then update with vpcId2
        DbLayer.createCollectionForHostAndVpc(host, id, vpcId2, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(ApiCollection.HOST_NAME, host);
        assertNotNull(collection);
        assertEquals(id, collection.getId());
        assertEquals(host, collection.getHostName());
        assertEquals(vpcId1 + ", " + vpcId2, collection.getUserSetEnvType());
    }
    
    @Test
    public void testCreateCollectionForHostAndVpc_NullVpcId() {
        // Test creating a collection with null VPC ID
        String host = "nullvpc.com";
        int id = 202;

        DbLayer.createCollectionForHostAndVpc(host, id, null, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(ApiCollection.HOST_NAME, host);
        assertNotNull(collection);
        assertEquals(id, collection.getId());
        assertEquals(host, collection.getHostName());
        assertNull(collection.getUserSetEnvType());
    }

    @Test
    public void testCreateCollectionForHostAndVpc_NullVpcIdPreservesExistingEnvType() {
        // Test that null VPC ID doesn't modify existing userSetEnvType
        String host = "preserve-env.com";
        int id = 303;
        String existingVpcId = "vpc-existing";

        // First create with existing VPC ID
        DbLayer.createCollectionForHostAndVpc(host, id, existingVpcId, null, null);

        // Then try to update with null VPC ID
        DbLayer.createCollectionForHostAndVpc(host, id, null, null, null);

        ApiCollection collection = ApiCollectionsDao.instance.findOne(ApiCollection.HOST_NAME, host);
        assertNotNull(collection);
        assertEquals(id, collection.getId());
        assertEquals(host, collection.getHostName());
        // Verify the original VPC ID is preserved
        assertEquals(existingVpcId, collection.getUserSetEnvType());
    }

    // One of DbLayer.CLAUDE_AGENT_LOGIN_SYNC_ACCOUNT_IDS — the sync is gated to those accounts, so
    // every Claude test below runs (and asserts) inside one of their account contexts.
    private static final int CLAUDE_SYNC_ACCOUNT_ID = 1726615470;

    @Before
    public void useClaudeSyncAccount() {
        Context.accountId.set(CLAUDE_SYNC_ACCOUNT_ID);
    }

    @After
    public void restoreDefaultAccount() {
        Context.accountId.set(ACCOUNT_ID);
    }

    private static Map<String, Object> claudeLogin(String agentType, String email, String organizationUuid) {
        Map<String, Object> login = new HashMap<>();
        login.put("agentType", agentType);
        login.put("email", email);
        login.put("loggedIn", !email.isEmpty());
        if (!organizationUuid.isEmpty()) login.put("organizationUuid", organizationUuid);
        return login;
    }

    private static void heartbeat(String moduleId, String deviceName, Map<String, Object> agentLogins) {
        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setId(moduleId);
        moduleInfo.setName(deviceName);
        moduleInfo.setModuleType(ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD);
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("agentLogins", agentLogins);
        moduleInfo.setAdditionalData(additionalData);
        DbLayer.updateModuleInfo(moduleInfo);
    }

    @Test
    public void testSyncClaudeAgentUsers_desktopAndCliCollapseToOneUser() {
        String email = "collapse@akto.io";
        String orgUuid = "e68d326b-0acb-4573-85a6-4ed867827c96";
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-desktop", claudeLogin("claude-desktop", email, orgUuid));
        agentLogins.put("claude-cli-user", claudeLogin("claude-cli-user", email, orgUuid));
        // Neither of these should ever produce an agent user.
        agentLogins.put("cursor", claudeLogin("cursor", "cursor-user@akto.io", orgUuid));
        agentLogins.put("claude-plugin", claudeLogin("claude-plugin", "plugin-user@akto.io", orgUuid));

        heartbeat("module-collapse-1", "device-collapse-1", agentLogins);

        List<AgenticUsers> users = AgentUsersDao.instance.findAll(Filters.eq(AgenticUsers.USER_EMAIL, email));
        assertEquals(1, users.size());
        AgenticUsers user = users.get(0);
        assertEquals(email + "_" + orgUuid, user.getUserId());
        assertEquals("collapse", user.getUserName());
        assertEquals(1, user.getDevices().size());
        assertEquals("device-collapse-1", user.getDevices().get(0));

        assertTrue(AgentUsersDao.instance.findAll(Filters.eq(AgenticUsers.USER_EMAIL, "cursor-user@akto.io")).isEmpty());
        assertTrue(AgentUsersDao.instance.findAll(Filters.eq(AgenticUsers.USER_EMAIL, "plugin-user@akto.io")).isEmpty());
    }

    @Test
    public void testSyncClaudeAgentUsers_sameUuidReusesRowAndAccumulatesDevices() {
        String email = "repeat@akto.io";
        String orgUuid = "11111111-1111-1111-1111-111111111111";
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-desktop", claudeLogin("claude-desktop", email, orgUuid));

        heartbeat("module-repeat-1", "device-repeat-1", agentLogins);
        heartbeat("module-repeat-1", "device-repeat-1", agentLogins);
        heartbeat("module-repeat-2", "device-repeat-2", agentLogins);

        List<AgenticUsers> users = AgentUsersDao.instance.findAll(Filters.eq(AgenticUsers.USER_EMAIL, email));
        assertEquals(1, users.size());
        assertEquals(2, users.get(0).getDevices().size());
        assertTrue(users.get(0).getDevices().contains("device-repeat-1"));
        assertTrue(users.get(0).getDevices().contains("device-repeat-2"));
    }

    @Test
    public void testSyncClaudeAgentUsers_newOrgUuidCreatesNewUser() {
        String email = "multiorg@akto.io";
        String firstOrg = "22222222-2222-2222-2222-222222222222";
        String secondOrg = "33333333-3333-3333-3333-333333333333";

        Map<String, Object> firstLogins = new HashMap<>();
        firstLogins.put("claude-desktop", claudeLogin("claude-desktop", email, firstOrg));
        heartbeat("module-multiorg-1", "device-multiorg-1", firstLogins);

        Map<String, Object> secondLogins = new HashMap<>();
        secondLogins.put("claude-desktop", claudeLogin("claude-desktop", email, secondOrg));
        heartbeat("module-multiorg-1", "device-multiorg-1", secondLogins);

        assertEquals(2, AgentUsersDao.instance.findAll(Filters.eq(AgenticUsers.USER_EMAIL, email)).size());
        assertNotNull(AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_ID, email + "_" + firstOrg)));
        assertNotNull(AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_ID, email + "_" + secondOrg)));
    }

    @Test
    public void testSyncClaudeAgentUsers_noOrgUuidKeysOnEmailAlone() {
        String email = "personal@gmail.com";
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-desktop", claudeLogin("claude-desktop", email, ""));

        heartbeat("module-personal-1", "device-personal-1", agentLogins);

        AgenticUsers user = AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_EMAIL, email));
        assertNotNull(user);
        assertEquals(email, user.getUserId());
        assertEquals("personal", user.getUserName());
    }

    @Test
    public void testSyncClaudeAgentUsers_loggedOutAgentCreatesNothing() {
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-desktop", claudeLogin("claude-desktop", "", ""));
        agentLogins.put("claude-cli-user", claudeLogin("claude-cli-user", "", ""));

        long before = AgentUsersDao.instance.getMCollection().countDocuments();
        heartbeat("module-loggedout-1", "device-loggedout-1", agentLogins);
        assertEquals(before, AgentUsersDao.instance.getMCollection().countDocuments());
    }

    private static Map<String, Object> claudeLogin(String agentType, String email, String organizationUuid,
                                                   String organizationName, String organizationType) {
        Map<String, Object> login = claudeLogin(agentType, email, organizationUuid);
        if (organizationName != null) login.put("organizationName", organizationName);
        if (organizationType != null) login.put("organizationType", organizationType);
        return login;
    }

    @Test
    public void testSyncClaudeAgentUsers_recordsOrgNameAndTypeFromCliLogin() {
        String email = "orguser@akto.io";
        String orgUuid = "cccccccc-cccc-cccc-cccc-cccccccccccc";
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-cli-user", claudeLogin("claude-cli-user", email, orgUuid,
                "Acme\\u0027s Organization", "claude_max"));
        agentLogins.put("claude-desktop", claudeLogin("claude-desktop", email, orgUuid,
                "Acme\\u0027s Organization", "claude_max"));

        heartbeat("module-orguser-1", "device-orguser-1", agentLogins);

        AgenticUsers user = AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_ID, email + "_" + orgUuid));
        assertNotNull(user);
        assertEquals("Acme's Organization", user.getOrganizationName());
        assertEquals("claude_max", user.getOrganizationType());
    }

    @Test
    public void testSyncClaudeAgentUsers_desktopOnlyIdentityGetsNoOrgFields() {
        // desktop reports the org, but org fields are only ever read from the cli-user block
        String email = "desktoponly@akto.io";
        String orgUuid = "dddddddd-dddd-dddd-dddd-dddddddddddd";
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-desktop", claudeLogin("claude-desktop", email, orgUuid, "Desktop Org", "claude_max"));

        heartbeat("module-desktoponly-1", "device-desktoponly-1", agentLogins);

        AgenticUsers user = AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_ID, email + "_" + orgUuid));
        assertNotNull(user);
        assertNull(user.getOrganizationName());
        assertNull(user.getOrganizationType());
    }

    @Test
    public void testSyncClaudeAgentUsers_personalAccountGetsNoOrgFields() {
        String email = "personal-fields@gmail.com";
        Map<String, Object> agentLogins = new HashMap<>();
        // a name/type present on a login with no org uuid must still not be stored
        agentLogins.put("claude-cli-user", claudeLogin("claude-cli-user", email, "", "Leaked Org", "claude_pro"));

        heartbeat("module-personalfields-1", "device-personalfields-1", agentLogins);

        AgenticUsers user = AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_ID, email));
        assertNotNull(user);
        assertNull(user.getOrganizationName());
        assertNull(user.getOrganizationType());
    }

    @Test
    public void testSyncClaudeAgentUsers_orgFieldsRefreshOnLaterHeartbeat() {
        String email = "renamed@akto.io";
        String orgUuid = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee";

        Map<String, Object> first = new HashMap<>();
        first.put("claude-cli-user", claudeLogin("claude-cli-user", email, orgUuid, "Before", "claude_pro"));
        heartbeat("module-renamed-1", "device-renamed-1", first);

        Map<String, Object> second = new HashMap<>();
        second.put("claude-cli-user", claudeLogin("claude-cli-user", email, orgUuid, "After", "claude_max"));
        heartbeat("module-renamed-1", "device-renamed-1", second);

        AgenticUsers user = AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_ID, email + "_" + orgUuid));
        assertNotNull(user);
        assertEquals("After", user.getOrganizationName());
        assertEquals("claude_max", user.getOrganizationType());
    }

    @Test
    public void testSyncClaudeAgentUsers_missingOrgFieldsDoNotWipeStoredOnes() {
        String email = "keepfields@akto.io";
        String orgUuid = "ffffffff-ffff-ffff-ffff-ffffffffffff";

        Map<String, Object> first = new HashMap<>();
        first.put("claude-cli-user", claudeLogin("claude-cli-user", email, orgUuid, "Kept Org", "claude_max"));
        heartbeat("module-keepfields-1", "device-keepfields-1", first);

        // a later heartbeat that reports the identity but not the org fields
        Map<String, Object> second = new HashMap<>();
        second.put("claude-cli-user", claudeLogin("claude-cli-user", email, orgUuid));
        heartbeat("module-keepfields-1", "device-keepfields-1", second);

        AgenticUsers user = AgentUsersDao.instance.findOne(Filters.eq(AgenticUsers.USER_ID, email + "_" + orgUuid));
        assertNotNull(user);
        assertEquals("Kept Org", user.getOrganizationName());
        assertEquals("claude_max", user.getOrganizationType());
    }

    private static EndpointAgentOrganization storedOrg(String organizationUuid) {
        return EndpointAgentOrganizationDao.instance.findOne(
                Filters.eq(EndpointAgentOrganization.ORGANIZATION_UUID, organizationUuid));
    }

    private static String storedOrgInfo(String organizationUuid) {
        EndpointAgentOrganization organization = storedOrg(organizationUuid);
        return organization == null ? null : organization.getOrganizationInfo();
    }

    @Test
    public void testSyncEndpointAgentOrganizations_storesNameTypeAndAgentType() {
        String orgUuid = "d89eadc2-b604-4e18-aa83-9e561408ba55";
        Map<String, Object> agentLogins = new HashMap<>();
        // the agent forwards the name straight out of ~/.claude.json, apostrophe still escaped
        agentLogins.put("claude-cli-user", claudeLogin("claude-cli-user", "cburns@vetpartners.com", orgUuid,
                "cburns@vetpartners.com\\u0027s Organization", "claude_max"));

        heartbeat("module-org-1", "device-org-1", agentLogins);

        EndpointAgentOrganization stored = storedOrg(orgUuid);
        assertNotNull(stored);
        assertEquals("cburns@vetpartners.com's Organization__claude_max", stored.getOrganizationInfo());
        assertEquals("claude-cli", stored.getAgentType());
    }

    @Test
    public void testSyncEndpointAgentOrganizations_knownOrgIsNeverRewritten() {
        String orgUuid = "44444444-4444-4444-4444-444444444444";
        Map<String, Object> first = new HashMap<>();
        first.put("claude-cli-user", claudeLogin("claude-cli-user", "org@akto.io", orgUuid, "Original Name", "claude_max"));
        heartbeat("module-org-2", "device-org-2", first);

        EndpointAgentOrganization stored = storedOrg(orgUuid);
        assertNotNull(stored);
        int createdAt = stored.getCreatedAt();

        Map<String, Object> renamed = new HashMap<>();
        renamed.put("claude-cli-user", claudeLogin("claude-cli-user", "org@akto.io", orgUuid, "Renamed", "claude_pro"));
        heartbeat("module-org-2", "device-org-2", renamed);
        heartbeat("module-org-3", "device-org-3", renamed);

        assertEquals(1, EndpointAgentOrganizationDao.instance.findAll(
                Filters.eq(EndpointAgentOrganization.ORGANIZATION_UUID, orgUuid)).size());
        assertEquals("Original Name__claude_max", storedOrgInfo(orgUuid));
        assertEquals(createdAt, storedOrg(orgUuid).getCreatedAt());
    }

    @Test
    public void testSyncEndpointAgentOrganizations_onlyCliUserIsRead() {
        Map<String, Object> agentLogins = new HashMap<>();
        // none of these are the claude-cli-user scope, so none of them may create a row
        agentLogins.put("claude-desktop", claudeLogin("claude-desktop", "d@akto.io", "55555555-5555-5555-5555-555555555555", "Desktop Org", "claude_max"));
        agentLogins.put("claude-cli-project", claudeLogin("claude-cli-project", "pr@akto.io", "66666666-6666-6666-6666-666666666666", "Project Org", "claude_max"));
        agentLogins.put("cursor", claudeLogin("cursor", "c@akto.io", "77777777-7777-7777-7777-777777777777", "Cursor Org", "x"));
        agentLogins.put("claude-plugin", claudeLogin("claude-plugin", "p@akto.io", "88888888-8888-8888-8888-888888888888", "Plugin Org", "x"));

        long before = EndpointAgentOrganizationDao.instance.getMCollection().countDocuments();
        heartbeat("module-org-4", "device-org-4", agentLogins);

        assertEquals(before, EndpointAgentOrganizationDao.instance.getMCollection().countDocuments());
        assertNull(storedOrgInfo("55555555-5555-5555-5555-555555555555"));
        assertNull(storedOrgInfo("66666666-6666-6666-6666-666666666666"));
        assertNull(storedOrgInfo("77777777-7777-7777-7777-777777777777"));
        assertNull(storedOrgInfo("88888888-8888-8888-8888-888888888888"));
    }

    @Test
    public void testSyncEndpointAgentOrganizations_fallsBackToAccountType() {
        String orgUuid = "aaaa1111-aaaa-1111-aaaa-111111111111";
        Map<String, Object> login = claudeLogin("claude-cli-user", "fallback@akto.io", orgUuid, "Fallback Org", null);
        login.put("accountType", "claude_enterprise");
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-cli-user", login);

        heartbeat("module-org-5", "device-org-5", agentLogins);

        assertEquals("Fallback Org__claude_enterprise", storedOrgInfo(orgUuid));
    }

    @Test
    public void testSyncEndpointAgentOrganizations_personalAccountWithoutOrgUuidCreatesNothing() {
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-cli-user", claudeLogin("claude-cli-user", "personal-org@gmail.com", "", null, "claude_pro"));

        long before = EndpointAgentOrganizationDao.instance.getMCollection().countDocuments();
        heartbeat("module-org-6", "device-org-6", agentLogins);
        assertEquals(before, EndpointAgentOrganizationDao.instance.getMCollection().countDocuments());
    }

    @Test
    public void testSyncEndpointAgentOrganizations_skippedForUngatedAccount() {
        Context.accountId.set(ACCOUNT_ID);
        String orgUuid = "99999999-9999-9999-9999-999999999999";
        Map<String, Object> agentLogins = new HashMap<>();
        agentLogins.put("claude-cli-user", claudeLogin("claude-cli-user", "ungated@akto.io", orgUuid, "Ungated Org", "claude_max"));

        heartbeat("module-org-7", "device-org-7", agentLogins);

        assertNull(storedOrgInfo(orgUuid));
    }

    @Test
    public void testFetchEndpointAgentOrganizations_returnsUuidToInfoMap() {
        String firstOrg = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
        String secondOrg = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

        Map<String, Object> firstLogins = new HashMap<>();
        firstLogins.put("claude-cli-user", claudeLogin("claude-cli-user", "fetch1@akto.io", firstOrg, "First Org", "claude_max"));
        heartbeat("module-org-8", "device-org-8", firstLogins);

        Map<String, Object> secondLogins = new HashMap<>();
        secondLogins.put("claude-cli-user", claudeLogin("claude-cli-user", "fetch2@akto.io", secondOrg, "Second Org", "claude_pro"));
        heartbeat("module-org-9", "device-org-9", secondLogins);

        // no filter -> every agent's orgs
        Map<String, String> organizations = DbLayer.fetchEndpointAgentOrganizations(null);
        assertEquals("First Org__claude_max", organizations.get(firstOrg));
        assertEquals("Second Org__claude_pro", organizations.get(secondOrg));

        // blank is treated as no filter too
        assertEquals(organizations, DbLayer.fetchEndpointAgentOrganizations("  "));

        // filtered to the agent that reported them
        Map<String, String> cliOrgs = DbLayer.fetchEndpointAgentOrganizations("claude-cli");
        assertEquals("First Org__claude_max", cliOrgs.get(firstOrg));
        assertEquals("Second Org__claude_pro", cliOrgs.get(secondOrg));

        // an agent that has reported nothing gets an empty map, not everything
        assertTrue(DbLayer.fetchEndpointAgentOrganizations("cursor").isEmpty());
    }

    @Test
    public void testDecodeJsonEscapes() {
        assertEquals("cburns@vetpartners.com's Organization",
                DbLayer.decodeJsonEscapes("cburns@vetpartners.com\\u0027s Organization"));
        assertEquals("a\"b/c\\d", DbLayer.decodeJsonEscapes("a\\\"b\\/c\\\\d"));
        // a decoded backslash must not be re-read as the start of the next escape
        assertEquals("\\u0027", DbLayer.decodeJsonEscapes("\\\\u0027"));
        // left as written when the escape is malformed or unknown
        assertEquals("\\u00", DbLayer.decodeJsonEscapes("\\u00"));
        assertEquals("\\uzzzz", DbLayer.decodeJsonEscapes("\\uzzzz"));
        assertEquals("\\q", DbLayer.decodeJsonEscapes("\\q"));
        assertEquals("plain", DbLayer.decodeJsonEscapes("plain"));
        assertNull(DbLayer.decodeJsonEscapes(null));
    }
}
