package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.akto.dto.monitoring.ModuleInfo.ModuleType;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Arrays;
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
    private static final int rebootThresholdSeconds = 60; // 1 minute
    private static final String _DEFAULT_PREFIX_REGEX_STRING = "^Default_";

    // Whitelist of environment variables that are safe to expose to frontend
    private static final List<String> ALLOWED_ENV_KEYS = Arrays.asList(
        "AKTO_KAFKA_BROKER_MAL",
        "AKTO_KAFKA_BROKER_URL",
        "AKTO_TRAFFIC_BATCH_SIZE",
        "AKTO_TRAFFIC_BATCH_TIME_SECS",
        "AKTO_LOG_LEVEL",
        "DEBUG_URLS",
        "AKTO_K8_METADATA_CAPTURE",
        "AKTO_THREAT_ENABLED",
        "AKTO_IGNORE_ENVOY_PROXY_CALLS",
        "AKTO_IGNORE_IP_TRAFFIC"
    );

    public String fetchModuleInfo() {
        List<Bson> filters = new ArrayList<>();

        boolean isEndpointShield = false;

        // Apply filter if provided
        if (filter != null && !filter.isEmpty()) {
            if (filter.containsKey(ModuleInfo.MODULE_TYPE)) {
                String moduleTypeStr = (String) filter.get(ModuleInfo.MODULE_TYPE);
                if(ModuleType.MCP_ENDPOINT_SHIELD.toString().equals(moduleTypeStr)) {
                    isEndpointShield = true;
                }
                filters.add(Filters.eq(ModuleInfo.MODULE_TYPE, moduleTypeStr));
            }
            // Add more filter fields as needed
        }

        if (!isEndpointShield) {
            int deltaTime = Context.now() - heartbeatThresholdSeconds;
            filters.add(Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, deltaTime));
        }

        Bson finalFilter = filters.isEmpty() ? Filters.empty() : Filters.and(filters);
        moduleInfos = ModuleInfoDao.instance.findAll(finalFilter);

        // Filter environment variables to only expose whitelisted keys
        filterEnvironmentVariables(moduleInfos);

        return SUCCESS.toUpperCase();
    }

    private void filterEnvironmentVariables(List<ModuleInfo> modules) {
        if (modules == null) {
            return;
        }

        for (ModuleInfo module : modules) {
            if (module.getAdditionalData() != null) {
                Map<String, Object> additionalData = module.getAdditionalData();
                Object envObj = additionalData.get("env");

                if (envObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> env = (Map<String, Object>) envObj;

                    // Create filtered env map with only allowed keys
                    Map<String, Object> filteredEnv = new HashMap<>();
                    for (String key : ALLOWED_ENV_KEYS) {
                        if (env.containsKey(key)) {
                            filteredEnv.put(key, env.get(key));
                        }
                    }

                    // Replace env with filtered version
                    additionalData.put("env", filteredEnv);
                }
            }
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
            System.out.println("updateModuleEnvAndReboot: moduleIds are " + moduleIds);
            Bson rebootFilter = Filters.and(
                Filters.in(ModuleInfoDao.ID, moduleIds),
                Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, deltaTimeForReboot),
                Filters.or(
                    Filters.regex(ModuleInfo.NAME, _DEFAULT_PREFIX_REGEX_STRING),
                    Filters.eq(ModuleInfo.MODULE_TYPE, ModuleType.TRAFFIC_COLLECTOR.toString())
                )
            );
            System.out.println("final filter will be : " + rebootFilter);

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

        try {
            int deltaTimeForReboot = Context.now() - rebootThresholdSeconds;


            Bson moduleFilter = Filters.and(
                Filters.eq(ModuleInfo.NAME, moduleName),
                Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, deltaTimeForReboot),
                Filters.ne(ModuleInfo.ADDITIONAL_DATA, null)
            );


            List<Bson> updates = new ArrayList<>();


            if (envData != null && !envData.isEmpty()) {
                // Update each environment variable individually to preserve other env vars
                // Only allow whitelisted keys for security
                for (Map.Entry<String, String> entry : envData.entrySet()) {
                    if (ALLOWED_ENV_KEYS.contains(entry.getKey())) {
                        updates.add(Updates.set(ModuleInfo.ADDITIONAL_DATA + ".env." + entry.getKey(), entry.getValue()));
                    } else {
                        System.out.println("Rejected attempt to update non-whitelisted env key: " + entry.getKey());
                    }
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