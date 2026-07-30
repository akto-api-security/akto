package com.akto.action;

import com.akto.dao.BrowserExtensionConfigDao;
import com.akto.dao.BrowserExtensionConfigCommonDao;
import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.BrowserExtensionConfigCommon;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;

import java.util.List;

public class BrowserExtensionConfigAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(BrowserExtensionConfigAction.class, LogDb.DB_ABS);

    @Getter
    private List<BrowserExtensionConfig> browserExtensionConfigs;

    @Getter
    private List<BrowserExtensionConfigCommon> browserExtensionConfigsCommon;

    public String fetchBrowserExtensionConfigs() {
        try {
            this.browserExtensionConfigs = BrowserExtensionConfigDao.instance.findActiveConfigs();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching browser extension configs: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to fetch browser extension configs");
            return ERROR.toUpperCase();
        }
    }

    public String fetchBrowserExtensionConfigsCommon() {
        try {
            this.browserExtensionConfigsCommon = BrowserExtensionConfigCommonDao.instance.findActiveConfigs();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching common browser extension configs: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to fetch common browser extension configs");
            return ERROR.toUpperCase();
        }
    }
}
