package com.akto.action;

import com.akto.dao.BrowserExtensionConfigDao;
import com.akto.dao.BrowserExtensionConfigCommonDao;
import com.akto.dao.context.Context;
import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.BrowserExtensionConfigCommon;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;

import lombok.Getter;
import lombok.Setter;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BrowserExtensionConfigAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(BrowserExtensionConfigAction.class, LogDb.DASHBOARD);

    // When enabling a supported host we copy the WHOLE catalogue document into the account row, so any
    // extraction field - present or added in future - flows through with no code change. This set is not
    // that allow-list; it is only the fields the account row owns and that this method sets itself.
    // Copying them would either be illegal (`_id` cannot be $set) or clash with the explicit $set below
    // ("update path twice" error) - and `active` must come from the toggle, not the catalogue.
    private static final Set<String> ACCOUNT_OWNED_FIELDS = new HashSet<>(Arrays.asList(
        "_id", BrowserExtensionConfig.HOST, BrowserExtensionConfig.ACTIVE,
        BrowserExtensionConfig.CREATED_BY, BrowserExtensionConfig.UPDATED_BY,
        BrowserExtensionConfig.CREATED_TIMESTAMP, BrowserExtensionConfig.UPDATED_TIMESTAMP
    ));

    // BrowserExtensionConfigCommon stores these as *either* a bare string or a list (whichever the
    // catalogue author wrote); BrowserExtensionConfig (the account row) is decoded through the strict
    // Mongo pojo codec and declares all three as List<String> only. Copying a bare-string catalogue
    // value straight through corrupts the account doc - every later read throws
    // "readStartArray can only be called when CurrentBSONType is ARRAY, not ... STRING" - so these are
    // normalized to a singleton list on the way in.
    private static final Set<String> LIST_ONLY_FIELDS = new HashSet<>(Arrays.asList(
        BrowserExtensionConfig.PATH, BrowserExtensionConfig.RESPONSE_PATH, BrowserExtensionConfig.MODEL_PATH
    ));

    @Getter
    @Setter
    private BrowserExtensionConfig browserExtensionConfig;

    @Setter
    private String hexId;

    @Getter
    private List<BrowserExtensionConfig> browserExtensionConfigs;

    @Getter
    private List<BrowserExtensionConfigCommon> browserExtensionConfigsCommon;

    @Setter
    private List<String> configIds;

    // hosts targeted by setBrowserExtensionConfigsActive; the active flag comes from
    // browserExtensionConfig, same as the single-host toggle
    @Setter
    private List<String> hosts;

    public String fetchBrowserExtensionConfigs() {
        try {
            this.browserExtensionConfigs = BrowserExtensionConfigDao.instance.findAllConfigs();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching browser extension configs: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to fetch browser extension configs");
            return ERROR.toUpperCase();
        }
    }

    // Reads the shared list of supported browser extension configs from the common DB.
    public String fetchBrowserExtensionConfigsCommon() {
        try {
            this.browserExtensionConfigsCommon = BrowserExtensionConfigCommonDao.instance.findAllConfigs();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching common browser extension configs: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to fetch common browser extension configs");
            return ERROR.toUpperCase();
        }
    }

    // Enables/disables a host for this account by upserting an account-level override row keyed by host.
    // Unlike saveBrowserExtensionConfig this needs no paths, since toggling a supported (common) host is
    // only about the active flag - the paths come from the common catalogue.
    public String setBrowserExtensionConfigActive() {
        try {
            if (browserExtensionConfig == null
                    || browserExtensionConfig.getHost() == null
                    || browserExtensionConfig.getHost().trim().isEmpty()) {
                addActionError("Host is required");
                return ERROR.toUpperCase();
            }

            User user = getSUser();
            int currentTime = Context.now();
            String host = browserExtensionConfig.getHost().trim();

            List<Bson> updates = buildActiveUpdates(host, browserExtensionConfig.isActive(), user, currentTime);

            BrowserExtensionConfigDao.instance.getMCollection().updateOne(
                Filters.eq(BrowserExtensionConfig.HOST, host),
                Updates.combine(updates),
                new UpdateOptions().upsert(true)
            );

            loggerMaker.info("Set browser extension config active=" + browserExtensionConfig.isActive()
                + " for host: " + host + " by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error toggling browser extension config: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to update browser extension config");
            return ERROR.toUpperCase();
        }
    }

    // Enables/disables many hosts for this account in one round trip - same semantics as
    // setBrowserExtensionConfigActive, applied per host in an unordered bulk write so one bad
    // host can't block the rest.
    public String setBrowserExtensionConfigsActive() {
        try {
            if (hosts == null || hosts.isEmpty()) {
                addActionError("At least one host is required");
                return ERROR.toUpperCase();
            }
            if (browserExtensionConfig == null) {
                addActionError("Active flag is required");
                return ERROR.toUpperCase();
            }

            User user = getSUser();
            int currentTime = Context.now();
            boolean active = browserExtensionConfig.isActive();

            Set<String> seen = new HashSet<>();
            List<WriteModel<BrowserExtensionConfig>> writeModels = new ArrayList<>();
            for (String rawHost : hosts) {
                if (rawHost == null || rawHost.trim().isEmpty()) {
                    continue;
                }
                String host = rawHost.trim();
                if (!seen.add(host.toLowerCase())) {
                    continue;   // same host given twice - one write per host
                }

                List<Bson> updates = buildActiveUpdates(host, active, user, currentTime);
                writeModels.add(new UpdateOneModel<BrowserExtensionConfig>(
                    Filters.eq(BrowserExtensionConfig.HOST, host),
                    Updates.combine(updates),
                    new UpdateOptions().upsert(true)
                ));
            }

            if (writeModels.isEmpty()) {
                addActionError("At least one host is required");
                return ERROR.toUpperCase();
            }

            BrowserExtensionConfigDao.instance.getMCollection().bulkWrite(writeModels, new BulkWriteOptions().ordered(false));

            loggerMaker.info("Set browser extension config active=" + active
                + " for " + writeModels.size() + " host(s) by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error bulk toggling browser extension configs: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to update browser extension configs");
            return ERROR.toUpperCase();
        }
    }

    // Enabling a supported host copies its full extraction spec (transport, format, prompt path,
    // response/model paths, …) from the common catalogue into the account row, so the extension
    // gets a config-driven entry rather than a bare host+paths one. Falls back to an empty paths
    // list on insert when the host is not in the catalogue.
    private List<Bson> buildActiveUpdates(String host, boolean active, User user, int currentTime) {
        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(BrowserExtensionConfig.ACTIVE, active));
        updates.add(Updates.set(BrowserExtensionConfig.UPDATED_TIMESTAMP, currentTime));
        updates.add(Updates.set(BrowserExtensionConfig.UPDATED_BY, user.getLogin()));
        updates.add(Updates.setOnInsert(BrowserExtensionConfig.CREATED_BY, user.getLogin()));
        updates.add(Updates.setOnInsert(BrowserExtensionConfig.CREATED_TIMESTAMP, currentTime));

        boolean pathsSet = false;
        if (active) {
            Document commonDoc = BrowserExtensionConfigCommonDao.instance.findRawByHost(host);
            if (commonDoc != null) {
                for (String field : commonDoc.keySet()) {
                    if (ACCOUNT_OWNED_FIELDS.contains(field)) {
                        continue;
                    }
                    Object value = commonDoc.get(field);
                    if (value != null) {
                        if (LIST_ONLY_FIELDS.contains(field) && value instanceof String) {
                            value = Collections.singletonList(value);
                        }
                        updates.add(Updates.set(field, value));
                        if (BrowserExtensionConfig.PATHS.equals(field)) {
                            pathsSet = true;
                        }
                    }
                }
            }
        }
        if (!pathsSet) {
            updates.add(Updates.setOnInsert(BrowserExtensionConfig.PATHS, new ArrayList<String>()));
        }
        return updates;
    }

    public String saveBrowserExtensionConfig() {
        try {
            if (browserExtensionConfig == null) {
                addActionError("Browser extension config is required");
                return ERROR.toUpperCase();
            }

            if (browserExtensionConfig.getHost() == null || browserExtensionConfig.getHost().trim().isEmpty()) {
                addActionError("Host is required");
                return ERROR.toUpperCase();
            }

            if (browserExtensionConfig.getPaths() == null || browserExtensionConfig.getPaths().isEmpty()) {
                addActionError("At least one path is required");
                return ERROR.toUpperCase();
            }

            User user = getSUser();
            int currentTime = Context.now();

            Bson filter;
            if (hexId != null && !hexId.isEmpty()) {
                filter = Filters.eq(Constants.ID, new ObjectId(hexId));
            } else {
                filter = Filters.eq(BrowserExtensionConfig.HOST, browserExtensionConfig.getHost());
            }

            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set(BrowserExtensionConfig.HOST, browserExtensionConfig.getHost().trim()));
            updates.add(Updates.set(BrowserExtensionConfig.PATHS, browserExtensionConfig.getPaths()));
            updates.add(Updates.set(BrowserExtensionConfig.ACTIVE, browserExtensionConfig.isActive()));
            updates.add(Updates.set(BrowserExtensionConfig.UPDATED_TIMESTAMP, currentTime));
            updates.add(Updates.set(BrowserExtensionConfig.UPDATED_BY, user.getLogin()));
            updates.add(Updates.setOnInsert(BrowserExtensionConfig.CREATED_BY, user.getLogin()));
            updates.add(Updates.setOnInsert(BrowserExtensionConfig.CREATED_TIMESTAMP, currentTime));

            // config-driven monitoring fields — set only what the client sent, so a plain
            // host+paths config stays minimal and a rich one carries its full extraction spec.
            addIfPresent(updates, BrowserExtensionConfig.TRANSPORT, emptyToNull(browserExtensionConfig.getTransport()));
            addIfPresent(updates, BrowserExtensionConfig.METHOD, emptyToNull(browserExtensionConfig.getMethod()));
            addIfPresent(updates, BrowserExtensionConfig.FORMAT, emptyToNull(browserExtensionConfig.getFormat()));
            addIfPresent(updates, BrowserExtensionConfig.PATH, nonEmpty(browserExtensionConfig.getPath()));
            addIfPresent(updates, BrowserExtensionConfig.OPERATIONS, nonEmpty(browserExtensionConfig.getOperations()));
            addIfPresent(updates, BrowserExtensionConfig.FRAME_MATCH,
                (browserExtensionConfig.getFrameMatch() != null && !browserExtensionConfig.getFrameMatch().isEmpty()) ? browserExtensionConfig.getFrameMatch() : null);
            addIfPresent(updates, BrowserExtensionConfig.RESPONSE_FORMAT, emptyToNull(browserExtensionConfig.getResponseFormat()));
            addIfPresent(updates, BrowserExtensionConfig.RESPONSE_PATH, nonEmpty(browserExtensionConfig.getResponsePath()));
            addIfPresent(updates, BrowserExtensionConfig.MODEL_PATH, nonEmpty(browserExtensionConfig.getModelPath()));

            BrowserExtensionConfigDao.instance.getMCollection().updateOne(
                filter,
                Updates.combine(updates),
                new UpdateOptions().upsert(true)
            );

            String action = (hexId != null && !hexId.isEmpty()) ? "Updated" : "Created";
            loggerMaker.info(action + " browser extension config for host: " + browserExtensionConfig.getHost() + " by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error saving browser extension config: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to save browser extension config");
            return ERROR.toUpperCase();
        }
    }

    public String deleteBrowserExtensionConfigs() {
        try {
            if (configIds == null || configIds.isEmpty()) {
                addActionError("No config IDs provided for deletion");
                return ERROR.toUpperCase();
            }

            User user = getSUser();
            List<ObjectId> objectIds = new ArrayList<>();
            for (String id : configIds) {
                objectIds.add(new ObjectId(id));
            }

            Bson filter = Filters.in(Constants.ID, objectIds);
            BrowserExtensionConfigDao.instance.getMCollection().deleteMany(filter);

            loggerMaker.info("Deleted " + configIds.size() + " browser extension config(s) by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error deleting browser extension configs: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to delete browser extension configs");
            return ERROR.toUpperCase();
        }
    }

    private static void addIfPresent(List<Bson> updates, String field, Object value) {
        if (value != null) {
            updates.add(Updates.set(field, value));
        }
    }

    private static String emptyToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    private static <T> List<T> nonEmpty(List<T> list) {
        return (list == null || list.isEmpty()) ? null : list;
    }
}
