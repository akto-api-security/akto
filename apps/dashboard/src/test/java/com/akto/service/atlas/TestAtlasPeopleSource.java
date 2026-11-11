package com.akto.service.atlas;

import com.akto.dto.AgenticUsers;
import com.akto.dto.DeviceTag;
import com.akto.dto.McpAllowlist;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestAtlasPeopleSource {

    private static DeviceTag tag(String key, String value) {
        DeviceTag tag = new DeviceTag();
        tag.setKey(key);
        tag.setValue(value);
        return tag;
    }

    private static AgenticUsers user(String name, DeviceTag... tags) {
        AgenticUsers user = new AgenticUsers();
        user.setUserName(name);
        user.setDeviceTags(tags.length == 0 ? null : Arrays.asList(tags));
        return user;
    }

    private static McpAllowlist entry(String name, String type) {
        McpAllowlist entry = new McpAllowlist();
        entry.setName(name);
        entry.setEntryType(type);
        return entry;
    }

    @Test
    public void aUsersTeamIsTheirFirstNonBlankTeamTag() {
        Map<String, String> teams = AtlasPeopleSource.teamsOf(Arrays.asList(
                user("user.one", tag("env", "prod"), tag("Team", " Platform ")),
                user("user.two", tag("team", " "), tag("team", "Data")),
                user("user.three", tag("env", "prod")),
                user("user.four")));
        assertEquals(2, teams.size());
        assertEquals("Platform", teams.get("user.one"));
        assertEquals("Data", teams.get("user.two"));
    }

    @Test
    public void noUsersMeansNoTeams() {
        assertTrue(AtlasPeopleSource.teamsOf(Collections.<AgenticUsers>emptyList()).isEmpty());
    }

    @Test
    public void allowlistNamesAreLowerCaseAndNamelessEntriesAreSkipped() {
        List<McpAllowlist> rows = Arrays.asList(entry("Sample-Server", "MCP_SERVER"), entry(null, "MCP_SERVER"));
        Set<String> names = AtlasPeopleSource.allowlistNames(rows);
        assertEquals(Collections.singleton("sample-server"), names);
    }

    @Test
    public void aVendorEntryIsMatchedByItsCanonicalName() {
        Set<String> names = AtlasPeopleSource.allowlistNames(Collections.singletonList(entry("ChatGPT", McpAllowlist.ENTRY_TYPE_VENDOR)));
        assertEquals(1, names.size());
        String name = names.iterator().next();
        assertEquals(name.toLowerCase(), name);
    }
}
