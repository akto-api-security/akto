package com.akto.action;

import com.akto.dao.BrowserExtensionConfigDao;
import com.akto.dao.BrowserExtensionConfigCommonDao;
import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.BrowserExtensionConfigCommon;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class BrowserExtensionConfigAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(BrowserExtensionConfigAction.class, LogDb.DB_ABS);

    @Getter
    private List<BrowserExtensionConfig> browserExtensionConfigs;

    @Getter
    private List<BrowserExtensionConfigCommon> browserExtensionConfigsCommon;

    // v2 returns the rich, per-site config shape the browser extension supports
    // (transport/format/path/response*/model*/frameMatch/...), typed via the shared mapper.
    @Getter
    private List<BrowserExtensionConfigCommon> browserExtensionConfigsV2;

    // v3 request: updatedAfter = the newest updatedAt the client holds (delta, one response). Omit it
    // for a full sync, which is paged: afterId = the nextAfterId of the previous page (none on the first)
    @Setter
    private long updatedAfter;
    @Setter
    private String afterId;
    @Setter
    private int limit;   // optional page size for a full sync; capped server side

    // v3 response: the configs, and for a full sync the afterId of the next page (null on the last)
    @Getter
    private List<BrowserExtensionConfigCommon> browserExtensionConfigsV3;
    @Getter
    private String nextAfterId;

    public String fetchBrowserExtensionConfigs() {
        try {
            this.browserExtensionConfigs = BrowserExtensionConfigDao.instance.findActiveConfigs();
            // Also surface the v2 merged catalogue so clients pinned to this endpoint can read the
            // richer per-site configs without switching to api/fetchBrowserExtensionConfigsV2.
            this.browserExtensionConfigsV2 = BrowserExtensionConfigDao.instance.findActiveConfigsV2();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching browser extension configs: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to fetch browser extension configs");
            return ERROR.toUpperCase();
        }
    }

    public String fetchBrowserExtensionConfigsV2() {
        try {
            this.browserExtensionConfigsV2 = BrowserExtensionConfigDao.instance.findActiveConfigsV2();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching browser extension configs v2: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to fetch browser extension configs");
            return ERROR.toUpperCase();
        }
    }

    public String fetchBrowserExtensionConfigsV3() {
        try {
            BrowserExtensionConfigDao.ConfigsV3 result = BrowserExtensionConfigDao.instance.findConfigsV3(updatedAfter, afterId, limit);
            this.browserExtensionConfigsV3 = result.getConfigs();
            this.nextAfterId = result.getNextAfterId();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching browser extension configs v3: " + e.getMessage(), LogDb.DB_ABS);
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
