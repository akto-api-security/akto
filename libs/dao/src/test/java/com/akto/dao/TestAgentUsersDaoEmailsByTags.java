package com.akto.dao;

import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AgenticUsers;
import com.akto.dto.DeviceTag;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.utils.MongoBasedTest;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// Audit Data blocks scoped by device tag also resolve to user emails, for browser traffic that has no device id.
public class TestAgentUsersDaoEmailsByTags extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        AgentUsersDao.instance.getMCollection().drop();
        AgentUsersDao.instance.insertMany(Arrays.asList(
                user("janedoe", "Jane.Doe@example.com", "backend"),
                user("johnsmith", "john.smith@example.com", "frontend"),
                user("noemail", null, "backend"),
                user("Vrt.Lead@example.com", "", "backend")));
        ModuleInfoDao.instance.getMCollection().drop();
        ModuleInfo device = new ModuleInfo();
        device.setModuleType(ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD);
        device.setName("noemail-host-1a2b3c4d");
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("username", "noemail");
        additionalData.put("email", "No.Email@example.com");
        device.setAdditionalData(additionalData);
        ModuleInfoDao.instance.insertOne(device);
    }

    private static AgenticUsers user(String name, String email, String team) {
        AgenticUsers u = new AgenticUsers();
        u.setUserName(name);
        u.setUserEmail(email);
        DeviceTag tag = new DeviceTag();
        tag.setKey("team");
        tag.setValue(team);
        u.setDeviceTags(new ArrayList<>(Collections.singletonList(tag)));
        return u;
    }

    private static Map<String, List<String>> team(String value) {
        Map<String, List<String>> tags = new HashMap<>();
        tags.put("team", Collections.singletonList(value));
        return tags;
    }

    @Test
    public void resolvesTaggedUsersToLowerCasedEmails() {
        // Without a userEmail, module_info's email for the username, else an email-shaped userName, stands in.
        assertEquals(new java.util.HashSet<>(Arrays.asList("jane.doe@example.com", "vrt.lead@example.com", "no.email@example.com")),
                new java.util.HashSet<>(AgentUsersDao.instance.findUserEmailsByTags(team("Backend"), null)));

        Map<String, Boolean> exclude = new HashMap<>();
        exclude.put("team", true);
        assertEquals(Collections.singletonList("john.smith@example.com"), AgentUsersDao.instance.findUserEmailsByTags(team("backend"), exclude));

        assertTrue(AgentUsersDao.instance.findUserEmailsByTags(team("sales"), null).isEmpty());
        assertTrue(AgentUsersDao.instance.findUserEmailsByTags(new HashMap<>(), null).isEmpty());
    }

    @Test
    public void selectAllMatchesAnyValueOfTheKey() {
        assertEquals(4, AgentUsersDao.instance.findUserEmailsByTags(team("__all__"), null).size());
        assertEquals(Collections.singletonList("noemail-host-1a2b3c4d"),
                AgentUsersDao.instance.findDeviceIdsByTags(team("__all__"), null, null, false));
    }
}
