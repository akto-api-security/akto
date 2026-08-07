package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.AgentUsersDao;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.AgenticUsers;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.dto.monitoring.ModuleInfoConstants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class ModuleInfoAction extends UserAction {
    private List<ModuleInfo> moduleInfos;
    private Map<String, Object> filter;
    private List<String> moduleIds;
    @Getter
    @Setter
    private boolean deleteTopicAndReboot;
    @Getter
    @Setter
    private String moduleId;
    @Getter
    @Setter
    private String moduleName;
    @Getter
    @Setter
    private Map<String, String> envData;

    @Getter
    @Setter
    private String username;
    @Getter
    @Setter
    private List<String> usernames;
    @Getter
    @Setter
    private String team;
    @Getter
    @Setter
    private String userRole;
    @Getter
    @Setter
    private String userEmail;

    @Getter
    private List<AgenticUsers> agenticUsers;

    // ---- Endpoint Shield server-side pagination (ATLAS) ----
    @Setter private int skip;
    @Setter private int limit;
    @Setter private String sortKey;
    @Setter private int sortOrder;                 // 1 asc, -1 desc
    @Setter private List<String> hostnames;
    @Setter private List<String> deviceIds;
    @Setter private List<String> oses;
    @Setter private String queryValue;
    @Setter private int startTimestamp;
    @Setter private int endTimestamp;
    @Getter private long total;
    @Getter private Map<String, Object> filterOptions;

    @Override
    public String execute() {
        return SUCCESS;
    }

    private static final String AD_DEVICE_ID = ModuleInfo.ADDITIONAL_DATA + ".deviceId";
    private static final String AD_USERNAME = ModuleInfo.ADDITIONAL_DATA + ".username";
    private static final String AD_OS = ModuleInfo.ADDITIONAL_DATA + ".os";

    private Bson buildEndpointShieldFilter() {
        List<Bson> f = new ArrayList<>();
        f.add(Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.MCP_ENDPOINT_SHIELD.toString()));
        if (startTimestamp > 0) f.add(Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, startTimestamp));
        if (endTimestamp > 0) f.add(Filters.lte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, endTimestamp));
        if (hostnames != null && !hostnames.isEmpty()) f.add(Filters.in(ModuleInfo.NAME, hostnames));
        if (usernames != null && !usernames.isEmpty()) f.add(Filters.in(AD_USERNAME, usernames));
        if (deviceIds != null && !deviceIds.isEmpty()) f.add(Filters.in(AD_DEVICE_ID, deviceIds));
        if (oses != null && !oses.isEmpty()) f.add(Filters.in(AD_OS, oses));
        if (queryValue != null && !queryValue.trim().isEmpty()) {
            String q = Pattern.quote(queryValue.trim());
            f.add(Filters.or(
                    Filters.regex(ModuleInfo.NAME, q, "i"),
                    Filters.regex(AD_DEVICE_ID, q, "i"),
                    Filters.regex(AD_USERNAME, q, "i"),
                    Filters.regex(ModuleInfoDao.ID, q, "i")
            ));
        }
        return Filters.and(f);
    }

    private static String mapEndpointShieldSortField(String key) {
        if (key == null) return ModuleInfo.LAST_HEARTBEAT_RECEIVED;
        switch (key) {
            case "hostname": return ModuleInfo.NAME;
            case "deviceId": return AD_DEVICE_ID;
            case "username": return AD_USERNAME;
            case "os": return AD_OS;
            case "agentVersion": return ModuleInfo.CURRENT_VERSION;
            case "lastDeployed": return ModuleInfo.STARTED_TS;
            case "lastHeartbeat":
            default: return ModuleInfo.LAST_HEARTBEAT_RECEIVED;
        }
    }

    /**
     * Server-side paginated Endpoint Shield agent list (ATLAS). Replaces the old load-all fetchModuleInfo
     * on that page which pulled every device's full module doc at once (~10MB at 1000 devices).
     */
    public String fetchEndpointShieldAgents() {
        Bson filter = buildEndpointShieldFilter();
        total = ModuleInfoDao.instance.count(filter);

        String sortField = mapEndpointShieldSortField(sortKey);
        Bson sort = (sortOrder < 0) ? Sorts.descending(sortField) : Sorts.ascending(sortField);

        int lim = (limit <= 0) ? 20 : Math.min(limit, 200);
        int sk = Math.max(skip, 0);
        moduleInfos = ModuleInfoDao.instance.findAll(filter, sk, lim, sort);
        filterEnvironmentVariables(moduleInfos);
        allowedEnvFields = computeAllowedEnvFields();
        return SUCCESS.toUpperCase();
    }

    /** Distinct filter-dropdown values across ALL endpoint-shield agents (for the paginated table). */
    public String fetchEndpointShieldFilterOptions() {
        Bson base = Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.MCP_ENDPOINT_SHIELD.toString());
        filterOptions = new HashMap<>();
        filterOptions.put("hostnames", distinctStrings(ModuleInfo.NAME, base));
        filterOptions.put("usernames", distinctStrings(AD_USERNAME, base));
        filterOptions.put("deviceIds", distinctStrings(AD_DEVICE_ID, base));
        filterOptions.put("oses", distinctStrings(AD_OS, base));
        return SUCCESS.toUpperCase();
    }

    /**
     * Lightweight module-info projection for MCP_ENDPOINT_SHIELD agents (ATLAS) — used by
     * endpointShieldHelper.js to build a username lookup map + per-device OS/browser display data, not
     * for the agent's own settings/observability. Excludes env vars/agent-version/heartbeat/etc from the
     * projection like before, but ALSO collapses each device's `mcpServers` sub-object (clientType/url/
     * updatedTs per MCP server) down to just the list of collection names — the only field the frontend's
     * buildUsernameMapFromModuleInfos actually reads from it. That sub-object was the reason the
     * "projected" response was still ~3MB at 1000-device scale.
     */
    public String fetchEndpointShieldUserMetadata() {
        Bson filter = Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.MCP_ENDPOINT_SHIELD.toString());
        Bson projection = Projections.include(
                ModuleInfoDao.ID, ModuleInfo.NAME,
                ModuleInfo.ADDITIONAL_DATA + ".username",
                ModuleInfo.ADDITIONAL_DATA + ".userName",
                ModuleInfo.ADDITIONAL_DATA + ".user",
                ModuleInfo.ADDITIONAL_DATA + ".email",
                ModuleInfo.ADDITIONAL_DATA + ".deviceId",
                ModuleInfo.ADDITIONAL_DATA + ".endpointId",
                ModuleInfo.ADDITIONAL_DATA + ".os",
                ModuleInfo.ADDITIONAL_DATA + ".browserName",
                ModuleInfo.ADDITIONAL_DATA + ".mcpServers"
        );
        List<ModuleInfo> infos = ModuleInfoDao.instance.findAll(filter, projection);

        for (ModuleInfo m : infos) {
            Map<String, Object> ad = m.getAdditionalData();
            if (ad == null) continue;
            Object mcpServersObj = ad.get("mcpServers");
            if (!(mcpServersObj instanceof Map)) continue;

            List<String> collectionNames = new ArrayList<>();
            for (Object serverObj : ((Map<?, ?>) mcpServersObj).values()) {
                if (!(serverObj instanceof Map)) continue;
                Object collectionName = ((Map<?, ?>) serverObj).get("collectionName");
                if (collectionName instanceof String && !((String) collectionName).isEmpty()) {
                    collectionNames.add((String) collectionName);
                }
            }
            ad.remove("mcpServers");
            ad.put("mcpServerCollectionNames", collectionNames);
        }

        moduleInfos = infos;
        return SUCCESS.toUpperCase();
    }

    private List<String> distinctStrings(String field, Bson filter) {
        List<String> out = new ArrayList<>();
        try {
            for (String v : ModuleInfoDao.instance.getMCollection().distinct(field, filter, String.class)) {
                if (v != null && !v.isEmpty()) out.add(v);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private static final int heartbeatThresholdSeconds = 5 * 60; // 5 minutes
    private static final int rebootThresholdSeconds = 2 * 60; // 2 minutes
    private static final String _DEFAULT_PREFIX_REGEX_STRING = "^(Default_|akto-mr)";

    private List<Map<String, String>> allowedEnvFields;

    public String fetchModuleInfo() {
        List<Bson> filters = new ArrayList<>();

        boolean isEndpointShield = false;
        boolean hasCustomHeartbeatFilter = false;

        // Apply filter if provided
        if (filter != null && !filter.isEmpty()) {
            if (filter.containsKey(ModuleInfo.MODULE_TYPE)) {
                String moduleTypeStr = (String) filter.get(ModuleInfo.MODULE_TYPE);
                if(ModuleType.MCP_ENDPOINT_SHIELD.toString().equals(moduleTypeStr)) {
                    isEndpointShield = true;
                }
                filters.add(Filters.eq(ModuleInfo.MODULE_TYPE, moduleTypeStr));
            }

            if (filter.containsKey("id")) {
                String idValue = (String) filter.get("id");
                filters.add(Filters.eq(ModuleInfoDao.ID, idValue));
            }

            if (filter.containsKey(ModuleInfo.LAST_HEARTBEAT_RECEIVED)) {
                Object heartbeatFilter = filter.get(ModuleInfo.LAST_HEARTBEAT_RECEIVED);
                if (heartbeatFilter instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> heartbeatMap = (Map<String, Object>) heartbeatFilter;

                    if (heartbeatMap.containsKey("$gte")) {
                        int gte = ((Number) heartbeatMap.get("$gte")).intValue();
                        filters.add(Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, gte));
                        hasCustomHeartbeatFilter = true;
                    }
                    if (heartbeatMap.containsKey("$lte")) {
                        int lte = ((Number) heartbeatMap.get("$lte")).intValue();
                        filters.add(Filters.lte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, lte));
                        hasCustomHeartbeatFilter = true;
                    }
                }
            }
            // Add more filter fields as needed
        }

        if (!isEndpointShield && !hasCustomHeartbeatFilter) {
            int deltaTime = Context.now() - heartbeatThresholdSeconds;
            filters.add(Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, deltaTime));
        }

        Bson finalFilter = filters.isEmpty() ? Filters.empty() : Filters.and(filters);
        moduleInfos = ModuleInfoDao.instance.findAll(finalFilter);

        // Filter environment variables to only expose whitelisted keys
        filterEnvironmentVariables(moduleInfos);

        // Prepare allowed env fields list by combining all module-specific fields
        allowedEnvFields = new ArrayList<>();
        for (Map.Entry<ModuleType, Map<String, String>> moduleEntry : ModuleInfoConstants.ALLOWED_ENV_KEYS_BY_MODULE.entrySet()) {
            for (Map.Entry<String, String> entry : moduleEntry.getValue().entrySet()) {
                Map<String, String> field = new HashMap<>();
                field.put("key", entry.getKey());
                field.put("label", entry.getValue());
                field.put("type", getFieldType(entry.getKey()));
                field.put("moduleCategory", moduleEntry.getKey().toString());
                allowedEnvFields.add(field);
            }
        }

        return SUCCESS.toUpperCase();
    }

    public List<Map<String, String>> getAllowedEnvFields() {
        return allowedEnvFields;
    }

    private List<Map<String, String>> computeAllowedEnvFields() {
        List<Map<String, String>> fields = new ArrayList<>();
        for (Map.Entry<ModuleType, Map<String, String>> moduleEntry : ModuleInfoConstants.ALLOWED_ENV_KEYS_BY_MODULE.entrySet()) {
            for (Map.Entry<String, String> entry : moduleEntry.getValue().entrySet()) {
                Map<String, String> field = new HashMap<>();
                field.put("key", entry.getKey());
                field.put("label", entry.getValue());
                field.put("type", getFieldType(entry.getKey()));
                field.put("moduleCategory", moduleEntry.getKey().toString());
                fields.add(field);
            }
        }
        return fields;
    }

    private String getFieldType(String key) {
        if (ModuleInfoConstants.SECRET_ENV_KEYS.contains(key)) {
            return "secret";
        }
        if (key.startsWith("ENABLE_") ||
                key.equals("AKTO_IGNORE_ENVOY_PROXY_CALLS") ||
                key.equals("AKTO_IGNORE_IP_TRAFFIC") ||
                key.equals("AKTO_K8_METADATA_CAPTURE") ||
                key.equals("AKTO_THREAT_ENABLED") ||
                key.equals("AGGREGATION_RULES_ENABLED") ||
                key.equals("SKIP_THREAT") ||
                key.equals("APPLY_GUARDRAILS_TO_SSE") ||
                key.equals("ENABLE_CLAUDE_SETTINGS_CONFIG_SCAN")) {
            return "boolean";
        }
        return "text";
    }

    private void filterEnvironmentVariables(List<ModuleInfo> modules) {
        if (modules == null) {
            return;
        }

        for (ModuleInfo module : modules) {
            ModuleType moduleType = module.getModuleType();
            Map<String, String> allowedKeys = ModuleInfoConstants.ALLOWED_ENV_KEYS_BY_MODULE.get(moduleType);
            if (allowedKeys == null) {
                continue;
            }

            // Go module writes env vars to additionalData.env once at startup (no heartbeat overwrite).
            // Dashboard writes desired changes to the same field. Read from there directly.
            @SuppressWarnings("unchecked")
            Map<String, Object> actualEnv = (module.getAdditionalData() != null
                    && module.getAdditionalData().get("env") instanceof Map)
                    ? (Map<String, Object>) module.getAdditionalData().get("env")
                    : null;

            Map<String, Object> filteredEnv = new HashMap<>();
            for (String key : allowedKeys.keySet()) {
                if (actualEnv != null && actualEnv.containsKey(key)) {
                    boolean isSecret = ModuleInfoConstants.SECRET_ENV_KEYS.contains(key);
                    filteredEnv.put(key, isSecret ? ModuleInfoConstants.REDACTED_PLACEHOLDER : actualEnv.get(key));
                }
            }

            if (module.getAdditionalData() == null) {
                module.setAdditionalData(new HashMap<>());
            }
            module.getAdditionalData().put("env", filteredEnv);
        }
    }

    public String deleteModuleInfo() {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return ERROR.toUpperCase();
        }

        // Delete modules by their IDs
        Bson deleteFilter = Filters.in(ModuleInfoDao.ID, moduleIds);
        ModuleInfoDao.instance.deleteAll(deleteFilter);

        return SUCCESS.toUpperCase();
    }

    public String rebootModules() {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return ERROR.toUpperCase();
        }

        try {
            int deltaTimeForReboot = Context.now() - rebootThresholdSeconds;

            // Find modules that received heartbeat in the last threshold minute(s) and name starts with "Default_"
            // TODO: Handle non-default modules reboot
            Bson rebootFilter = Filters.and(
                Filters.in(ModuleInfoDao.ID, moduleIds),
                Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, deltaTimeForReboot),
                Filters.or(
                    Filters.regex(ModuleInfo.NAME, _DEFAULT_PREFIX_REGEX_STRING),
                    Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.TRAFFIC_COLLECTOR.toString()),
                    Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.AKTO_AGENT_GATEWAY.toString()),
                    Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.THREAT_DETECTION.toString())
                )
            );

            // Update reboot flag to true for matching modules
            // Use deleteTopicAndReboot flag if specified, otherwise use regular reboot flag
            String rebootField = deleteTopicAndReboot ? ModuleInfo.DELETE_TOPIC_AND_REBOOT : ModuleInfo._REBOOT;

            ModuleInfoDao.instance.updateMany(rebootFilter, Updates.set(rebootField, true));

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            return ERROR.toUpperCase();
        }
    }

    public List<ModuleInfo> getModuleInfos() {
        return moduleInfos;
    }

    public void setModuleInfos(List<ModuleInfo> moduleInfos) {
        this.moduleInfos = moduleInfos;
    }

    public Map<String, Object> getFilter() {
        return filter;
    }

    public void setFilter(Map<String, Object> filter) {
        this.filter = filter;
    }

    public List<String> getModuleIds() {
        return moduleIds;
    }

    public void setModuleIds(List<String> moduleIds) {
        this.moduleIds = moduleIds;
    }

    public String updateUserDeviceTag() {
        if (username == null || username.trim().isEmpty()) {
            addActionError("Username is required");
            return ERROR.toUpperCase();
        }

        AgentUsersDao.instance.upsertTagFromDashboard(username, userEmail, team, userRole, getSUser().getLogin());
        return SUCCESS.toUpperCase();
    }

    public String bulkUpdateUserDeviceTag() {
        if (usernames == null || usernames.isEmpty()) {
            addActionError("At least one username is required");
            return ERROR.toUpperCase();
        }

        String updatedBy = getSUser().getLogin();
        for (String u : usernames) {
            AgentUsersDao.instance.upsertTagFromDashboard(u, null, team, userRole, updatedBy);
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchAgenticUsers() {
        agenticUsers = AgentUsersDao.instance.findAll(Filters.empty());
        // Overwrite teamName/userRole with SSO values for users not manually pinned,
        // so callers always see a single consistent effective field.
        for (AgenticUsers u : agenticUsers) {
            if (!AgenticUsers.SOURCE_MANUAL.equals(u.getTeamSource()) && u.getSsoTeamName() != null) {
                u.setTeamName(u.getSsoTeamName());
            }
            if (!AgenticUsers.SOURCE_MANUAL.equals(u.getRoleSource()) && u.getSsoUserRole() != null) {
                u.setUserRole(u.getSsoUserRole());
            }
        }
        return SUCCESS.toUpperCase();
    }

    public String updateModuleEnvAndReboot() {
        if (moduleId == null || moduleId.isEmpty()) {
            return ERROR.toUpperCase();
        }

        if (envData == null || envData.isEmpty()) {
            return SUCCESS.toUpperCase();
        }

        try {
            Bson moduleFilter = Filters.eq(ModuleInfoDao.ID, moduleId);


            List<Bson> updates = new ArrayList<>();

            // Write directly to additionalData.env — same field the Go module writes at startup.
            // Go module only writes env vars once at startup (not on every heartbeat), so no race condition.
            for (Map.Entry<String, String> entry : envData.entrySet()) {
                boolean isAllowedKey = ModuleInfoConstants.ALLOWED_ENV_KEYS_BY_MODULE.values().stream()
                    .anyMatch(moduleEnvMap -> moduleEnvMap.containsKey(entry.getKey()));

                if (isAllowedKey) {
                    // Skip secret fields if the user submitted the redacted placeholder unchanged
                    if (ModuleInfoConstants.SECRET_ENV_KEYS.contains(entry.getKey())
                            && ModuleInfoConstants.REDACTED_PLACEHOLDER.equals(entry.getValue())) {
                        continue;
                    }
                    updates.add(Updates.set(ModuleInfo.ADDITIONAL_DATA + ".env." + entry.getKey(), entry.getValue()));
                }
            }

            updates.add(Updates.set(ModuleInfo._REBOOT, true));


            ModuleInfoDao.instance.updateMany(moduleFilter, Updates.combine(updates));

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            e.printStackTrace();
            return ERROR.toUpperCase();
        }
    }
}