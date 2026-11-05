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

    // v3 request:
    //  mode=FULL   (cursor, limit optional): the whole catalogue, paged. cursor = the nextCursor of the
    //              previous page (none on the first), opaque to the client.
    //  mode=DELTA  (updatedAfter required): everything newer than updatedAfter, one response.
    // No mode = an older client: updatedAfter > 0 is a delta, otherwise a full sync.
    @Setter
    @Getter   // echoed back as the mode actually served
    private String mode;
    @Setter
    private long updatedAfter;
    @Setter
    private String cursor;
    @Setter
    private int limit;   // page size for a full sync; capped server side

    // v3 response: mode (above), the configs, and for a full sync the cursor of the next page (null on the last)
    @Getter
    private List<BrowserExtensionConfigCommon> browserExtensionConfigsV3;
    @Getter
    private String nextCursor;

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
        String invalid = BrowserExtensionConfigDao.SyncMode.validate(mode, updatedAfter, cursor, limit);
        if (invalid != null) {
            addActionError(invalid);
            return ERROR.toUpperCase();
        }
        try {
            BrowserExtensionConfigDao.SyncMode syncMode = BrowserExtensionConfigDao.SyncMode.resolve(mode, updatedAfter);
            BrowserExtensionConfigDao.ConfigsV3 result = syncMode == BrowserExtensionConfigDao.SyncMode.DELTA
                    ? BrowserExtensionConfigDao.instance.findDelta(updatedAfter)
                    : BrowserExtensionConfigDao.instance.findFullSyncPage(cursor, limit);
            this.mode = syncMode.name();
            this.browserExtensionConfigsV3 = result.getConfigs();
            this.nextCursor = result.getNextCursor();
            return SUCCESS.toUpperCase();
        } catch (IllegalArgumentException e) {
            addActionError(e.getMessage());
            return ERROR.toUpperCase();
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
