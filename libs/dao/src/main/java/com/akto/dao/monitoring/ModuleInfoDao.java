package com.akto.dao.monitoring;


import com.akto.dao.AccountsContextDao;
import com.akto.dao.MCollection;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

    // Companion to fetchUsernameToDeviceIdsForEndpointShield: username -> email. Some agentic
    // identities only ever exist via module_info reporting (e.g. a browser extension or the
    // Claude Desktop app, which reports additionalData.email but never gets an agent_users doc
    // created for it) — for those, this is the only place their email lives, so GuardrailPolicies
    // targeting-by-user (see GuardrailPoliciesAction#createGuardrailPolicy) re-verifies against
    // this live source instead of trusting a client-supplied email.
    public Map<String, String> fetchUsernameToEmailForEndpointShield() {
        List<ModuleInfo> modules = findAll(Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD),
            Projections.include(ModuleInfo.ADDITIONAL_DATA));
        Map<String, String> result = new HashMap<>();
        for (ModuleInfo m : modules) {
            Map<String, Object> ad = m.getAdditionalData();
            if (ad == null || ad.get("username") == null || ad.get("email") == null) continue;
            String username = String.valueOf(ad.get("username")).trim();
            String email = String.valueOf(ad.get("email")).trim();
            if (username.isEmpty() || email.isEmpty()) continue;
            result.putIfAbsent(username, email);
        }
        return result;
    }

    // Server-side port of endpointShieldHelper.js's buildUsernameMapFromModuleInfos; key shapes must match it exactly.
    public Map<String, String> fetchUsernameLookupMapForEndpointShield() {
        return buildUsernameLookupMap(findAll(Filters.eq(ModuleInfo.MODULE_TYPE, ModuleInfo.ModuleType.MCP_ENDPOINT_SHIELD),
            Projections.include(
                ModuleInfo.NAME,
                ModuleInfo.ADDITIONAL_DATA + ".username",
                ModuleInfo.ADDITIONAL_DATA + ".userName",
                ModuleInfo.ADDITIONAL_DATA + ".user",
                ModuleInfo.ADDITIONAL_DATA + ".email",
                ModuleInfo.ADDITIONAL_DATA + ".deviceId",
                ModuleInfo.ADDITIONAL_DATA + ".endpointId",
                ModuleInfo.ADDITIONAL_DATA + ".mcpServers")));
    }

    // Split from the query above so TestModuleInfoUsernameLookup can pin the key shapes without Mongo.
    public static Map<String, String> buildUsernameLookupMap(List<ModuleInfo> modules) {
        Map<String, String> result = new HashMap<>();
        for (ModuleInfo m : modules) {
            Map<String, Object> ad = m.getAdditionalData();
            String username = resolveModuleUsername(ad);
            if (username == null) continue;

            registerDeviceKey(result, username, m.getName());
            if (ad == null) continue;
            registerDeviceKey(result, username, ad.get("deviceId"));
            registerDeviceKey(result, username, ad.get("endpointId"));

            Object mcpServers = ad.get("mcpServers");
            if (!(mcpServers instanceof Map)) continue;
            for (Object serverObj : ((Map<?, ?>) mcpServers).values()) {
                if (!(serverObj instanceof Map)) continue;
                Object collectionName = ((Map<?, ?>) serverObj).get("collectionName");
                if (collectionName instanceof String && !((String) collectionName).isEmpty()) {
                    result.put(((String) collectionName).toLowerCase(Locale.ROOT), username);
                }
            }
        }
        return result;
    }

    // Matches the JS resolveModuleUsername's candidate order and "-" rejection.
    private static String resolveModuleUsername(Map<String, Object> additionalData) {
        if (additionalData == null) return null;
        for (String field : new String[]{"username", "userName", "user", "email"}) {
            Object raw = additionalData.get(field);
            if (!(raw instanceof String)) continue;
            String v = ((String) raw).trim();
            if (!v.isEmpty() && !"-".equals(v)) return v;
        }
        return null;
    }

    private static void registerDeviceKey(Map<String, String> target, String username, Object rawId) {
        if (rawId == null) return;
        String key = String.valueOf(rawId).toLowerCase(Locale.ROOT);
        if (key.isEmpty()) return;
        target.put("__deviceId__" + key, username);
    }
}
