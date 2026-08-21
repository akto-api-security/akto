package com.akto.dao.monitoring;


import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModuleInfoDao extends AccountsContextDao<ModuleInfo> {
    @Override
    public String getCollName() {
        return "module_info";
    }

    public static final ModuleInfoDao instance = new ModuleInfoDao();
    private ModuleInfoDao(){}

    @Override
    public Class<ModuleInfo> getClassT() {
        return ModuleInfo.class;
    }

    public void createIndicesIfAbsent() {
        // moduleType is the most common single-field filter
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{ ModuleInfo.MODULE_TYPE }, false);

        // moduleType + lastHeartbeatReceived — used in heartbeat threshold queries
        MCollection.createIndexIfAbsent(getDBName(), getCollName(),
            new String[]{ ModuleInfo.MODULE_TYPE, ModuleInfo.LAST_HEARTBEAT_RECEIVED }, false);
    }

    // Computed fresh on every call from module_info (updated every heartbeat) rather than
    // from AgenticUsers.devices, which is only ever backfilled once by a startup migration
    // and never kept in sync with devices that report in afterwards.
    //
    // Uses ModuleInfo.name (== the Go agent's GetDeviceLabel(), "{hostname}-{first8ofMachineID}"),
    // NOT additionalData.deviceId (the raw machine ID) — the enforcement layer's
    // filterPoliciesByDeviceId (guardrails-service/.../validator/service.go) matches
    // ApplyToDeviceIds against the device-label prefix parsed out of mcpServerName, which is
    // built from GetDeviceLabel(), never the raw machine ID. Using additionalData.deviceId here
    // would silently make Team/Role/Device targeting match nothing at enforcement time.
    public Map<String, Set<String>> fetchUsernameToDeviceIdsForEndpointShield() {
        List<ModuleInfo> modules = findAll(Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD),
            Projections.include(ModuleInfo.NAME, ModuleInfo.ADDITIONAL_DATA));
        Map<String, Set<String>> result = new HashMap<>();
        for (ModuleInfo m : modules) {
            Map<String, Object> ad = m.getAdditionalData();
            if (ad == null || ad.get("username") == null) continue;
            String username = String.valueOf(ad.get("username")).trim();
            String deviceId = m.getName() == null ? "" : m.getName().trim();
            if (username.isEmpty() || deviceId.isEmpty()) continue;
            result.computeIfAbsent(username, k -> new HashSet<>()).add(deviceId);
        }
        return result;
    }
}
