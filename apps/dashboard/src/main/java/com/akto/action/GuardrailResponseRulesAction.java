package com.akto.action;

import com.akto.dao.GuardrailResponseRulesDao;
import com.akto.dao.JiraIntegrationDao;
import com.akto.dao.ServiceNowIntegrationDao;
import com.akto.dao.context.Context;
import com.akto.dao.notifications.CustomWebhooksDao;
import com.akto.dao.notifications.SlackWebhooksDao;
import com.akto.dto.GuardrailResponseRule;
import com.akto.dto.GuardrailResponseRule.RuleAction;
import com.akto.dto.GuardrailResponseRule.RuleCondition;
import com.akto.dto.User;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.akto.util.enums.GlobalEnums.CONTEXT_SOURCE;
import com.mongodb.BasicDBObject;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GuardrailResponseRulesAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(GuardrailResponseRulesAction.class, LogDb.DASHBOARD);

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_RULES = 200;
    private static final int MAX_NAME_LENGTH = 120;
    private static final int MAX_CONDITIONS = 10;
    private static final int MAX_ACTIONS = 6;
    private static final int MAX_VALUES = 50;
    private static final int MAX_VALUE_LENGTH = 200;

    private static final Set<String> CONDITION_FIELDS = new HashSet<>(Arrays.asList(
        GuardrailResponseRule.CONDITION_SEVERITY, GuardrailResponseRule.CONDITION_POLICY,
        GuardrailResponseRule.CONDITION_ACCOUNT_TYPE, GuardrailResponseRule.CONDITION_SURFACE,
        GuardrailResponseRule.CONDITION_AGENT, GuardrailResponseRule.CONDITION_UNASSIGNED_HOURS));
    private static final Set<String> CONDITION_OPERATORS = new HashSet<>(Arrays.asList("is", "in"));
    private static final Set<String> ACTION_TYPES = new HashSet<>(Arrays.asList(
        GuardrailResponseRule.ACTION_CREATE_TICKET, GuardrailResponseRule.ACTION_ASSIGN,
        GuardrailResponseRule.ACTION_ALERT, GuardrailResponseRule.ACTION_IGNORE,
        GuardrailResponseRule.ACTION_COACH_EMPLOYEE, GuardrailResponseRule.ACTION_NOTIFY_MANAGER));

    @Setter private int skip;
    @Setter private int limit = DEFAULT_LIMIT;
    @Setter private String ruleId;
    @Setter private List<String> ruleIds;
    @Setter private String name;
    @Setter private boolean active;
    @Setter private List<RuleCondition> conditions;
    @Setter private List<RuleAction> actions;

    @Getter private List<GuardrailResponseRule> rules;
    @Getter private long total;
    @Getter private GuardrailResponseRule rule;
    @Getter private List<Map<String, Object>> destinations;

    public String fetchGuardrailResponseRules() {
        try {
            int pageSkip = Math.max(skip, 0);
            int pageLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
            rules = GuardrailResponseRulesDao.instance.findPage(pageSkip, pageLimit);
            total = GuardrailResponseRulesDao.instance.totalCount();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("fetchGuardrailResponseRules failed: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Unable to load response rules");
            return ERROR.toUpperCase();
        }
    }

    public String saveGuardrailResponseRule() {
        try {
            String validationError = validateRule();
            if (validationError != null) {
                addActionError(validationError);
                return ERROR.toUpperCase();
            }
            GuardrailResponseRulesDao.instance.createIndicesIfAbsent();
            User user = getSUser();
            String login = user != null ? user.getLogin() : null;
            int now = Context.now();

            if (StringUtils.isBlank(ruleId)) {
                if (GuardrailResponseRulesDao.instance.totalCount() >= MAX_RULES) {
                    addActionError("You can have at most " + MAX_RULES + " response rules");
                    return ERROR.toUpperCase();
                }
                GuardrailResponseRule created = new GuardrailResponseRule();
                created.setId(new ObjectId());
                created.setName(name.trim());
                created.setActive(active);
                created.setContextSource(currentContextSource());
                created.setConditions(conditions);
                created.setActions(actions);
                created.setCreatedBy(login);
                created.setCreatedTimestamp(now);
                created.setUpdatedBy(login);
                created.setUpdatedTimestamp(now);
                GuardrailResponseRulesDao.instance.insertOne(created);
                rule = created;
                return SUCCESS.toUpperCase();
            }

            ObjectId id = parseId(ruleId);
            if (id == null) {
                addActionError("Invalid rule id");
                return ERROR.toUpperCase();
            }
            Bson scope = Filters.and(Filters.eq("_id", id), GuardrailResponseRulesDao.instance.contextSourceFilter());
            if (GuardrailResponseRulesDao.instance.findOne(scope) == null) {
                addActionError("Rule not found");
                return ERROR.toUpperCase();
            }
            rule = GuardrailResponseRulesDao.instance.updateOne(scope, Updates.combine(
                Updates.set("name", name.trim()),
                Updates.set("active", active),
                Updates.set("conditions", conditions),
                Updates.set("actions", actions),
                Updates.set("updatedBy", login),
                Updates.set("updatedTimestamp", now)));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("saveGuardrailResponseRule failed: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Unable to save the response rule");
            return ERROR.toUpperCase();
        }
    }

    public String toggleGuardrailResponseRule() {
        try {
            ObjectId id = parseId(ruleId);
            if (id == null) {
                addActionError("Invalid rule id");
                return ERROR.toUpperCase();
            }
            Bson scope = Filters.and(Filters.eq("_id", id), GuardrailResponseRulesDao.instance.contextSourceFilter());
            User user = getSUser();
            rule = GuardrailResponseRulesDao.instance.updateOne(scope, Updates.combine(
                Updates.set("active", active),
                Updates.set("updatedBy", user != null ? user.getLogin() : null),
                Updates.set("updatedTimestamp", Context.now())));
            if (rule == null) {
                addActionError("Rule not found");
                return ERROR.toUpperCase();
            }
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("toggleGuardrailResponseRule failed: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Unable to update the response rule");
            return ERROR.toUpperCase();
        }
    }

    public String deleteGuardrailResponseRules() {
        try {
            if (ruleIds == null || ruleIds.isEmpty() || ruleIds.size() > MAX_LIMIT) {
                addActionError("Select between 1 and " + MAX_LIMIT + " rules");
                return ERROR.toUpperCase();
            }
            List<ObjectId> ids = new ArrayList<>();
            for (String value : ruleIds) {
                ObjectId id = parseId(value);
                if (id == null) {
                    addActionError("Invalid rule id");
                    return ERROR.toUpperCase();
                }
                ids.add(id);
            }
            GuardrailResponseRulesDao.instance.deleteAll(Filters.and(Filters.in("_id", ids), GuardrailResponseRulesDao.instance.contextSourceFilter()));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("deleteGuardrailResponseRules failed: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Unable to delete the response rules");
            return ERROR.toUpperCase();
        }
    }

    public String fetchResponseRuleDestinations() {
        try {
            destinations = new ArrayList<>();
            BasicDBObject any = new BasicDBObject();
            destinations.add(destination("JIRA", "Jira", JiraIntegrationDao.instance.count(any) > 0, "Create tickets from violations"));
            destinations.add(destination("SERVICENOW", "ServiceNow", ServiceNowIntegrationDao.instance.count(any) > 0, "Create incidents from violations"));
            destinations.add(destination("SLACK", "Slack", SlackWebhooksDao.instance.count(any) > 0, "Send alerts to a Slack channel"));
            destinations.add(destination("WEBHOOK", "Webhook", CustomWebhooksDao.instance.count(any) > 0, "Send violations to your SIEM or SOAR"));
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("fetchResponseRuleDestinations failed: " + e.getMessage(), LogDb.DASHBOARD);
            addActionError("Unable to load destinations");
            return ERROR.toUpperCase();
        }
    }

    private static Map<String, Object> destination(String id, String label, boolean connected, String detail) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("label", label);
        row.put("connected", connected);
        row.put("detail", detail);
        return row;
    }

    private static ObjectId parseId(String value) {
        if (StringUtils.isBlank(value) || !ObjectId.isValid(value)) return null;
        return new ObjectId(value);
    }

    private static CONTEXT_SOURCE currentContextSource() {
        CONTEXT_SOURCE source = Context.contextSource.get();
        return source == null ? CONTEXT_SOURCE.AGENTIC : source;
    }

    private String validateRule() {
        if (StringUtils.isBlank(name)) return "Rule name is required";
        if (name.trim().length() > MAX_NAME_LENGTH) return "Rule name must be at most " + MAX_NAME_LENGTH + " characters";
        if (conditions == null || conditions.isEmpty()) return "Add at least one condition";
        if (conditions.size() > MAX_CONDITIONS) return "A rule can have at most " + MAX_CONDITIONS + " conditions";
        for (RuleCondition condition : conditions) {
            if (condition == null || !CONDITION_FIELDS.contains(condition.getField())) return "Unknown condition";
            if (!CONDITION_OPERATORS.contains(condition.getOperator())) return "Unknown condition operator";
            List<String> values = condition.getValues();
            if (values == null || values.isEmpty() || values.size() > MAX_VALUES) return "Each condition needs a value";
            for (String value : values) {
                if (StringUtils.isBlank(value) || value.length() > MAX_VALUE_LENGTH) return "Invalid condition value";
            }
        }
        if (actions == null || actions.isEmpty()) return "Add at least one action";
        if (actions.size() > MAX_ACTIONS) return "A rule can have at most " + MAX_ACTIONS + " actions";
        for (RuleAction action : actions) {
            if (action == null || !ACTION_TYPES.contains(action.getType())) return "Unknown action";
            if (action.getConfig() == null) action.setConfig(new LinkedHashMap<>());
        }
        return null;
    }
}
