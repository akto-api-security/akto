package com.akto.action;

import com.akto.dao.AgentUsersDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.data_actor.DbLayer;
import com.akto.dto.AgenticUsers;
import com.akto.dto.DeviceTag;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.util.Constants;
import com.akto.utils.MongoBasedTest;
import com.mongodb.client.model.Filters;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

// Audit Data block scope: inherited by new device rows, and resolved for guardrails / devices on fetch.
public class TestMcpAuditBlockScope extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        McpAuditInfoDao.instance.getMCollection().drop();
        AgentUsersDao.instance.getMCollection().drop();
        ModuleInfoDao.instance.getMCollection().drop();
    }

    private static Map<String, List<String>> backendTeam() {
        Map<String, List<String>> tags = new HashMap<>();
        tags.put("team", new ArrayList<>(Collections.singletonList("backend")));
        return tags;
    }

    private static McpAuditInfo blockedServer(String resourceName, Map<String, List<String>> tags) {
        McpAuditInfo row = server(resourceName);
        row.setRemarks("Rejected");
        row.setBlockAll(true);
        row.setTargetTags(tags);
        return row;
    }

    private static McpAuditInfo server(String resourceName) {
        McpAuditInfo row = new McpAuditInfo();
        row.setType(Constants.AKTO_MCP_SERVER_TAG);
        row.setResourceName(resourceName);
        return row;
    }

    private static McpAuditInfo stored(String resourceName) {
        return McpAuditInfoDao.instance.findOne(Filters.eq(McpAuditInfo.RESOURCE_NAME, resourceName));
    }

    @Test
    public void newDeviceRowInheritsTheBlockScope() {
        AgenticUsers jane = new AgenticUsers();
        jane.setUserName("janedoe");
        jane.setUserEmail("jane.doe@example.com");
        McpAuditInfo blocked = blockedServer("janedoe-mac.cursor.github", backendTeam());
        blocked.setUserMetadata(Collections.singletonList(jane));
        blocked.setNegatedTargetUserNames(true);
        McpAuditInfoDao.instance.insertOne(blocked);

        DbLayer.insertMCPAuditDataLog(server("samlee-mac.cursor.github"));

        McpAuditInfo row = stored("samlee-mac.cursor.github");
        assertEquals("Rejected", row.getRemarks());
        assertEquals(backendTeam(), row.getTargetTags());
        assertEquals("jane.doe@example.com", row.getUserMetadata().get(0).getUserEmail());
        assertTrue(row.isNegatedTargetUserNames());
    }

    @Test
    public void unscopedBlockStaysForEveryone() {
        McpAuditInfoDao.instance.insertOne(blockedServer("janedoe-mac.cursor.notion", null));

        DbLayer.insertMCPAuditDataLog(server("samlee-mac.cursor.notion"));

        McpAuditInfo row = stored("samlee-mac.cursor.notion");
        assertEquals("Rejected", row.getRemarks());
        assertNull(row.getTargetTags());
        assertNull(row.getUserMetadata());
    }

    @Test
    public void fetchResolvesTagScopesToDevicesAndEmails() {
        AgenticUsers jane = new AgenticUsers();
        jane.setUserName("janedoe");
        jane.setUserEmail("Jane.Doe@example.com");
        DeviceTag team = new DeviceTag();
        team.setKey("team");
        team.setValue("backend");
        jane.setDeviceTags(new ArrayList<>(Collections.singletonList(team)));
        AgentUsersDao.instance.insertOne(jane);
        ModuleInfo device = new ModuleInfo();
        device.setModuleType(ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD);
        device.setName("janedoe-mac-1a2b3c4d");
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("username", "janedoe");
        device.setAdditionalData(additionalData);
        ModuleInfoDao.instance.insertOne(device);
        McpAuditInfoDao.instance.insertMany(Arrays.asList(
                blockedServer("janedoe-mac.cursor.github", backendTeam()),
                blockedServer("janedoe-mac.cursor.notion", null)));

        DbAction action = new DbAction();
        action.setRemarksList(Collections.singletonList("Rejected"));
        action.fetchMcpAuditInfo();

        for (McpAuditInfo row : action.getMcpAuditInfoList()) {
            if (row.getTargetTags() == null) {
                // Unscoped: nothing resolved, so it still applies to everyone.
                assertNull(row.getApplyToDeviceIds());
                assertNull(row.getApplyToUserEmails());
            } else {
                assertEquals(Collections.singletonList("janedoe-mac-1a2b3c4d"), row.getApplyToDeviceIds());
                assertEquals(Collections.singletonList("jane.doe@example.com"), row.getApplyToUserEmails());
            }
        }
        assertEquals(2, action.getMcpAuditInfoList().size());
    }
}
