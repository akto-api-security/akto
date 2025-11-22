package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.BasicDBObject;

import java.util.List;
import java.util.Map;

public class ModuleInfoAction extends UserAction {
    private List<ModuleInfo> moduleInfos;
    private Map<String, Object> filter;
    private List<String> moduleIds;

    @Override
    public String execute() {
        return SUCCESS;
    }

    public String fetchModuleInfo() {
        BasicDBObject query = new BasicDBObject();

        // Apply filter if provided
        if (filter != null && !filter.isEmpty()) {
            if (filter.containsKey(ModuleInfo.MODULE_TYPE)) {
                query.put(ModuleInfo.MODULE_TYPE, filter.get(ModuleInfo.MODULE_TYPE));
            }
            // Add more filter fields as needed
        }

        moduleInfos = ModuleInfoDao.instance.findAll(query);
        return SUCCESS.toUpperCase();
    }

    public String deleteModuleInfo() {
        if (moduleIds == null || moduleIds.isEmpty()) {
            return ERROR.toUpperCase();
        }

        // Delete modules by their IDs
        BasicDBObject query = new BasicDBObject();
        query.put(ModuleInfoDao.ID, new BasicDBObject("$in", moduleIds));
        ModuleInfoDao.instance.deleteAll(query);

        return SUCCESS.toUpperCase();
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