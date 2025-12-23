package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;

import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ModuleInfoAction extends UserAction {
    private List<ModuleInfo> moduleInfos;
    private Map<String, Object> filter;
    private List<String> moduleIds;
    @Getter
    @Setter
    private boolean rebootContainer;

    @Override
    public String execute() {
        return SUCCESS;
    }

    private static final int heartbeatThresholdSeconds = 5 * 60; // 5 minutes
    private static final int rebootThresholdSeconds = 60; // 1 minute
    private static final String _DEFAULT_PREFIX_REGEX_STRING = "^Default_";

    public String fetchModuleInfo() {
        List<Bson> filters = new ArrayList<>();

        int deltaTime = Context.now() - heartbeatThresholdSeconds;
        filters.add(Filters.gte(ModuleInfo.LAST_HEARTBEAT_RECEIVED, deltaTime));

        // Apply filter if provided
        if (filter != null && !filter.isEmpty()) {
            if (filter.containsKey(ModuleInfo.MODULE_TYPE)) {
                filters.add(Filters.eq(ModuleInfo.MODULE_TYPE, filter.get(ModuleInfo.MODULE_TYPE)));
            }
            // Add more filter fields as needed
        }

        Bson finalFilter = Filters.and(filters);
        moduleInfos = ModuleInfoDao.instance.findAll(finalFilter);
        return SUCCESS.toUpperCase();
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
                Filters.regex(ModuleInfo.NAME, _DEFAULT_PREFIX_REGEX_STRING)
            );

            // Update reboot flag to true for matching modules
            // Use rebootContainer flag if specified, otherwise use regular reboot flag
            String rebootField = rebootContainer ? ModuleInfo._REBOOT_CONTAINER : ModuleInfo._REBOOT;
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
}