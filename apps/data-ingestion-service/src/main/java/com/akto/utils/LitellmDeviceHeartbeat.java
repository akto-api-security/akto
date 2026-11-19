package com.akto.utils;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActorFactory;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.log.LoggerMaker;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * Makes each user of Atlas LiteLLM traffic an Endpoint Shield device, so the dashboard shows their
 * username wherever it resolves users by device (e.g. an agent's Devices list).
 *
 * The dashboard only knows device users from MCP_ENDPOINT_SHIELD heartbeats (module_info: name is
 * the device id, additionalData.username the user). LiteLLM users run no Endpoint Shield, so this
 * writes that heartbeat on their behalf, through the database-abstractor heartbeat API the agent
 * itself uses (DataActor#updateModuleInfo). The device id is the first label of the host
 * LitellmAgentEndpointRewrite sets ({identity}.ai-agent.{agent}); the record is keyed by it and
 * marked additionalData.source=litellm so it can be told apart from a real agent.
 *
 * At most one heartbeat per device per HEARTBEAT_INTERVAL_SECONDS per process, well inside the
 * dashboard's 4h active window, so a user seen through LiteLLM stays active; fail-open.
 */
public final class LitellmDeviceHeartbeat {

    private static final LoggerMaker loggerMaker = new LoggerMaker(LitellmDeviceHeartbeat.class, LoggerMaker.LogDb.DATA_INGESTION);
    static final String SOURCE = "litellm";
    static final String ID_PREFIX = "litellm-";
    static final int HEARTBEAT_INTERVAL_SECONDS = 30 * 60;

    // The database-abstractor call and clock; replaceable in tests.
    static Consumer<ModuleInfo> updateModuleInfo = moduleInfo -> DataActorFactory.fetchInstance().updateModuleInfo(moduleInfo);
    static IntSupplier now = Context::now;

    // Device id -> when this process last sent its heartbeat.
    static final Map<String, Integer> lastSent = new ConcurrentHashMap<>();

    private LitellmDeviceHeartbeat() {}

    /** Sends deviceId's heartbeat naming email as its user, unless one went out in the last interval. */
    public static void beat(String deviceId, String email) {
        if (deviceId == null || deviceId.isEmpty() || email == null || email.trim().isEmpty()) {
            return;
        }
        int ts = now.getAsInt();
        boolean[] due = {false};
        lastSent.compute(deviceId, (k, prev) -> {
            if (prev == null || ts - prev >= HEARTBEAT_INTERVAL_SECONDS) {
                due[0] = true;
                return ts;
            }
            return prev;
        });
        if (!due[0]) {
            return;
        }
        try {
            updateModuleInfo.accept(moduleInfo(deviceId, email.trim(), ts));
        } catch (Exception e) {
            loggerMaker.error("LiteLLM device heartbeat failed for " + deviceId + ": " + e.getMessage(), e);
        }
    }

    static ModuleInfo moduleInfo(String deviceId, String email, int ts) {
        Map<String, Object> additionalData = new HashMap<>();
        additionalData.put("username", email);
        additionalData.put("email", email);
        additionalData.put("deviceId", deviceId);
        additionalData.put("source", SOURCE);

        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setId(ID_PREFIX + deviceId);
        moduleInfo.setName(deviceId);
        moduleInfo.setModuleType(ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD);
        moduleInfo.setCurrentVersion(SOURCE);
        moduleInfo.setStartedTs(ts);
        moduleInfo.setLastHeartbeatReceived(ts);
        moduleInfo.setAdditionalData(additionalData);
        return moduleInfo;
    }

    static void reset() {
        lastSent.clear();
    }
}
