package com.akto.action;

import com.akto.dao.BrowserExtensionConfigDao;
import com.akto.dao.context.Context;
import com.akto.dto.BrowserExtensionConfig;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;

import lombok.Getter;
import lombok.Setter;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class BrowserExtensionConfigAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(BrowserExtensionConfigAction.class, LogDb.DASHBOARD);

    @Getter
    @Setter
    private BrowserExtensionConfig browserExtensionConfig;

    @Setter
    private String hexId;

    @Getter
    private List<BrowserExtensionConfig> browserExtensionConfigs;

    @Setter
    private List<String> configIds;

    public String fetchBrowserExtensionConfigs() {
        try {
            this.browserExtensionConfigs = BrowserExtensionConfigDao.instance.findAllSortedByCreatedTimestamp(0, 100);
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching browser extension configs: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to fetch browser extension configs");
            return ERROR.toUpperCase();
        }
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
}
