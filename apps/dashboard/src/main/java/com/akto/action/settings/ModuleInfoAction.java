package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.akto.dto.monitoring.ModuleInfoConstants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Override
    public String execute() {
        return SUCCESS;
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

    private String getFieldType(String key) {
        if (key.equals("AKTO_IGNORE_ENVOY_PROXY_CALLS") ||
            key.equals("AKTO_IGNORE_IP_TRAFFIC") ||
            key.equals("AKTO_K8_METADATA_CAPTURE") ||
            key.equals("AKTO_THREAT_ENABLED") ||
            key.equals("AGGREGATION_RULES_ENABLED")) {
            return "boolean";
        }
        return "text";
    }

    private void filterEnvironmentVariables(List<ModuleInfo> modules) {
        if (modules == null) {
            return;
        }

        for (ModuleInfo module : modules) {
            if (module.getAdditionalData() == null) {
                continue;
            }

            Map<String, Object> additionalData = module.getAdditionalData();
            Object envObj = additionalData.get("env");

            if (!(envObj instanceof Map)) {
                continue;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> env = (Map<String, Object>) envObj;

            // Create filtered env map with only allowed keys for the module's type
            Map<String, Object> filteredEnv = new HashMap<>();
            ModuleType moduleType = module.getModuleType();

            Map<String, String> allowedKeys = ModuleInfoConstants.ALLOWED_ENV_KEYS_BY_MODULE.get(moduleType);
            if (allowedKeys != null) {
                for (String key : allowedKeys.keySet()) {
                    if (env.containsKey(key)) {
                        filteredEnv.put(key, env.get(key));
                    }
                }
            }

            // Replace env with filtered version
            additionalData.put("env", filteredEnv);
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

    public String updateModuleEnvAndReboot() {
        if (moduleName == null || moduleName.isEmpty()) {
            return ERROR.toUpperCase();
        }

        if (envData == null || envData.isEmpty()) {
            return SUCCESS.toUpperCase();
        }

        try {
            int deltaTimeForReboot = Context.now() - rebootThresholdSeconds;


            Bson moduleFilter = Filters.and(
                Filters.eq(ModuleInfo.NAME, moduleName),
                Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, deltaTimeForReboot),
                Filters.ne(ModuleInfo.ADDITIONAL_DATA, null)
            );


            List<Bson> updates = new ArrayList<>();

            // Update each environment variable individually to preserve other env vars
            // Only allow whitelisted keys for security
            for (Map.Entry<String, String> entry : envData.entrySet()) {
                boolean isAllowedKey = ModuleInfoConstants.ALLOWED_ENV_KEYS_BY_MODULE.values().stream()
                    .anyMatch(moduleEnvMap -> moduleEnvMap.containsKey(entry.getKey()));

                if (isAllowedKey) {
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