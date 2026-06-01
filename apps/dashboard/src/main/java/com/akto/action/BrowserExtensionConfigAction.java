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
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
            // Returns both MONITOR configs and BLOCK rules; the caller splits by type.
            this.browserExtensionConfigs = BrowserExtensionConfigDao.instance.findAllSortedByCreatedTimestamp(0, 500);
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

            String type = normalizeType(browserExtensionConfig.getType());
            if (BrowserExtensionConfig.TYPE_BLOCK.equals(type)) {
                return saveBlockRule();
            }
            return saveMonitorConfig();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error saving browser extension config: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Failed to save browser extension config");
            return ERROR.toUpperCase();
        }
    }

    private String saveMonitorConfig() {
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
            filter = Filters.and(
                Filters.eq(BrowserExtensionConfig.TYPE, BrowserExtensionConfig.TYPE_MONITOR),
                Filters.eq(BrowserExtensionConfig.HOST, browserExtensionConfig.getHost().trim()));
        }

        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(BrowserExtensionConfig.TYPE, BrowserExtensionConfig.TYPE_MONITOR));
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

        String op = (hexId != null && !hexId.isEmpty()) ? "Updated" : "Created";
        loggerMaker.info(op + " browser extension config for host: " + browserExtensionConfig.getHost() + " by user: " + user.getLogin());
        return SUCCESS.toUpperCase();
    }

    private String saveBlockRule() {
        String matchType = normalizeMatchType(browserExtensionConfig.getMatchType());
        if (matchType == null) {
            addActionError("Invalid match type. Allowed: DOMAIN, HOST, URL_PATTERN, REGEX");
            return ERROR.toUpperCase();
        }

        String action = normalizeAction(browserExtensionConfig.getAction());
        if (action == null) {
            addActionError("Invalid action. Allowed: block, allow");
            return ERROR.toUpperCase();
        }

        String normalizedValue = validateAndNormalizeValue(matchType, browserExtensionConfig.getValue());
        if (normalizedValue == null) {
            // validateAndNormalizeValue already added the specific action error
            return ERROR.toUpperCase();
        }

        User user = getSUser();
        int currentTime = Context.now();

        Bson filter = (hexId != null && !hexId.isEmpty())
            ? Filters.eq(Constants.ID, new ObjectId(hexId))
            : Filters.and(
                Filters.eq(BrowserExtensionConfig.TYPE, BrowserExtensionConfig.TYPE_BLOCK),
                Filters.eq(BrowserExtensionConfig.MATCH_TYPE, matchType),
                Filters.eq(BrowserExtensionConfig.VALUE, normalizedValue));

        List<Bson> updates = new ArrayList<>();
        updates.add(Updates.set(BrowserExtensionConfig.TYPE, BrowserExtensionConfig.TYPE_BLOCK));
        updates.add(Updates.set(BrowserExtensionConfig.MATCH_TYPE, matchType));
        updates.add(Updates.set(BrowserExtensionConfig.VALUE, normalizedValue));
        updates.add(Updates.set(BrowserExtensionConfig.ACTION, action));
        updates.add(Updates.set(BrowserExtensionConfig.ACTIVE, browserExtensionConfig.isActive()));
        updates.add(Updates.set(BrowserExtensionConfig.DESCRIPTION,
                browserExtensionConfig.getDescription() == null ? "" : browserExtensionConfig.getDescription().trim()));
        updates.add(Updates.set(BrowserExtensionConfig.UPDATED_TIMESTAMP, currentTime));
        updates.add(Updates.set(BrowserExtensionConfig.UPDATED_BY, user.getLogin()));
        updates.add(Updates.setOnInsert(BrowserExtensionConfig.CREATED_BY, user.getLogin()));
        updates.add(Updates.setOnInsert(BrowserExtensionConfig.CREATED_TIMESTAMP, currentTime));

        BrowserExtensionConfigDao.instance.getMCollection().updateOne(
            filter,
            Updates.combine(updates),
            new UpdateOptions().upsert(true)
        );

        String op = (hexId != null && !hexId.isEmpty()) ? "Updated" : "Created";
        loggerMaker.info(op + " browser extension block rule " + matchType + ":" + normalizedValue + " by user: " + user.getLogin());
        return SUCCESS.toUpperCase();
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

            // Works for both MONITOR configs and BLOCK rules (deletes by id).
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

    private static String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return BrowserExtensionConfig.TYPE_MONITOR; // backward compatibility
        }
        String t = type.trim().toUpperCase();
        return BrowserExtensionConfig.TYPE_BLOCK.equals(t) ? BrowserExtensionConfig.TYPE_BLOCK : BrowserExtensionConfig.TYPE_MONITOR;
    }

    private static String normalizeMatchType(String matchType) {
        if (matchType == null) return null;
        String mt = matchType.trim().toUpperCase();
        switch (mt) {
            case BrowserExtensionConfig.MATCH_TYPE_DOMAIN:
            case BrowserExtensionConfig.MATCH_TYPE_HOST:
            case BrowserExtensionConfig.MATCH_TYPE_URL_PATTERN:
            case BrowserExtensionConfig.MATCH_TYPE_REGEX:
                return mt;
            default:
                return null;
        }
    }

    private static String normalizeAction(String action) {
        if (action == null || action.trim().isEmpty()) {
            return BrowserExtensionConfig.ACTION_BLOCK; // default
        }
        String a = action.trim().toLowerCase();
        if (BrowserExtensionConfig.ACTION_BLOCK.equals(a) || BrowserExtensionConfig.ACTION_ALLOW.equals(a)) {
            return a;
        }
        return null;
    }

    /**
     * Validates and normalizes a block-rule value by match type.
     * Returns the normalized value, or null after adding an action error on failure.
     */
    private String validateAndNormalizeValue(String matchType, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            addActionError("Rule value is required");
            return null;
        }

        switch (matchType) {
            case BrowserExtensionConfig.MATCH_TYPE_DOMAIN:
            case BrowserExtensionConfig.MATCH_TYPE_HOST: {
                // Strip scheme, leading www., path/query, trailing dot and port.
                String host = value.toLowerCase();
                host = host.replaceFirst("^[a-z]+://", "");
                int slash = host.indexOf('/');
                if (slash >= 0) host = host.substring(0, slash);
                int colon = host.indexOf(':');
                if (colon >= 0) host = host.substring(0, colon);
                if (host.startsWith("www.")) host = host.substring(4);
                if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
                // Must look like a hostname: labels separated by dots, no spaces, at least one dot.
                if (host.isEmpty() || host.contains(" ") || !host.contains(".")
                        || !host.matches("[a-z0-9.-]+")) {
                    addActionError("Enter a valid domain or host, e.g. deepseek.com or chat.deepseek.com");
                    return null;
                }
                return host;
            }
            case BrowserExtensionConfig.MATCH_TYPE_URL_PATTERN: {
                if (value.contains(" ")) {
                    addActionError("URL pattern cannot contain spaces");
                    return null;
                }
                // A bare "*" would block everything - reject as too broad.
                if (value.equals("*") || value.equals("*://*")) {
                    addActionError("URL pattern is too broad");
                    return null;
                }
                return value;
            }
            case BrowserExtensionConfig.MATCH_TYPE_REGEX: {
                try {
                    Pattern.compile(value);
                } catch (PatternSyntaxException e) {
                    addActionError("Invalid regular expression: " + e.getDescription());
                    return null;
                }
                // Catch-all patterns that would block every URL.
                String stripped = value.replaceAll("[\\^$]", "");
                if (stripped.equals(".*") || stripped.equals(".+") || stripped.equals("(.*)") || stripped.equals("(.+)")) {
                    addActionError("Regex is too broad - it would block every URL");
                    return null;
                }
                return value;
            }
            default:
                addActionError("Invalid match type");
                return null;
        }
    }
}
