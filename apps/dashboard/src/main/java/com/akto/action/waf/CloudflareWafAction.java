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
        String listId;
        if (existingConfig != null && existingConfig.getListId() != null) {
            listId = existingConfig.getListId();
        } else {
            listId = findOrCreateIpList(config, "akto_blocked_ips_" + accId);
            if (listId == null) {
                return ERROR.toUpperCase();
            }
        }

        // 3. Find or create WAF rule
        String ruleId;
        if (existingConfig != null && existingConfig.getRuleId() != null) {
            ruleId = existingConfig.getRuleId();
        } else {
            ruleId = createWafRule(config, "akto_blocked_ips_" + accId);
            if (ruleId == null) {
                return ERROR.toUpperCase();
            }
        }

        config.setListId(listId);
        config.setRuleId(ruleId);

        // 4. Save
        if (existingConfig != null) {
            Bson updates = Updates.combine(
                    Updates.set(Config.CloudflareWafConfig.ACCOUNT_OR_ZONE_ID, accountOrZoneId),
                    Updates.set(Config.CloudflareWafConfig.INTEGRATION_TYPE, integrationType),
                    Updates.set(Config.CloudflareWafConfig.EMAIL, email),
                    Updates.set(Config.CloudflareWafConfig.SEVERITY_LEVELS, severityLevels),
                    Updates.set(Config.CloudflareWafConfig.LIST_ID, listId),
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
        if (config == null || config.getListId() != null) {
            return config;
        }

        String listId = findOrCreateIpList(config, "akto_blocked_ips_" + config.getAccountId());
        if (listId == null) {
            loggerMaker.errorAndAddToDb("Auto-migration: failed to create Cloudflare IP list");
            return config;
        }

        config.setListId(listId);
        Bson filters = Filters.and(
                Filters.eq(Config.CloudflareWafConfig.ACCOUNT_ID, config.getAccountId()),
                Filters.eq(Config.CloudflareWafConfig._CONFIG_ID, Config.ConfigType.CLOUDFLARE_WAF.name())
        );
        ConfigsDao.instance.updateOne(filters, Updates.set(Config.CloudflareWafConfig.LIST_ID, listId));
        loggerMaker.infoAndAddToDb("Auto-migrated Cloudflare WAF config for account " + config.getAccountId());
        return config;
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

    private static void deleteIpList(Config.CloudflareWafConfig config) {
        try {
            String url = CLOUDFLARE_BASE_URL + "/accounts/" + config.getAccountOrZoneId() + "/rules/lists/" + config.getListId();
            sendCfRequest(url, "DELETE", "", config);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error deleting IP list: " + e.getMessage());
        }
    }

    // --- WAF Rule ---

    private static String createWafRule(Config.CloudflareWafConfig config, String listName) {
        boolean isZoneLevel = "zones".equalsIgnoreCase(config.getIntegrationType());

        if (isZoneLevel) {
            return createZoneLevelRule(config, listName);
        } else {
            return createAccountLevelRule(config, listName);
        }
    }

    /**
     * Zone-level: add block rule directly to zone entrypoint.
     * Works on all Cloudflare plans.
     */
    private static String createZoneLevelRule(Config.CloudflareWafConfig config, String listName) {
        try {
            String zId = config.getZoneId();
            Map<String, List<String>> headers = getAuthHeaders(config);

            // Check if entrypoint exists
            String entrypointId = findEntrypoint("/zones/" + zId, headers);
            BasicDBObject blockRule = buildBlockRule(listName);

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
            return extractRuleId(resp.getBody(), listName);
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating zone WAF rule: " + e.getMessage());
            return null;
        }
    }

    /**
     * Account-level: create custom ruleset + deploy via execute rule in entrypoint.
     * Requires Enterprise plan.
     */
    private static String createAccountLevelRule(Config.CloudflareWafConfig config, String listName) {
        try {
            String accId = config.getAccountOrZoneId();
            Map<String, List<String>> headers = getAuthHeaders(config);

            // Step 1: Create custom ruleset with block rule
            String url = CLOUDFLARE_BASE_URL + "/accounts/" + accId + "/rulesets";
            BasicDBList rules = new BasicDBList();
            rules.add(buildBlockRule(listName));
            BasicDBObject payload = new BasicDBObject();
            payload.put("name", "Akto IP Block - " + listName);
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
            executeRule.put("description", "Deploy Akto IP block - " + listName);

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

    private static BasicDBObject buildBlockRule(String listName) {
        BasicDBObject rule = new BasicDBObject();
        rule.put("action", "block");
        rule.put("expression", "ip.src in $" + listName);
        rule.put("description", "Akto - Block malicious IPs");
        return rule;
    }

    private static String extractRuleId(String body, String listName) {
        try {
            BasicDBObject result = (BasicDBObject) BasicDBObject.parse(body).get("result");
            if (result == null) return null;
            BasicDBList rules = (BasicDBList) result.get("rules");
            if (rules == null) return null;
            String expr = "ip.src in $" + listName;
            for (Object item : rules) {
                BasicDBObject rule = (BasicDBObject) item;
                if (expr.equals(rule.getString("expression"))) {
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
