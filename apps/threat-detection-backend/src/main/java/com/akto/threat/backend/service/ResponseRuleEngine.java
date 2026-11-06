package com.akto.threat.backend.service;

import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ConfigsDao;
import com.akto.dao.GuardrailResponseRuleRunsDao;
import com.akto.dao.GuardrailResponseRulesDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.Config;
import com.akto.dto.GuardrailResponseRule;
import com.akto.dto.GuardrailResponseRule.RuleAction;
import com.akto.dto.GuardrailResponseRule.RuleCondition;
import com.akto.dto.GuardrailResponseRuleRun;
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
import com.akto.util.Constants;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
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
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ResponseRuleEngine {

    private static final LoggerMaker logger = new LoggerMaker(ResponseRuleEngine.class);
    private static final ResponseRuleEngine INSTANCE = new ResponseRuleEngine();

    private static final String ACTIVITY_PATH = "/dashboard/protection/threat-activity";
    private static final long RULES_TTL_MS = 15_000L;
    private static final long PERSONAL_TTL_MS = 60_000L;
    private static final int PERSONAL_CACHE_CAPACITY = 10_000;
    private static final int WORKERS = 2;
    private static final int DELIVERY_WORKERS = 3;
    private static final int QUEUE_CAPACITY = 10_000;
    private static final int MAX_EMAILS = 20;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final String CHANNEL_SLACK = "slack";
    private static final String CHANNEL_WEBHOOK = "webhook";
    private static final String CHANNEL_EMAIL = "email";
    private static final List<String> ALERT_CHANNELS = Arrays.asList(CHANNEL_SLACK, CHANNEL_WEBHOOK, CHANNEL_EMAIL);
    private static final String CONFIG_CHANNELS = "channels";
    private static final String CONFIG_SLACK_WEBHOOK_ID = "slackWebhookId";
    private static final String CONFIG_WEBHOOK_ID = "webhookId";
    private static final String CONFIG_EMAILS = "emails";
    private static final String CONFIG_ASSIGNEE = "assignee";
    private static final String EMAIL_FROM = "ankita.gupta@akto.io";
    private static final String EMAIL_FROM_NAME = "Akto";
    private static final String UPDATED_BY_PREFIX = "Response rule: ";
    private static final String SURFACE_AGENTS = "agents";
    private static final String SURFACE_MCP = "mcp";
    private static final String SURFACE_BROWSER = "browser";
    private static final String AGENT_HOST_MARKER = "ai-agent";
    private static final String TAG_BROWSER_ACCOUNT_TYPE = "browser-llm-account-type";
    private static final String TAG_LOGIN_EMAIL_TYPE = "login-user-email-type";
    private static final String PERSONAL = "personal";
    private static final String CORPORATE = "corporate";
    private static final Set<String> BROWSER_MARKERS = new HashSet<>(Arrays.asList("chrome", "edge", "firefox", "safari", "brave", "arc", "opera"));

    private final ExecutorService executor = newPool(WORKERS, "Response rule queue is full, a violation was not evaluated");
    private final ExecutorService deliveries = newPool(DELIVERY_WORKERS, "Response rule delivery queue is full, a delivery will be retried by the sweep");
    private final Map<String, CachedRules> rulesByAccount = new ConcurrentHashMap<>();
    private final Map<String, CachedFlag> personalByHost = new ConcurrentHashMap<>();
    private final Set<Integer> indexedAccounts = ConcurrentHashMap.newKeySet();
    private volatile MaliciousEventService eventService;

    private ResponseRuleEngine() {}

    public static ResponseRuleEngine getInstance() {
        return INSTANCE;
    }

    public void init(MaliciousEventService eventService) {
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
        final String eventKey;
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
            this.eventKey = StringUtils.firstNonBlank(eventId, refId, UUID.randomUUID().toString());
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
            if (!matches(rule, violation)) return;
            List<GuardrailResponseRuleRun> units = unitsOf(rule, violation);
            if (units.isEmpty()) return;
            if (indexedAccounts.add(violation.accountId)) GuardrailResponseRuleRunsDao.instance.createIndicesIfAbsent();
            for (int i = 0; i < units.size(); i++) {
                GuardrailResponseRuleRun run = units.get(i);
                if (!claim(run)) continue;
                if (i == 0) recordRun(rule);
                submit(violation.accountId, run);
            }
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
        if (AGENT_HOST_MARKER.equals(marker)) return SURFACE_AGENTS;
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

    private List<GuardrailResponseRuleRun> unitsOf(GuardrailResponseRule rule, Violation violation) {
        List<GuardrailResponseRuleRun> units = new ArrayList<>();
        List<RuleAction> actions = rule.getActions();
        if (actions == null) return units;
        for (int idx = 0; idx < actions.size(); idx++) {
            RuleAction action = actions.get(idx);
            Map<String, Object> config = action.getConfig() == null ? Collections.emptyMap() : action.getConfig();
            for (String channel : channelsOf(action.getType(), config, violation)) {
                units.add(newRun(rule, violation, idx, action.getType(), channel, config));
            }
        }
        return units;
    }

    private static List<String> channelsOf(String type, Map<String, Object> config, Violation violation) {
        switch (type) {
            case GuardrailResponseRule.ACTION_ALERT:
                List<String> channels = new ArrayList<>();
                Object value = config.get(CONFIG_CHANNELS);
                if (!(value instanceof Collection)) return channels;
                for (String channel : ALERT_CHANNELS) {
                    if (((Collection<?>) value).contains(channel)) channels.add(channel);
                }
                return channels;
            case GuardrailResponseRule.ACTION_IGNORE:
                return violation.eventId.isEmpty() ? Collections.emptyList() : Collections.singletonList("");
            case GuardrailResponseRule.ACTION_ASSIGN:
                boolean assignable = !violation.eventId.isEmpty() && !assigneeOf(config).isEmpty();
                return assignable ? Collections.singletonList("") : Collections.emptyList();
            default:
                return Collections.emptyList();
        }
    }

    private static String assigneeOf(Map<String, Object> config) {
        return String.valueOf(config.getOrDefault(CONFIG_ASSIGNEE, "")).trim();
    }

    private GuardrailResponseRuleRun newRun(GuardrailResponseRule rule, Violation violation, int idx, String type, String channel, Map<String, Object> config) {
        GuardrailResponseRuleRun run = new GuardrailResponseRuleRun();
        run.setId(rule.getHexId() + ":" + violation.eventKey + ":" + idx + ":" + channel);
        run.setRuleId(rule.getHexId());
        run.setRuleName(rule.getName());
        run.setEventId(violation.eventId);
        run.setActionIdx(idx);
        run.setActionType(type);
        run.setChannel(channel);
        run.setActionConfig(config);
        run.setSeverity(violation.severity);
        run.setPolicy(violation.policy());
        run.setFilterId(violation.filterId);
        run.setHost(violation.host);
        run.setActor(violation.actor);
        run.setRefId(violation.refId);
        run.setEventType(violation.eventType);
        run.setDetectedAt(violation.detectedAt);
        run.setStatus(GuardrailResponseRuleRun.STATUS_PENDING);
        run.setCreatedAt(new Date());
        return run;
    }

    private boolean claim(GuardrailResponseRuleRun run) {
        try {
            GuardrailResponseRuleRunsDao.instance.insertOne(run);
            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) return false;
            throw e;
        }
    }

    private void recordRun(GuardrailResponseRule rule) {
        try {
            GuardrailResponseRulesDao.instance.updateOneNoUpsert(
                Filters.eq("_id", rule.getId()),
                Updates.combine(Updates.inc("runCount", 1L), Updates.set("lastRunAt", Context.now())));
        } catch (Exception e) {
            logger.error("Could not record a response rule run: " + e.getMessage(), e);
        }
    }

    private void submit(int accountId, GuardrailResponseRuleRun run) {
        deliveries.execute(() -> {
            try {
                Context.accountId.set(accountId);
                attempt(run);
            } finally {
                Context.accountId.remove();
            }
        });
    }

    private void attempt(GuardrailResponseRuleRun run) {
        try {
            perform(run);
            GuardrailResponseRuleRunsDao.instance.updateOneNoUpsert(Filters.eq("_id", run.getId()), Updates.combine(
                Updates.set(GuardrailResponseRuleRun.STATUS, GuardrailResponseRuleRun.STATUS_SENT),
                Updates.set(GuardrailResponseRuleRun.SENT_AT, Context.now())));
        } catch (Exception e) {
            recordFailure(run, e.getMessage());
        }
    }

    private void recordFailure(GuardrailResponseRuleRun run, String error) {
        try {
            String message = StringUtils.abbreviate(StringUtils.defaultString(error, "Unknown error"), MAX_ERROR_LENGTH);
            logger.error("Response rule " + run.getRuleId() + " " + run.getActionType() + " " + run.getChannel() + " failed: " + message);
            GuardrailResponseRuleRunsDao.instance.updateOneNoUpsert(Filters.eq("_id", run.getId()), Updates.combine(
                Updates.set(GuardrailResponseRuleRun.STATUS, GuardrailResponseRuleRun.STATUS_FAILED),
                Updates.set(GuardrailResponseRuleRun.LAST_ERROR, message)));
        } catch (Exception e) {
            logger.error("Could not record a response rule failure: " + e.getMessage(), e);
        }
    }

    private void perform(GuardrailResponseRuleRun run) throws Exception {
        switch (run.getActionType()) {
            case GuardrailResponseRule.ACTION_ALERT:
                sendAlert(run);
                break;
            case GuardrailResponseRule.ACTION_IGNORE:
                updateEvent(run, MaliciousEventDto.Status.IGNORED.name(), null);
                break;
            case GuardrailResponseRule.ACTION_ASSIGN:
                updateEvent(run, null, assigneeOf(run.getActionConfig()));
                break;
            default:
                throw new Exception("Action " + run.getActionType() + " is not supported");
        }
    }

    private void sendAlert(GuardrailResponseRuleRun run) throws Exception {
        switch (run.getChannel()) {
            case CHANNEL_SLACK:
                postSlack(run);
                break;
            case CHANNEL_WEBHOOK:
                postWebhook(run);
                break;
            case CHANNEL_EMAIL:
                sendEmail(run);
                break;
            default:
                throw new Exception("Channel " + run.getChannel() + " is not supported");
        }
    }

    private void updateEvent(GuardrailResponseRuleRun run, String status, String owner) throws Exception {
        if (eventService == null || StringUtils.isBlank(run.getEventId())) throw new Exception("The event cannot be updated");
        eventService.updateMaliciousEventStatus(String.valueOf(Context.accountId.get()), Collections.singletonList(run.getEventId()),
            null, status, null, null, null, owner, null, UPDATED_BY_PREFIX + run.getRuleName());
    }

    private void postSlack(GuardrailResponseRuleRun run) {
        SlackSender.sendAlert(Context.accountId.get(), new CustomTextAlert(slackText(run)), toInteger(run.getActionConfig().get(CONFIG_SLACK_WEBHOOK_ID)));
    }

    private void postWebhook(GuardrailResponseRuleRun run) throws Exception {
        Integer webhookId = toInteger(run.getActionConfig().get(CONFIG_WEBHOOK_ID));
        CustomWebhook webhook = webhookId == null
            ? CustomWebhooksDao.instance.findOne(Filters.empty())
            : CustomWebhooksDao.instance.findOne(Filters.eq("_id", webhookId));
        if (webhook == null || StringUtils.isBlank(webhook.getUrl())) throw new Exception("No webhook is connected");
        Map<String, List<String>> headers = OriginalHttpRequest.buildHeadersMap(webhook.getHeaderString());
        boolean hasContentType = headers.keySet().stream().anyMatch("content-type"::equalsIgnoreCase);
        if (!hasContentType) headers.put("content-type", Collections.singletonList("application/json"));
        String method = webhook.getMethod() == null ? "POST" : webhook.getMethod().toString();
        OriginalHttpRequest request = new OriginalHttpRequest(webhook.getUrl(), webhook.getQueryParams(), method, webhookPayload(run), headers, "");
        OriginalHttpResponse response = ApiExecutor.sendRequest(request, true, null, false, new ArrayList<>());
        if (response == null) throw new Exception("The webhook did not respond");
        if (response.getStatusCode() >= 300) throw new Exception("The webhook returned " + response.getStatusCode());
    }

    private void sendEmail(GuardrailResponseRuleRun run) throws Exception {
        String secretKey = com.akto.onprem.Constants.getSendgridConfig().getSendgridSecretKey();
        if (StringUtils.isBlank(secretKey)) throw new Exception("SendGrid is not configured");
        Personalization personalization = new Personalization();
        int added = 0;
        for (String address : String.valueOf(run.getActionConfig().getOrDefault(CONFIG_EMAILS, "")).split("[,;\\s]+")) {
            if (added >= MAX_EMAILS) break;
            if (!address.contains("@")) continue;
            personalization.addTo(new Email(address));
            added++;
        }
        if (added == 0) throw new Exception("No valid email address in the rule");
        Mail mail = new Mail();
        mail.setFrom(new Email(EMAIL_FROM, EMAIL_FROM_NAME));
        personalization.setSubject(run.getSeverity() + " violation: " + run.getRuleName());
        mail.addPersonalization(personalization);
        mail.addContent(new Content("text/plain", plainText(run)));
        SendgridEmail.getInstance().send(mail);
    }

    private String slackText(GuardrailResponseRuleRun run) {
        StringBuilder text = new StringBuilder();
        text.append(":rotating_light: *Response rule* \"").append(escape(run.getRuleName())).append("\"\n");
        text.append("*").append(escape(run.getSeverity())).append("* violation");
        if (StringUtils.isNotBlank(run.getPolicy())) text.append(" · ").append(escape(run.getPolicy()));
        if (!StringUtils.isEmpty(run.getHost())) text.append("\nAgent: ").append(escape(run.getHost()));
        if (!StringUtils.isEmpty(run.getActor())) text.append("\nUser: ").append(escape(run.getActor()));
        String link = activityUrl(run);
        if (link != null) text.append("\n<").append(link).append("|View violation>");
        return text.toString();
    }

    private String plainText(GuardrailResponseRuleRun run) {
        StringBuilder text = new StringBuilder();
        text.append("Response rule: ").append(run.getRuleName()).append("\n");
        text.append("Severity: ").append(run.getSeverity()).append("\n");
        if (StringUtils.isNotBlank(run.getPolicy())) text.append("Policy: ").append(run.getPolicy()).append("\n");
        if (!StringUtils.isEmpty(run.getHost())) text.append("Agent: ").append(run.getHost()).append("\n");
        if (!StringUtils.isEmpty(run.getActor())) text.append("User: ").append(run.getActor()).append("\n");
        String link = activityUrl(run);
        if (link != null) text.append("View violation: ").append(link).append("\n");
        return text.toString();
    }

    private String webhookPayload(GuardrailResponseRuleRun run) {
        return new BasicDBObject("rule", run.getRuleName())
            .append("severity", run.getSeverity())
            .append("policy", run.getPolicy())
            .append("agent", run.getHost())
            .append("user", run.getActor())
            .append("eventId", run.getEventId())
            .append("refId", run.getRefId())
            .append("detectedAt", run.getDetectedAt())
            .append("link", activityUrl(run))
            .toJson();
    }

    private String activityUrl(GuardrailResponseRuleRun run) {
        boolean complete = StringUtils.isNoneEmpty(run.getRefId(), run.getEventType(), run.getActor(), run.getFilterId());
        if (!complete) return null;
        return dashboardBaseUrl() + ACTIVITY_PATH
            + "?refId=" + encode(run.getRefId())
            + "&eventType=" + encode(run.getEventType())
            + "&actor=" + encode(run.getActor())
            + "&filterId=" + encode(run.getFilterId());
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
