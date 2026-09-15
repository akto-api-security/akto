package com.akto.data_actor;

import com.akto.MongoBasedTest;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.context.Context;
import com.akto.dto.AgenticUsers;
import com.akto.dto.ApiCollection;
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

    // Mirrors DbLayer.CLAUDE_AGENT_LOGIN_SYNC_ACCOUNT_ID — the sync is gated to this account, so
    // every Claude test below runs (and asserts) inside its account context.
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
}
