package com.akto.utility;

import com.akto.dao.context.Context;
import com.akto.data_actor.DataActor;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;

import java.io.IOException;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

/**
 * Handles POST /utility/sendHealthCheck. Heartbeat call from agentic testing (or other) modules.
 * Request body: { "moduleId", "moduleType", "moduleName", "version", "startedTs", "lastHeartbeatReceived" }.
 * All params including startedTs and lastHeartbeatReceived are sent from the calling module.
 * Persists heartbeat via DataActor (same path as ModuleInfoWorker).
 */
public class SendHealthCheckHandler implements HttpHandler {

    private final DataActor dataActor;

    public SendHealthCheckHandler(DataActor dataActor) {
        this.dataActor = dataActor;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!HttpUtil.requirePost(exchange)) return;
        String rawBody = HttpUtil.requireRequestBody(exchange);
        if (rawBody == null) return;
        Map<String, Object> json = HttpUtil.parseJsonBody(rawBody, exchange);
        if (json == null) return;

        String moduleId = HttpUtil.stringOrNull(json, "moduleId");
        String moduleTypeStr = HttpUtil.stringOrNull(json, "moduleType");
        String moduleName = HttpUtil.stringOrNull(json, "moduleName");
        String version = HttpUtil.stringOrNull(json, "version");

        if (moduleId == null || moduleId.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "moduleId is required");
            return;
        }

        ModuleType moduleType = ModuleType.AGENTIC_TESTING;
        if (moduleTypeStr != null && !moduleTypeStr.trim().isEmpty()) {
            try {
                moduleType = ModuleType.valueOf(moduleTypeStr.trim());
            } catch (IllegalArgumentException e) {
                HttpUtil.sendError(exchange, 400, "Invalid moduleType: " + moduleTypeStr);
                return;
            }
        }

        int now = Context.now();
        int startedTs = HttpUtil.intOrDefault(json.get("startedTs"), now);
        int lastHeartbeatReceived = HttpUtil.intOrDefault(json.get("lastHeartbeatReceived"), now);

        ModuleInfo moduleInfo = new ModuleInfo();
        moduleInfo.setId(moduleId.trim());
        moduleInfo.setModuleType(moduleType);
        moduleInfo.setName(moduleName != null && !moduleName.trim().isEmpty() ? moduleName.trim() : moduleId.trim());
        moduleInfo.setCurrentVersion(version != null && !version.trim().isEmpty() ? version.trim() : "1.0");
        moduleInfo.setStartedTs(startedTs);
        moduleInfo.setLastHeartbeatReceived(lastHeartbeatReceived);

        try {
            dataActor.updateModuleInfo(moduleInfo);
        } catch (Exception e) {
            HttpUtil.sendError(exchange, 500, "Failed to save heartbeat: " + e.getMessage());
            return;
        }

        HttpUtil.sendJson(exchange, 200, HttpUtil.mapOf("ok", true, "lastHeartbeatReceived", moduleInfo.getLastHeartbeatReceived()));
    }
}
