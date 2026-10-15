package com.akto.action;

import com.akto.dao.BrowserInterceptConfigDao;
import com.akto.dto.BrowserInterceptConfigs;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.opensymphony.xwork2.ActionSupport;
import lombok.Getter;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves a tenant's v2 per-site interception catalogue (api/fetchBrowserInterceptConfigs) as a
 * timestamp delta from its OWN account DB: every tenant owns and fetches only its own configs - no
 * shared catalogue, no cross-account merge.
 *
 * {@code syncedUpTo} is one cursor used both ways: the client sends the timestamp it is already
 * synced up to, and gets back only the configs changed after it plus the NEW {@code syncedUpTo} to
 * store for next time. {@code syncedUpTo <= 0} returns the whole catalogue ({@code fullSync=true}) -
 * the first fetch, or the client's periodic full re-sync that reconciles deletions.
 *
 * Wire contract matches the extension's InterceptConfigsClient: request { engineVersion, capabilities
 * (flat string[]), syncedUpTo }, response { configs, syncedUpTo, fullSync, refreshSeconds,
 * unsupported, source }. The request body is bound by the json interceptor.
 */
public class BrowserInterceptConfigAction extends ActionSupport {

    private static final LoggerMaker loggerMaker = new LoggerMaker(BrowserInterceptConfigAction.class, LogDb.DB_ABS);

    private static final int REFRESH_SECONDS = 900; // 15 minutes

    // ---- request (set by the json interceptor) ----
    private String engineVersion;
    private List<String> capabilities;
    // one cursor, both ways: IN = the timestamp the client is already synced up to; OUT (getter
    // below) = the new high-water mark the client should store and send back next time.
    private long syncedUpTo;

    public void setEngineVersion(String engineVersion) { this.engineVersion = engineVersion; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }
    public void setSyncedUpTo(long syncedUpTo) { this.syncedUpTo = syncedUpTo; }
    public long getSyncedUpTo() { return syncedUpTo; }

    // ---- response ----
    // configs: the changed configs (whole catalogue when fullSync==true, else only those after syncedUpTo)
    @Getter private List<Document> configs = new ArrayList<>();
    // fullSync: true when this response is the whole catalogue, so the client replaces rather than merges
    @Getter private boolean fullSync;
    @Getter private int refreshSeconds = REFRESH_SECONDS;
    @Getter private List<String> unsupported = new ArrayList<>();
    @Getter private String source = "db";

    public String fetchBrowserInterceptConfigs() {
        try {
            // the timestamp the client already has; capture it before overwriting syncedUpTo for the response
            long clientSyncedUpTo = this.syncedUpTo;

            // this tenant's own configs only (account DB; account id comes from the authenticated token)
            List<Document> tenantConfigs = BrowserInterceptConfigDao.instance.findActive();

            List<String> unsupportedIds = new ArrayList<>();
            List<Document> supported = BrowserInterceptConfigs.filterByCapabilities(tenantConfigs, capabilities, unsupportedIds);
            this.unsupported = unsupportedIds;

            this.fullSync = clientSyncedUpTo <= 0;
            this.configs = BrowserInterceptConfigs.changedSince(supported, clientSyncedUpTo);
            // new cursor for the client to store: the newest updatedTimestamp over the set it can use,
            // so it never skips a config it couldn't hold yet.
            this.syncedUpTo = BrowserInterceptConfigs.maxUpdatedTimestamp(supported);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching browser intercept configs: " + e.getMessage(), LogDb.DB_ABS);
            addActionError("Failed to fetch browser intercept configs");
            return ERROR.toUpperCase();
        }
    }
}
