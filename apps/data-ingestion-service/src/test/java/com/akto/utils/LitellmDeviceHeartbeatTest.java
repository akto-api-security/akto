package com.akto.utils;

import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.BasicDBObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class LitellmDeviceHeartbeatTest {

    private Consumer<ModuleInfo> originalUpdate;
    private IntSupplier originalNow;
    private final List<ModuleInfo> sent = new ArrayList<>();
    private int clock = 1_000_000;

    @Before
    public void setUp() {
        originalUpdate = LitellmDeviceHeartbeat.updateModuleInfo;
        originalNow = LitellmDeviceHeartbeat.now;
        LitellmDeviceHeartbeat.reset();
        LitellmDeviceHeartbeat.updateModuleInfo = sent::add;
        LitellmDeviceHeartbeat.now = () -> clock;
    }

    @After
    public void tearDown() {
        LitellmDeviceHeartbeat.updateModuleInfo = originalUpdate;
        LitellmDeviceHeartbeat.now = originalNow;
        LitellmDeviceHeartbeat.reset();
    }

    @Test
    public void heartbeatNamesTheUserOfTheDevice() {
        LitellmDeviceHeartbeat.beat("jane", " jane@example.com ");
        assertEquals(1, sent.size());
        ModuleInfo m = sent.get(0);
        assertEquals("litellm-jane", m.getId());
        assertEquals("jane", m.getName());
        assertEquals(ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD, m.getModuleType());
        assertEquals(clock, m.getLastHeartbeatReceived());
        assertEquals("jane@example.com", m.getAdditionalData().get("username"));
        assertEquals("jane@example.com", m.getAdditionalData().get("email"));
        assertEquals("jane", m.getAdditionalData().get("deviceId"));
        assertEquals("litellm", m.getAdditionalData().get("source"));
    }

    @Test
    public void heartbeatIsSentOncePerIntervalPerDevice() {
        LitellmDeviceHeartbeat.beat("jane", "jane@example.com");
        clock += LitellmDeviceHeartbeat.HEARTBEAT_INTERVAL_SECONDS - 1;
        LitellmDeviceHeartbeat.beat("jane", "jane@example.com");
        LitellmDeviceHeartbeat.beat("raj", "raj@example.com");
        assertEquals(2, sent.size());

        clock += 1;
        LitellmDeviceHeartbeat.beat("jane", "jane@example.com");
        assertEquals(3, sent.size());
        assertEquals("jane", sent.get(2).getName());
    }

    @Test
    public void nothingIsSentWithoutDeviceOrEmail() {
        LitellmDeviceHeartbeat.beat(null, "jane@example.com");
        LitellmDeviceHeartbeat.beat("", "jane@example.com");
        LitellmDeviceHeartbeat.beat("jane", null);
        LitellmDeviceHeartbeat.beat("jane", "  ");
        assertTrue(sent.isEmpty());
    }

    @Test
    public void aFailedHeartbeatDoesNotThrow() {
        LitellmDeviceHeartbeat.updateModuleInfo = m -> {
            throw new RuntimeException("abstractor down");
        };
        LitellmDeviceHeartbeat.beat("jane", "jane@example.com");
    }

    @Test
    public void deviceIdIsTheFirstHostLabel() {
        Map<String, Object> data = new HashMap<>();
        data.put("requestHeaders", new BasicDBObject("Host", "jane.ai-agent.opencode-litellm").toJson());
        assertEquals("jane", LitellmAgentEndpointRewrite.deviceId(data));

        data.put("requestHeaders", new BasicDBObject("content-type", "application/json").toJson());
        assertNull(LitellmAgentEndpointRewrite.deviceId(data));
        assertNull(LitellmAgentEndpointRewrite.deviceId(new HashMap<>()));
    }
}
