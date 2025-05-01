package com.akto.action.settings;

import com.akto.action.UserAction;
import com.akto.dao.monitoring.ModuleInfoDao;
import com.akto.dto.monitoring.ModuleInfo;
import com.mongodb.BasicDBObject;

import java.util.List;

public class ModuleInfoAction extends UserAction {
    private List<ModuleInfo> moduleInfos;
    @Override
    public String execute() {
        return SUCCESS;
    }

    public String fetchModuleInfo() {
        moduleInfos = ModuleInfoDao.instance.findAll(new BasicDBObject());
        return SUCCESS.toUpperCase();
    }

    public List<ModuleInfo> getModuleInfos() {
        return moduleInfos;
    }

    public void setModuleInfos(List<ModuleInfo> moduleInfos) {
        this.moduleInfos = moduleInfos;
    }
}