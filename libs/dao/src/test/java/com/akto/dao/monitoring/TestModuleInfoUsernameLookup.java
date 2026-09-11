package com.akto.dao.monitoring;

import com.akto.dto.monitoring.ModuleInfo;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

// Pins both server-side ports against their JS originals; drift degrades the grids silently.
public class TestModuleInfoUsernameLookup {

    private static ModuleInfo module(String name, Map<String, Object> additionalData) {
        ModuleInfo m = new ModuleInfo();
        m.setName(name);
        m.setAdditionalData(additionalData);
        return m;
    }

    private static Map<String, Object> ad(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ---- buildUsernameLookupMap (mirrors buildUsernameMapFromModuleInfos) ----

    @Test
    public void registersAllThreeDeviceIdFieldsLowercased() {
        Map<String, String> map = ModuleInfoDao.buildUsernameLookupMap(Arrays.asList(
                module("Laptop-AB12CD34", ad("username", "priya", "deviceId", "RAW-Machine-ID", "endpointId", "EP-99"))));

        assertEquals("priya", map.get("__deviceId__laptop-ab12cd34"));
        assertEquals("priya", map.get("__deviceId__raw-machine-id"));
        assertEquals("priya", map.get("__deviceId__ep-99"));
    }

    @Test
    public void registersMcpServerCollectionNamesUnprefixed() {
        Map<String, Object> servers = new HashMap<>();
        servers.put("s1", ad("collectionName", "Laptop-AB12CD34.claude.Filesystem"));
        Map<String, String> map = ModuleInfoDao.buildUsernameLookupMap(Arrays.asList(
                module("Laptop-AB12CD34", ad("username", "priya", "mcpServers", servers))));

        assertEquals("priya", map.get("laptop-ab12cd34.claude.filesystem"));
    }

    @Test
    public void fallsBackThroughUsernameCandidatesInOrder() {
        assertEquals("via-userName", ModuleInfoDao.buildUsernameLookupMap(Arrays.asList(
                module("d1", ad("userName", "via-userName", "user", "via-user", "email", "via-email"))))
                .get("__deviceId__d1"));

        assertEquals("via-email", ModuleInfoDao.buildUsernameLookupMap(Arrays.asList(
                module("d2", ad("email", "via-email")))).get("__deviceId__d2"));
    }

    @Test
    public void skipsModulesWithNoUsableUsername() {
        Map<String, String> map = ModuleInfoDao.buildUsernameLookupMap(Arrays.asList(
                module("d1", ad("username", "-")),          // the JS builder's explicit "-" rejection
                module("d2", ad("username", "   ")),
                module("d3", ad()),
                module("d4", null)));

        assertTrue(map.isEmpty());
    }

    // ---- buildDeviceMetadataMap (mirrors buildModuleDeviceMap) ----

    @Test
    public void deviceMetadataIsKeyedByModuleNameVerbatim() {
        Map<String, Map<String, String>> map = ModuleInfoDao.buildDeviceMetadataMap(Arrays.asList(
                module("Laptop-AB12CD34", ad("username", "priya", "os", "macOS", "browserName", "Chrome"))));

        Map<String, String> meta = map.get("Laptop-AB12CD34"); // NOT lowercased, unlike the lookup map
        assertNotNull(meta);
        assertEquals("priya", meta.get("username"));
        assertEquals("macOS", meta.get("os"));
        assertEquals("Chrome", meta.get("browserName"));
    }

    @Test
    public void deviceMetadataKeepsLiteralDashAndDefaultsMissingUsername() {
        // JS `||` keeps a literal "-" here, unlike buildUsernameLookupMap which rejects it.
        assertEquals("-", ModuleInfoDao.buildDeviceMetadataMap(Arrays.asList(
                module("d1", ad("username", "-", "userName", "real")))).get("d1").get("username"));

        // No candidate at all falls back to "-".
        assertEquals("-", ModuleInfoDao.buildDeviceMetadataMap(Arrays.asList(
                module("d2", ad()))).get("d2").get("username"));
    }

    @Test
    public void deviceMetadataSkipsNamelessModulesAndNullsAbsentFields() {
        Map<String, Map<String, String>> map = ModuleInfoDao.buildDeviceMetadataMap(Arrays.asList(
                module(null, ad("username", "priya")),
                module("", ad("username", "priya")),
                module("d1", ad("username", "priya"))));

        assertEquals(1, map.size());
        assertNull(map.get("d1").get("os"));
        assertNull(map.get("d1").get("browserName"));
    }
}
