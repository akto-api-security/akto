package com.akto.threat.backend.service;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.GuardrailResponseRulesDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.Config;
import com.akto.dto.GuardrailResponseRule;
import com.akto.dto.GuardrailResponseRule.RuleAction;
import com.akto.dto.GuardrailResponseRule.RuleCondition;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.notifications.slack.CustomTextAlert;
import com.akto.notifications.slack.SlackSender;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class ResponseRuleEngine {

    private static final LoggerMaker logger = new LoggerMaker(ResponseRuleEngine.class);
    private static final ResponseRuleEngine INSTANCE = new ResponseRuleEngine();

    private static final String ACTIVITY_PATH = "/dashboard/protection/threat-activity";
    private static final long RULES_TTL_MS = 15_000L;
    private static final int WORKERS = 2;
    private static final int QUEUE_CAPACITY = 1000;
    private static final int HANDLED_CAPACITY = 10_000;
    private static final String SLACK_CHANNEL = "slack";
    private static final String SURFACE_AGENTS = "agents";
    private static final String SURFACE_IDE = "ide";
    private static final String SURFACE_MCP = "mcp";
    private static final String SURFACE_BROWSER = "browser";
    private static final String AGENT_HOST_MARKER = "ai-agent";
    private static final String TAG_BROWSER_ACCOUNT_TYPE = "browser-llm-account-type";
    private static final String TAG_LOGIN_EMAIL_TYPE = "login-user-email-type";
    private static final String PERSONAL = "personal";
    private static final String CORPORATE = "corporate";
    private static final Pattern IDE_VENDOR = Pattern.compile("cursor|copilot|windsurf|codeium|vscode|antigravity|kiro|zed|jetbrains|intellij");
    private static final Set<String> BROWSER_MARKERS = new HashSet<>(Arrays.asList("chrome", "edge", "firefox", "safari", "brave", "arc", "opera"));

    private final ExecutorService executor = new ThreadPoolExecutor(
        WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(QUEUE_CAPACITY),
        new ThreadPoolExecutor.DiscardPolicy());
    private final Map<String, CachedRules> rulesByAccount = new ConcurrentHashMap<>();
    private final Set<String> handled = Collections.newSetFromMap(Collections.synchronizedMap(
        new LinkedHashMap<String, Boolean>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > HANDLED_CAPACITY;
            }
        }));

    private ResponseRuleEngine() {}

    public static ResponseRuleEngine getInstance() {
        return INSTANCE;
    }

    private static final class CachedRules {
        final List<GuardrailResponseRule> rules;
        final long loadedAt;

        CachedRules(List<GuardrailResponseRule> rules, long loadedAt) {
            this.rules = rules;
            this.loadedAt = loadedAt;
        }
    }

    private static final class Violation {
        final String severity;
        final String filterId;
        final String category;
        final String subCategory;
        final String host;
        final String actor;
        final String contextSource;
        final String refId;
        final String eventType;
        final String endpoint;
        private Boolean personal;

        Violation(MaliciousEventDto event) {
            this.severity = nonNull(event.getSeverity());
            this.filterId = nonNull(event.getFilterId());
            this.category = nonNull(event.getCategory());
            this.subCategory = nonNull(event.getSubCategory());
            this.host = nonNull(event.getHost());
            this.actor = nonNull(event.getActor());
            this.contextSource = nonNull(event.getContextSource());
            this.refId = nonNull(event.getRefId());
            this.eventType = event.getEventType() == null ? "" : event.getEventType().name();
            this.endpoint = nonNull(event.getLatestApiEndpoint());
        }

        boolean isPersonalAccount() {
            if (personal == null) personal = lookupPersonal(host);
            return personal;
        }
    }

    public void onEventSaved(String accountId, MaliciousEventDto event) {
        if (event == null || StringUtils.isBlank(accountId)) return;
        executor.execute(() -> process(accountId, event));
    }

    private void process(String accountId, MaliciousEventDto event) {
        try {
            int account = Integer.parseInt(accountId);
            Context.accountId.set(account);
            List<GuardrailResponseRule> rules = activeRules(accountId);
            if (rules.isEmpty()) return;
            Violation violation = new Violation(event);
            for (GuardrailResponseRule rule : rules) {
                if (!matches(rule, violation)) continue;
                if (!violation.refId.isEmpty() && !handled.add(accountId + "|" + rule.getHexId() + "|" + violation.refId)) continue;
                if (execute(rule, account, violation)) recordRun(rule);
            }
        } catch (Exception e) {
            logger.error("Response rule evaluation failed: " + e.getMessage(), e);
        } finally {
            Context.accountId.remove();
        }
    }

    private List<GuardrailResponseRule> activeRules(String accountId) {
        long now = System.currentTimeMillis();
        CachedRules cached = rulesByAccount.get(accountId);
        if (cached != null && now - cached.loadedAt < RULES_TTL_MS) return cached.rules;
        List<GuardrailResponseRule> loaded = GuardrailResponseRulesDao.instance.findAll(Filters.eq("active", true));
        rulesByAccount.put(accountId, new CachedRules(loaded, now));
        return loaded;
    }

    private boolean matches(GuardrailResponseRule rule, Violation violation) {
        if (!sameContext(rule.getContextSource(), violation.contextSource)) return false;
        List<RuleCondition> conditions = rule.getConditions();
        if (conditions == null || conditions.isEmpty()) return false;
        for (RuleCondition condition : conditions) {
            if (!conditionMatches(condition, violation)) return false;
        }
        return true;
    }

    private static boolean sameContext(CONTEXT_SOURCE ruleContext, String eventContext) {
        String rule = ruleContext == null ? CONTEXT_SOURCE.AGENTIC.name() : ruleContext.name();
        String event = StringUtils.isBlank(eventContext) ? CONTEXT_SOURCE.AGENTIC.name() : eventContext;
        return rule.equalsIgnoreCase(event);
    }

    private static boolean conditionMatches(RuleCondition condition, Violation violation) {
        List<String> values = condition.getValues();
        if (condition.getField() == null || values == null || values.isEmpty()) return false;
        switch (condition.getField()) {
            case GuardrailResponseRule.CONDITION_SEVERITY:
                return anyEquals(values, violation.severity);
            case GuardrailResponseRule.CONDITION_POLICY:
                return anyEquals(values, violation.filterId) || anyEquals(values, violation.subCategory) || anyEquals(values, violation.category);
            case GuardrailResponseRule.CONDITION_AGENT:
                return !violation.host.isEmpty() && violation.host.toLowerCase().contains(values.get(0).trim().toLowerCase());
            case GuardrailResponseRule.CONDITION_SURFACE:
                return anyEquals(values, surfaceOf(violation.host));
            case GuardrailResponseRule.CONDITION_ACCOUNT_TYPE:
                return anyEquals(values, violation.isPersonalAccount() ? PERSONAL : CORPORATE);
            default:
                return false;
        }
    }

    private static boolean anyEquals(List<String> values, String target) {
        if (StringUtils.isBlank(target)) return false;
        for (String value : values) {
            if (target.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private static String surfaceOf(String host) {
        String[] parts = host.toLowerCase().split("\\.");
        String marker = parts.length > 1 ? parts[1] : "";
        if (BROWSER_MARKERS.contains(marker)) return SURFACE_BROWSER;
        if (AGENT_HOST_MARKER.equals(marker)) {
            String vendor = parts.length > 2 ? parts[2] : "";
            return IDE_VENDOR.matcher(vendor).find() ? SURFACE_IDE : SURFACE_AGENTS;
        }
        return SURFACE_MCP;
    }

    private static boolean lookupPersonal(String host) {
        if (StringUtils.isBlank(host)) return false;
        ApiCollection collection = ApiCollectionsDao.instance.findOne(Filters.eq("hostName", host));
        if (collection == null || collection.getEnvType() == null) return false;
        for (CollectionTags tag : collection.getEnvType()) {
            boolean accountTypeTag = TAG_BROWSER_ACCOUNT_TYPE.equals(tag.getKeyName()) || TAG_LOGIN_EMAIL_TYPE.equals(tag.getKeyName());
            if (accountTypeTag && PERSONAL.equalsIgnoreCase(tag.getValue())) return true;
        }
        return false;
    }

    private boolean execute(GuardrailResponseRule rule, int accountId, Violation violation) {
        boolean executed = false;
        List<RuleAction> actions = rule.getActions();
        if (actions == null) return false;
        for (RuleAction action : actions) {
            if (!GuardrailResponseRule.ACTION_ALERT.equals(action.getType())) {
                logger.debug("Response rule action " + action.getType() + " is not executed yet");
                continue;
            }
            Object channels = action.getConfig() == null ? null : action.getConfig().get("channels");
            if (channels instanceof Collection && ((Collection<?>) channels).contains(SLACK_CHANNEL)) {
                SlackSender.sendAlert(accountId, new CustomTextAlert(alertText(rule, violation)), null);
                executed = true;
            }
        }
        return executed;
    }

    private void recordRun(GuardrailResponseRule rule) {
        try {
            GuardrailResponseRulesDao.instance.updateOne(
                Filters.eq("_id", rule.getId()),
                Updates.combine(Updates.inc("runCount", 1L), Updates.set("lastRunAt", Context.now())));
        } catch (Exception e) {
            logger.error("Could not record a response rule run: " + e.getMessage(), e);
        }
    }

    private String alertText(GuardrailResponseRule rule, Violation violation) {
        StringBuilder text = new StringBuilder();
        text.append(":rotating_light: *Response rule* \"").append(escape(rule.getName())).append("\"\n");
        text.append("*").append(escape(violation.severity)).append("* violation");
        String policy = StringUtils.firstNonBlank(violation.filterId, violation.subCategory, violation.category);
        if (StringUtils.isNotBlank(policy)) text.append(" · ").append(escape(policy));
        if (!violation.host.isEmpty()) text.append("\nAgent: ").append(escape(violation.host));
        if (!violation.actor.isEmpty()) text.append("\nUser: ").append(escape(violation.actor));
        String link = activityUrl(violation);
        if (link != null) text.append("\n<").append(link).append("|View violation>");
        return text.toString();
    }

    private String activityUrl(Violation violation) {
        if (violation.refId.isEmpty() || violation.eventType.isEmpty() || violation.actor.isEmpty() || violation.filterId.isEmpty()) return null;
        return dashboardBaseUrl() + ACTIVITY_PATH
            + "?refId=" + encode(violation.refId)
            + "&eventType=" + encode(violation.eventType)
            + "&actor=" + encode(violation.actor)
            + "&filterId=" + encode(violation.filterId);
    }

    private String dashboardBaseUrl() {
        String base = Constants.DEFAULT_AKTO_DASHBOARD_URL;
        try {
            Config.AktoHostUrlConfig config = (Config.AktoHostUrlConfig) ConfigsDao.instance.findOne(
                Filters.eq(Constants.ID, Config.ConfigType.AKTO_DASHBOARD_HOST_URL.name()));
            if (config != null && StringUtils.isNotBlank(config.getHostUrl())) base = config.getHostUrl();
        } catch (Exception e) {
            logger.error("Could not read the dashboard host url: " + e.getMessage(), e);
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String nonNull(String value) {
        return value == null ? "" : value;
    }
}
