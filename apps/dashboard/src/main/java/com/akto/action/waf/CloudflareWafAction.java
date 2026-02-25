package com.akto.action.waf;

import com.akto.action.UserAction;
import com.akto.audit_logs_util.Audit;
import com.akto.dao.ConfigsDao;
import com.akto.dao.context.Context;
import com.akto.dto.Config;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.audit_logs.Operation;
import com.akto.dto.audit_logs.Resource;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.testing.ApiExecutor;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;

import java.util.*;

public class CloudflareWafAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(CloudflareWafAction.class, LogDb.DASHBOARD);
    public static final String CLOUDFLARE_BASE_URL = "https://api.cloudflare.com/client/v4";
    private static final String WAF_ENTRYPOINT = "/rulesets/phases/http_request_firewall_custom/entrypoint";

    // --- Input fields from frontend ---
    private String apiKey;
    private String email;
    private String integrationType;   // "accounts" or "zones"
    private String accountOrZoneId;   // Cloudflare account ID (always required for lists)
    private String zoneId;            // Cloudflare zone ID (required when integrationType = "zones")
    private List<String> severityLevels;

    private Config.CloudflareWafConfig cloudflareWafConfig;

    @Audit(description = "User added cloudflare waf integration", resource = Resource.CONFIGS, operation = Operation.CREATE)
    public String addCloudflareWafIntegration() {
        if (accountOrZoneId == null || accountOrZoneId.isEmpty()) {
            addActionError("Please provide a valid Account ID.");
            return ERROR.toUpperCase();
        }
        boolean isZoneLevel = "zones".equalsIgnoreCase(integrationType);
        if (isZoneLevel && (zoneId == null || zoneId.isEmpty())) {
            addActionError("Please provide a valid Zone ID.");
            return ERROR.toUpperCase();
        }

        int accId = Context.accountId.get();
        Bson filters = Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        Config.CloudflareWafConfig existingConfig = (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(filters);

        if (existingConfig == null && (apiKey == null || apiKey.isEmpty())) {
            addActionError("Please provide a valid API Key.");
            return ERROR.toUpperCase();
        }
        if (existingConfig != null) {
            setApiKey(existingConfig.getApiKey());
        }

        Config.CloudflareWafConfig config = new Config.CloudflareWafConfig(apiKey, email, integrationType, accountOrZoneId, accId, severityLevels);
        if (isZoneLevel) {
            config.setZoneId(zoneId);
        }

        // 1. Validate credentials
        String error = validateCredentials(config);
        if (error != null) {
            addActionError(error);
            return ERROR.toUpperCase();
        }

        // 2. Find or create IP list (account-level)
        List<String> listIds;
        if (existingConfig != null && existingConfig.getListIds() != null && !existingConfig.getListIds().isEmpty()) {
            listIds = existingConfig.getListIds();
        } else {
            String listId = findOrCreateIpList(config, "akto_blocked_ips_" + accId);
            if (listId == null) {
                return ERROR.toUpperCase();
            }
            listIds = new ArrayList<>();
            listIds.add(listId);
        }

        // 3. Find or create WAF rule
        String ruleId;
        if (existingConfig != null && existingConfig.getRuleId() != null) {
            ruleId = existingConfig.getRuleId();
        } else {
            ruleId = createWafRule(config, buildListNames(accId, listIds.size()));
            if (ruleId == null) {
                return ERROR.toUpperCase();
            }
        }

        config.setListIds(listIds);
        config.setRuleId(ruleId);

        // 4. Save
        if (existingConfig != null) {
            Bson updates = Updates.combine(
                    Updates.set(Config.CloudflareWafConfig.ACCOUNT_OR_ZONE_ID, accountOrZoneId),
                    Updates.set(Config.CloudflareWafConfig.INTEGRATION_TYPE, integrationType),
                    Updates.set(Config.CloudflareWafConfig.EMAIL, email),
                    Updates.set(Config.CloudflareWafConfig.SEVERITY_LEVELS, severityLevels),
                    Updates.set(Config.CloudflareWafConfig.LIST_IDS, listIds),
                    Updates.set(Config.CloudflareWafConfig.RULE_ID, ruleId),
                    Updates.set(Config.CloudflareWafConfig.ZONE_ID, zoneId)
            );
            ConfigsDao.instance.updateOne(filters, updates);
        } else {
            ConfigsDao.instance.insertOne(config);
        }

        return SUCCESS.toUpperCase();
    }

    @Audit(description = "User deleted cloudflare waf integration", resource = Resource.CONFIGS, operation = Operation.DELETE)
    public String deleteCloudflareWafIntegration() {
        int accId = Context.accountId.get();
        Bson filters = Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        Config.CloudflareWafConfig config = (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(filters);
        if (config != null) {
            if (config.getRuleId() != null) {
                deleteWafRule(config);
            }
            deleteAllIpLists(config);
        }
        ConfigsDao.instance.getMCollection().deleteOne(filters);
        return SUCCESS.toUpperCase();
    }

    public String fetchCloudflareWafIntegration() {
        int accountId = Context.accountId.get();

        cloudflareWafConfig = (Config.CloudflareWafConfig) ConfigsDao.instance.findOne(Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accountId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        ));
        return SUCCESS.toUpperCase();
    }

    /**
     * Auto-migrates existing config: creates IP list if missing.
     * Called from ThreatActorAction before block/unblock.
     */
    public static Config.CloudflareWafConfig migrateToListIfNeeded(Config.CloudflareWafConfig config) {
        if (config == null || (config.getListIds() != null && !config.getListIds().isEmpty())) {
            return config;
        }

        String listId = findOrCreateIpList(config, "akto_blocked_ips_" + config.getAccountId());
        if (listId == null) {
            loggerMaker.errorAndAddToDb("Auto-migration: failed to create Cloudflare IP list");
            return config;
        }

        List<String> listIds = new ArrayList<>();
        listIds.add(listId);
        config.setListIds(listIds);
        Bson filters = Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, config.getAccountId()),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        ConfigsDao.instance.updateOne(filters, Updates.set(Config.CloudflareWafConfig.LIST_IDS, listIds));
        loggerMaker.infoAndAddToDb("Auto-migrated Cloudflare WAF config for account " + config.getAccountId());
        return config;
    }

    /**
     * Creates a new overflow list when the current one is full.
     * Updates the WAF rule expression to include the new list.
     * Returns the new list ID, or null on failure.
     */
    public static String createOverflowList(Config.CloudflareWafConfig config) {
        int accId = config.getAccountId();
        int nextIndex = config.getListIds().size();
        String newListName = "akto_blocked_ips_" + accId + "_" + nextIndex;

        String newListId = findOrCreateIpList(config, newListName);
        if (newListId == null) {
            return null;
        }

        config.getListIds().add(newListId);

        // Update WAF rule expression to include new list
        if (config.getRuleId() != null) {
            updateWafRuleExpression(config);
        }

        // Persist updated listIds
        Bson filters = Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, accId),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        ConfigsDao.instance.updateOne(filters, Updates.set(Config.CloudflareWafConfig.LIST_IDS, config.getListIds()));

        return newListId;
    }

    /**
     * Updates the WAF rule expression to reference all lists.
     */
    private static void updateWafRuleExpression(Config.CloudflareWafConfig config) {
        try {
            boolean isZoneLevel = "zones".equalsIgnoreCase(config.getIntegrationType());
            Map<String, List<String>> headers = getAuthHeaders(config);
            String expression = buildExpression(buildListNames(config.getAccountId(), config.getListIds().size()));

            BasicDBObject patchPayload = new BasicDBObject();
            patchPayload.put("expression", expression);
            patchPayload.put("action", "block");
            patchPayload.put("description", "Akto - Block malicious IPs");

            if (isZoneLevel && config.getZoneId() != null) {
                String entrypointId = findEntrypoint("/zones/" + config.getZoneId(), headers);
                if (entrypointId != null) {
                    String url = CLOUDFLARE_BASE_URL + "/zones/" + config.getZoneId()
                            + "/rulesets/" + entrypointId + "/rules/" + config.getRuleId();
                    sendRequest(url, "PATCH", patchPayload.toString(), headers);
                }
            } else {
                // Account-level: update rule inside the custom ruleset
                // ruleId is the custom ruleset ID, need to update the rule inside it
                String url = CLOUDFLARE_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                        + "/rulesets/" + config.getRuleId();
                OriginalHttpResponse resp = sendRequest(url, "GET", "", headers);
                if (resp.getStatusCode() <= 201 && resp.getBody() != null) {
                    BasicDBObject result = (BasicDBObject) BasicDBObject.parse(resp.getBody()).get("result");
                    if (result != null) {
                        BasicDBList rules = (BasicDBList) result.get("rules");
                        if (rules != null && !rules.isEmpty()) {
                            String innerRuleId = ((BasicDBObject) rules.get(0)).getString("id");
                            url = CLOUDFLARE_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                                    + "/rulesets/" + config.getRuleId() + "/rules/" + innerRuleId;
                            sendRequest(url, "PATCH", patchPayload.toString(), headers);
                        }
                    }
                }
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error updating WAF rule expression: " + e.getMessage());
        }
    }

    // ==================== Cloudflare API Helpers ====================

    public static Map<String, List<String>> getAuthHeaders(Config.CloudflareWafConfig config) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", Collections.singletonList("application/json"));
        String cfEmail = config.getEmail();
        if (cfEmail != null && !cfEmail.isEmpty()) {
            headers.put("X-Auth-Key", Collections.singletonList(config.getApiKey()));
            headers.put("X-Auth-Email", Collections.singletonList(cfEmail));
        } else {
            headers.put("Authorization", Collections.singletonList("Bearer " + config.getApiKey()));
        }
        return headers;
    }

    public static String extractCfError(String body) {
        try {
            if (body == null) return null;
            BasicDBObject obj = BasicDBObject.parse(body);
            BasicDBList errors = (BasicDBList) obj.get("errors");
            if (errors != null && !errors.isEmpty()) {
                return ((BasicDBObject) errors.get(0)).getString("message");
            }
        } catch (Exception ignored) {}
        return null;
    }

    // --- Credential validation ---

    private static String validateCredentials(Config.CloudflareWafConfig config) {
        try {
            String url = CLOUDFLARE_BASE_URL + "/accounts/" + config.getAccountOrZoneId() + "/rules/lists";
            OriginalHttpResponse resp = sendCfRequest(url, "GET", "", config);
            if (resp.getStatusCode() <= 201 && resp.getBody() != null) {
                return null;
            }
            String cfErr = extractCfError(resp.getBody());
            return cfErr != null ? cfErr : "Invalid Cloudflare credentials.";
        } catch (Exception e) {
            return "Error connecting to Cloudflare: " + e.getMessage();
        }
    }

    // --- IP List ---

    static String findOrCreateIpList(Config.CloudflareWafConfig config, String listName) {
        String existing = findIpListByName(config, listName);
        if (existing != null) return existing;
        return createIpList(config, listName);
    }

    private static String findIpListByName(Config.CloudflareWafConfig config, String listName) {
        try {
            String url = CLOUDFLARE_BASE_URL + "/accounts/" + config.getAccountOrZoneId() + "/rules/lists";
            OriginalHttpResponse resp = sendCfRequest(url, "GET", "", config);
            if (resp.getStatusCode() > 201 || resp.getBody() == null) return null;

            BasicDBList result = (BasicDBList) BasicDBObject.parse(resp.getBody()).get("result");
            if (result == null) return null;
            for (Object item : result) {
                BasicDBObject list = (BasicDBObject) item;
                if (listName.equals(list.getString("name"))) {
                    return list.getString("id");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String createIpList(Config.CloudflareWafConfig config, String listName) {
        try {
            String url = CLOUDFLARE_BASE_URL + "/accounts/" + config.getAccountOrZoneId() + "/rules/lists";
            BasicDBObject payload = new BasicDBObject();
            payload.put("name", listName);
            payload.put("kind", "ip");
            payload.put("description", "Akto threat detection blocked IPs");

            OriginalHttpResponse resp = sendCfRequest(url, "POST", payload.toString(), config);
            if (resp.getStatusCode() > 201 || resp.getBody() == null) {
                loggerMaker.errorAndAddToDb("Failed to create IP list: " + extractCfError(resp.getBody()));
                return null;
            }
            BasicDBObject result = (BasicDBObject) BasicDBObject.parse(resp.getBody()).get("result");
            return result != null ? result.getString("id") : null;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating IP list: " + e.getMessage());
            return null;
        }
    }

    private static void deleteAllIpLists(Config.CloudflareWafConfig config) {
        if (config.getListIds() == null) return;
        for (String id : config.getListIds()) {
            try {
                String url = CLOUDFLARE_BASE_URL + "/accounts/" + config.getAccountOrZoneId() + "/rules/lists/" + id;
                sendCfRequest(url, "DELETE", "", config);
            } catch (Exception e) {
                loggerMaker.errorAndAddToDb("Error deleting IP list " + id + ": " + e.getMessage());
            }
        }
    }

    // --- WAF Rule ---

    private static String createWafRule(Config.CloudflareWafConfig config, List<String> listNames) {
        boolean isZoneLevel = "zones".equalsIgnoreCase(config.getIntegrationType());
        String expression = buildExpression(listNames);

        if (isZoneLevel) {
            return createZoneLevelRule(config, expression);
        } else {
            return createAccountLevelRule(config, expression);
        }
    }

    /**
     * Zone-level: add block rule directly to zone entrypoint.
     * Works on all Cloudflare plans.
     */
    private static String createZoneLevelRule(Config.CloudflareWafConfig config, String expression) {
        try {
            String zId = config.getZoneId();
            Map<String, List<String>> headers = getAuthHeaders(config);

            String entrypointId = findEntrypoint("/zones/" + zId, headers);
            BasicDBObject blockRule = buildBlockRule(expression);

            OriginalHttpResponse resp;
            if (entrypointId != null) {
                String url = CLOUDFLARE_BASE_URL + "/zones/" + zId + "/rulesets/" + entrypointId + "/rules";
                resp = sendRequest(url, "POST", blockRule.toString(), headers);
            } else {
                String url = CLOUDFLARE_BASE_URL + "/zones/" + zId + WAF_ENTRYPOINT;
                BasicDBList rules = new BasicDBList();
                rules.add(blockRule);
                BasicDBObject payload = new BasicDBObject();
                payload.put("rules", rules);
                resp = sendRequest(url, "PUT", payload.toString(), headers);
            }

            if (resp.getStatusCode() > 201 || resp.getBody() == null) {
                loggerMaker.errorAndAddToDb("Failed to create zone WAF rule: " + extractCfError(resp.getBody()));
                return null;
            }
            return extractRuleId(resp.getBody());
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating zone WAF rule: " + e.getMessage());
            return null;
        }
    }

    /**
     * Account-level: create custom ruleset + deploy via execute rule in entrypoint.
     * Requires Enterprise plan.
     */
    private static String createAccountLevelRule(Config.CloudflareWafConfig config, String expression) {
        try {
            String accId = config.getAccountOrZoneId();
            Map<String, List<String>> headers = getAuthHeaders(config);

            // Step 1: Create custom ruleset with block rule
            String url = CLOUDFLARE_BASE_URL + "/accounts/" + accId + "/rulesets";
            BasicDBList rules = new BasicDBList();
            rules.add(buildBlockRule(expression));
            BasicDBObject payload = new BasicDBObject();
            payload.put("name", "Akto IP Block Rules");
            payload.put("kind", "custom");
            payload.put("phase", "http_request_firewall_custom");
            payload.put("rules", rules);

            OriginalHttpResponse resp = sendRequest(url, "POST", payload.toString(), headers);
            if (resp.getStatusCode() > 201 || resp.getBody() == null) {
                loggerMaker.errorAndAddToDb("Failed to create account custom ruleset: " + extractCfError(resp.getBody()));
                return null;
            }
            BasicDBObject result = (BasicDBObject) BasicDBObject.parse(resp.getBody()).get("result");
            if (result == null) return null;
            String customRulesetId = result.getString("id");

            // Step 2: Deploy via execute rule in entrypoint
            String entrypointId = findEntrypoint("/accounts/" + accId, headers);

            BasicDBObject executeRule = new BasicDBObject();
            executeRule.put("action", "execute");
            BasicDBObject actionParams = new BasicDBObject();
            actionParams.put("id", customRulesetId);
            executeRule.put("action_parameters", actionParams);
            executeRule.put("expression", "true");
            executeRule.put("description", "Deploy Akto IP block rules");

            if (entrypointId != null) {
                url = CLOUDFLARE_BASE_URL + "/accounts/" + accId + "/rulesets/" + entrypointId + "/rules";
                resp = sendRequest(url, "POST", executeRule.toString(), headers);
            } else {
                url = CLOUDFLARE_BASE_URL + "/accounts/" + accId + WAF_ENTRYPOINT;
                BasicDBList entryRules = new BasicDBList();
                entryRules.add(executeRule);
                BasicDBObject entryPayload = new BasicDBObject();
                entryPayload.put("rules", entryRules);
                resp = sendRequest(url, "PUT", entryPayload.toString(), headers);
            }

            if (resp.getStatusCode() > 201) {
                loggerMaker.errorAndAddToDb("Failed to deploy account ruleset: " + extractCfError(resp.getBody()));
                return null;
            }

            return customRulesetId;
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating account WAF rule: " + e.getMessage());
            return null;
        }
    }

    private static void deleteWafRule(Config.CloudflareWafConfig config) {
        try {
            boolean isZoneLevel = "zones".equalsIgnoreCase(config.getIntegrationType());
            Map<String, List<String>> headers = getAuthHeaders(config);

            if (isZoneLevel && config.getZoneId() != null) {
                // Zone-level: find entrypoint, delete rule by ID
                String entrypointId = findEntrypoint("/zones/" + config.getZoneId(), headers);
                if (entrypointId != null) {
                    String url = CLOUDFLARE_BASE_URL + "/zones/" + config.getZoneId()
                            + "/rulesets/" + entrypointId + "/rules/" + config.getRuleId();
                    sendRequest(url, "DELETE", "", headers);
                }
            } else {
                // Account-level: delete the custom ruleset (CF auto-removes execute reference)
                String url = CLOUDFLARE_BASE_URL + "/accounts/" + config.getAccountOrZoneId()
                        + "/rulesets/" + config.getRuleId();
                sendRequest(url, "DELETE", "", headers);
            }
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error deleting WAF rule: " + e.getMessage());
        }
    }

    // --- Shared helpers ---

    private static String findEntrypoint(String scopePath, Map<String, List<String>> headers) {
        try {
            String url = CLOUDFLARE_BASE_URL + scopePath + WAF_ENTRYPOINT;
            OriginalHttpResponse resp = sendRequest(url, "GET", "", headers);
            if (resp.getStatusCode() > 201 || resp.getBody() == null) return null;

            BasicDBObject obj = BasicDBObject.parse(resp.getBody());
            if (!obj.getBoolean("success", false)) return null;

            BasicDBObject result = (BasicDBObject) obj.get("result");
            return result != null ? result.getString("id") : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Builds list names for a given account and count.
     * First list: akto_blocked_ips_{accId}, overflow: akto_blocked_ips_{accId}_1, _2, etc.
     */
    static List<String> buildListNames(int accId, int count) {
        List<String> names = new ArrayList<>();
        names.add("akto_blocked_ips_" + accId);
        for (int i = 1; i < count; i++) {
            names.add("akto_blocked_ips_" + accId + "_" + i);
        }
        return names;
    }

    /**
     * Builds WAF rule expression from list names.
     * Format: (ip.src in $list1) or (ip.src in $list2)
     */
    static String buildExpression(List<String> listNames) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < listNames.size(); i++) {
            if (i > 0) sb.append(" or ");
            sb.append("(ip.src in $").append(listNames.get(i)).append(")");
        }
        return sb.toString();
    }

    private static BasicDBObject buildBlockRule(String expression) {
        BasicDBObject rule = new BasicDBObject();
        rule.put("action", "block");
        rule.put("expression", expression);
        rule.put("description", "Akto - Block malicious IPs");
        return rule;
    }

    /**
     * Extracts the rule ID for the Akto block rule from the ruleset response.
     */
    private static String extractRuleId(String body) {
        try {
            BasicDBObject result = (BasicDBObject) BasicDBObject.parse(body).get("result");
            if (result == null) return null;
            BasicDBList rules = (BasicDBList) result.get("rules");
            if (rules == null) return null;
            for (Object item : rules) {
                BasicDBObject rule = (BasicDBObject) item;
                String desc = rule.getString("description");
                if ("Akto - Block malicious IPs".equals(desc)) {
                    return rule.getString("id");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static OriginalHttpResponse sendCfRequest(String url, String method, String body, Config.CloudflareWafConfig config) throws Exception {
        return sendRequest(url, method, body, getAuthHeaders(config));
    }

    private static OriginalHttpResponse sendRequest(String url, String method, String body, Map<String, List<String>> headers) throws Exception {
        OriginalHttpRequest request = new OriginalHttpRequest(url, "", method, body, headers, "");
        return ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
    }

    // ==================== Getters and Setters ====================

    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAccountOrZoneId() { return accountOrZoneId; }
    public void setAccountOrZoneId(String accountOrZoneId) { this.accountOrZoneId = accountOrZoneId; }
    public Config.CloudflareWafConfig getCloudflareWafConfig() { return cloudflareWafConfig; }
    public String getIntegrationType() { return integrationType; }
    public void setIntegrationType(String integrationType) { this.integrationType = integrationType; }
    public List<String> getSeverityLevels() { return severityLevels; }
    public void setSeverityLevels(List<String> severityLevels) { this.severityLevels = severityLevels; }
    public String getZoneId() { return zoneId; }
    public void setZoneId(String zoneId) { this.zoneId = zoneId; }
}
