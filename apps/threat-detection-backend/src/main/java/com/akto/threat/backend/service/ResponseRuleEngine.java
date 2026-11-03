package com.akto.threat.backend.service;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.GuardrailResponseRulesDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.Config;
import com.akto.dto.GuardrailResponseRule;
import com.akto.dto.GuardrailResponseRule.RuleAction;
import com.akto.dto.GuardrailResponseRule.RuleCondition;
import com.akto.dto.OriginalHttpRequest;
import com.akto.dto.OriginalHttpResponse;
import com.akto.dto.notifications.CustomWebhook;
import com.akto.dto.threat_detection_backend.MaliciousEventDto;
import com.akto.dto.traffic.CollectionTags;
import com.akto.log.LoggerMaker;
import com.akto.notifications.email.SendgridEmail;
import com.akto.notifications.slack.CustomTextAlert;
import com.akto.notifications.slack.SlackSender;
import com.akto.testing.ApiExecutor;
import com.akto.threat.backend.dao.MaliciousEventDao;
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
    private static final String CLAIMED_RULES_FIELD = "responseRuleIds";
    private static final long RULES_TTL_MS = 15_000L;
    private static final long PERSONAL_TTL_MS = 60_000L;
    private static final int PERSONAL_CACHE_CAPACITY = 10_000;
    private static final int WORKERS = 2;
    private static final int DELIVERY_WORKERS = 3;
    private static final int QUEUE_CAPACITY = 10_000;
    private static final int MAX_EMAILS = 20;
    private static final String CHANNEL_SLACK = "slack";
    private static final String CHANNEL_WEBHOOK = "webhook";
    private static final String CHANNEL_EMAIL = "email";
    private static final String CONFIG_CHANNELS = "channels";
    private static final String CONFIG_SLACK_WEBHOOK_ID = "slackWebhookId";
    private static final String CONFIG_WEBHOOK_ID = "webhookId";
    private static final String CONFIG_EMAILS = "emails";
    private static final String CONFIG_ASSIGNEE = "assignee";
    private static final String EMAIL_FROM = "ankita.gupta@akto.io";
    private static final String EMAIL_FROM_NAME = "Akto";
    private static final String UPDATED_BY_PREFIX = "Response rule: ";
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

    private final ExecutorService executor = newPool(WORKERS, "Response rule queue is full, a violation was not evaluated");
    private final ExecutorService deliveries = newPool(DELIVERY_WORKERS, "Response rule delivery queue is full, an alert was not sent");
    private final Map<String, CachedRules> rulesByAccount = new ConcurrentHashMap<>();
    private final Map<String, CachedFlag> personalByHost = new ConcurrentHashMap<>();
    private volatile MaliciousEventService eventService;

    private ResponseRuleEngine() {}

    public static ResponseRuleEngine getInstance() {
        return INSTANCE;
    }

    public void setEventService(MaliciousEventService eventService) {
        this.eventService = eventService;
    }

    private static ExecutorService newPool(int workers, String rejectedMessage) {
        return new ThreadPoolExecutor(
            workers, workers, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            (task, pool) -> logger.error(rejectedMessage));
    }

    private static final class CachedRules {
        final List<GuardrailResponseRule> rules;
        final long loadedAt;

        CachedRules(List<GuardrailResponseRule> rules, long loadedAt) {
            this.rules = rules;
            this.loadedAt = loadedAt;
        }
    }

    private static final class CachedFlag {
        final boolean value;
        final long loadedAt;

        CachedFlag(boolean value, long loadedAt) {
            this.value = value;
            this.loadedAt = loadedAt;
        }
    }

    private final class Violation {
        final int accountId;
        final String eventId;
        final String severity;
        final String filterId;
        final String category;
        final String subCategory;
        final String host;
        final String actor;
        final String contextSource;
        final String refId;
        final String eventType;
        final long detectedAt;
        private Boolean personal;

        Violation(int accountId, MaliciousEventDto event) {
            this.accountId = accountId;
            this.eventId = nonNull(event.getId());
            this.severity = nonNull(event.getSeverity());
            this.filterId = nonNull(event.getFilterId());
            this.category = nonNull(event.getCategory());
            this.subCategory = nonNull(event.getSubCategory());
            this.host = nonNull(event.getHost());
            this.actor = nonNull(event.getActor());
            this.contextSource = nonNull(event.getContextSource());
            this.refId = nonNull(event.getRefId());
            this.eventType = event.getEventType() == null ? "" : event.getEventType().name();
            this.detectedAt = event.getDetectedAt();
        }

        boolean isPersonalAccount() {
            if (personal == null) personal = lookupPersonal(accountId, host);
            return personal;
        }

        String policy() {
            return StringUtils.firstNonBlank(filterId, subCategory, category);
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
            Violation violation = new Violation(account, event);
            for (GuardrailResponseRule rule : rules) {
                runRule(rule, violation);
            }
        } catch (Exception e) {
            logger.error("Response rule evaluation failed: " + e.getMessage(), e);
        } finally {
            Context.accountId.remove();
        }
    }

    private void runRule(GuardrailResponseRule rule, Violation violation) {
        try {
            if (!matches(rule, violation) || !claim(rule, violation)) return;
            execute(rule, violation);
            recordRun(rule);
        } catch (Exception e) {
            logger.error("Response rule " + rule.getHexId() + " failed: " + e.getMessage(), e);
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

    private boolean claim(GuardrailResponseRule rule, Violation violation) {
        if (violation.eventId.isEmpty()) return true;
        long modified = MaliciousEventDao.instance.getDocumentCollection(String.valueOf(violation.accountId)).updateOne(
            Filters.and(Filters.eq("_id", violation.eventId), Filters.ne(CLAIMED_RULES_FIELD, rule.getHexId())),
            Updates.addToSet(CLAIMED_RULES_FIELD, rule.getHexId())).getModifiedCount();
        return modified > 0;
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
                return anyContainedIn(values, violation.host);
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

    private static boolean anyContainedIn(List<String> values, String target) {
        if (StringUtils.isBlank(target)) return false;
        String haystack = target.toLowerCase();
        for (String value : values) {
            if (StringUtils.isNotBlank(value) && haystack.contains(value.trim().toLowerCase())) return true;
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

    private boolean lookupPersonal(int accountId, String host) {
        if (StringUtils.isBlank(host)) return false;
        String key = accountId + "|" + host;
        long now = System.currentTimeMillis();
        CachedFlag cached = personalByHost.get(key);
        if (cached != null && now - cached.loadedAt < PERSONAL_TTL_MS) return cached.value;
        boolean personal = isPersonalCollection(host);
        if (personalByHost.size() >= PERSONAL_CACHE_CAPACITY) personalByHost.clear();
        personalByHost.put(key, new CachedFlag(personal, now));
        return personal;
    }

    private static boolean isPersonalCollection(String host) {
        ApiCollection collection = ApiCollectionsDao.instance.findOne(Filters.eq("hostName", host));
        if (collection == null || collection.getEnvType() == null) return false;
        for (CollectionTags tag : collection.getEnvType()) {
            boolean accountTypeTag = TAG_BROWSER_ACCOUNT_TYPE.equals(tag.getKeyName()) || TAG_LOGIN_EMAIL_TYPE.equals(tag.getKeyName());
            if (accountTypeTag && PERSONAL.equalsIgnoreCase(tag.getValue())) return true;
        }
        return false;
    }

    private void execute(GuardrailResponseRule rule, Violation violation) {
        List<RuleAction> actions = rule.getActions();
        if (actions == null) return;
        for (RuleAction action : actions) {
            try {
                executeAction(rule, action, violation);
            } catch (Exception e) {
                logger.error("Response rule " + rule.getHexId() + " action " + action.getType() + " failed: " + e.getMessage(), e);
            }
        }
    }

    private void executeAction(GuardrailResponseRule rule, RuleAction action, Violation violation) {
        Map<String, Object> config = action.getConfig() == null ? Collections.emptyMap() : action.getConfig();
        switch (action.getType()) {
            case GuardrailResponseRule.ACTION_ALERT:
                sendAlerts(rule, config, violation);
                break;
            case GuardrailResponseRule.ACTION_IGNORE:
                updateEvent(rule, violation, MaliciousEventDto.Status.IGNORED.name(), null);
                break;
            case GuardrailResponseRule.ACTION_ASSIGN:
                String assignee = String.valueOf(config.getOrDefault(CONFIG_ASSIGNEE, "")).trim();
                if (!assignee.isEmpty()) updateEvent(rule, violation, null, assignee);
                break;
            default:
                logger.debug("Response rule action " + action.getType() + " is not executed yet");
        }
    }

    private void updateEvent(GuardrailResponseRule rule, Violation violation, String status, String owner) {
        if (eventService == null || violation.eventId.isEmpty()) return;
        eventService.updateMaliciousEventStatus(String.valueOf(violation.accountId), Collections.singletonList(violation.eventId),
            null, status, null, null, null, owner, null, UPDATED_BY_PREFIX + rule.getName());
    }

    private void sendAlerts(GuardrailResponseRule rule, Map<String, Object> config, Violation violation) {
        Object value = config.get(CONFIG_CHANNELS);
        Collection<?> channels = value instanceof Collection ? (Collection<?>) value : Collections.emptyList();
        if (channels.contains(CHANNEL_SLACK)) {
            SlackSender.sendAlert(violation.accountId, new CustomTextAlert(slackText(rule, violation)), toInteger(config.get(CONFIG_SLACK_WEBHOOK_ID)));
        }
        if (channels.contains(CHANNEL_WEBHOOK)) {
            deliver(violation.accountId, () -> postWebhook(rule, violation, toInteger(config.get(CONFIG_WEBHOOK_ID))));
        }
        if (channels.contains(CHANNEL_EMAIL)) {
            deliver(violation.accountId, () -> sendEmail(rule, violation, String.valueOf(config.getOrDefault(CONFIG_EMAILS, ""))));
        }
    }

    @FunctionalInterface
    private interface Delivery {
        void run() throws Exception;
    }

    private void deliver(int accountId, Delivery task) {
        deliveries.execute(() -> {
            try {
                Context.accountId.set(accountId);
                task.run();
            } catch (Exception e) {
                logger.error("Response rule delivery failed: " + e.getMessage(), e);
            } finally {
                Context.accountId.remove();
            }
        });
    }

    private void postWebhook(GuardrailResponseRule rule, Violation violation, Integer webhookId) throws Exception {
        CustomWebhook webhook = webhookId == null
            ? CustomWebhooksDao.instance.findOne(Filters.empty())
            : CustomWebhooksDao.instance.findOne(Filters.eq("_id", webhookId));
        if (webhook == null || StringUtils.isBlank(webhook.getUrl())) {
            logger.error("Response rule webhook is not configured");
            return;
        }
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(webhook.getHeaderString());
        boolean hasContentType = headers.keySet().stream().anyMatch("content-type"::equalsIgnoreCase);
        if (!hasContentType) headers.put("content-type", Collections.singletonList("application/json"));
        String method = webhook.getMethod() == null ? "POST" : webhook.getMethod().toString();
        OriginalHttpRequest request = new OriginalHttpRequest(webhook.getUrl(), webhook.getQueryParams(), method, webhookPayload(rule, violation), headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        if (response == null || response.getStatusCode() >= 300) {
            logger.error("Response rule webhook " + webhook.getId() + " returned " + (response == null ? "no response" : response.getStatusCode()));
        }
    }

    private void sendEmail(GuardrailResponseRule rule, Violation violation, String recipients) throws Exception {
        Personalization personalization = new Personalization();
        int added = 0;
        for (String address : recipients.split("[,;\\s]+")) {
            if (added >= MAX_EMAILS) break;
            if (!address.contains("@")) continue;
            personalization.addTo(new Email(address));
            added++;
        }
        if (added == 0) return;
        Mail mail = new Mail();
        mail.setFrom(new Email(EMAIL_FROM, EMAIL_FROM_NAME));
        personalization.setSubject(violation.severity + " violation: " + rule.getName());
        mail.addPersonalization(personalization);
        mail.addContent(new Content("text/plain", plainText(rule, violation)));
        SendgridEmail.getInstance().send(mail);
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

    private String slackText(GuardrailResponseRule rule, Violation violation) {
        StringBuilder text = new StringBuilder();
        text.append(":rotating_light: *Response rule* \"").append(escape(rule.getName())).append("\"\n");
        text.append("*").append(escape(violation.severity)).append("* violation");
        if (StringUtils.isNotBlank(violation.policy())) text.append(" · ").append(escape(violation.policy()));
        if (!violation.host.isEmpty()) text.append("\nAgent: ").append(escape(violation.host));
        if (!violation.actor.isEmpty()) text.append("\nUser: ").append(escape(violation.actor));
        String link = activityUrl(violation);
        if (link != null) text.append("\n<").append(link).append("|View violation>");
        return text.toString();
    }

    private String plainText(GuardrailResponseRule rule, Violation violation) {
        StringBuilder text = new StringBuilder();
        text.append("Response rule: ").append(rule.getName()).append("\n");
        text.append("Severity: ").append(violation.severity).append("\n");
        if (StringUtils.isNotBlank(violation.policy())) text.append("Policy: ").append(violation.policy()).append("\n");
        if (!violation.host.isEmpty()) text.append("Agent: ").append(violation.host).append("\n");
        if (!violation.actor.isEmpty()) text.append("User: ").append(violation.actor).append("\n");
        String link = activityUrl(violation);
        if (link != null) text.append("View violation: ").append(link).append("\n");
        return text.toString();
    }

    private String webhookPayload(GuardrailResponseRule rule, Violation violation) {
        return new BasicDBObject("rule", rule.getName())
            .append("severity", violation.severity)
            .append("policy", violation.policy())
            .append("agent", violation.host)
            .append("user", violation.actor)
            .append("eventId", violation.eventId)
            .append("refId", violation.refId)
            .append("detectedAt", violation.detectedAt)
            .append("link", activityUrl(violation))
            .toJson();
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

    private static Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
