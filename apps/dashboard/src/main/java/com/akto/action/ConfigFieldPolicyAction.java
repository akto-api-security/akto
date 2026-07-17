package com.akto.action;

import com.akto.dao.config_field_policy.ConfigFieldPolicyDao;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.dto.config_field_policy.ConfigFieldPolicy;
import com.akto.log.LoggerMaker;
import com.akto.log.LoggerMaker.LogDb;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.apache.commons.lang3.StringUtils;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.List;

public class ConfigFieldPolicyAction extends UserAction {

    private static final LoggerMaker loggerMaker = new LoggerMaker(ConfigFieldPolicyAction.class, LogDb.DASHBOARD);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final List<String> SUPPORTED_TOOL_NAMES = java.util.Collections.singletonList("claude");
    private static final int MAX_FIELD_PATH_LENGTH = 200;

    private ConfigFieldPolicy policy;
    private String hexId; // null = create, set = update

    private List<ConfigFieldPolicy> configFieldPolicies;
    private long total;

    private List<String> policyIds; // for bulk delete

    private int skip;
    private int limit;

    public String fetchConfigFieldPolicies() {
        try {
            int effectiveLimit = (limit > 0 && limit <= 100) ? limit : 50;
            this.configFieldPolicies = ConfigFieldPolicyDao.instance.findAll(
                    Filters.empty(), skip, effectiveLimit, Sorts.descending(ConfigFieldPolicy.CREATED_AT));
            this.total = ConfigFieldPolicyDao.instance.getMCollection().countDocuments();
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error fetching config field policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    private String validatePolicy() {
        if (policy == null) {
            return "policy is required";
        }
        if (StringUtils.isBlank(policy.getPolicyName())) {
            return "policyName is required";
        }
        if (StringUtils.isBlank(policy.getFieldPath())) {
            return "fieldPath is required";
        }
        if (policy.getFieldPath().length() > MAX_FIELD_PATH_LENGTH) {
            return "fieldPath must be at most " + MAX_FIELD_PATH_LENGTH + " characters";
        }
        if (policy.getFieldPath().startsWith(".") || policy.getFieldPath().endsWith(".")) {
            return "fieldPath must not start or end with a dot";
        }
        if (StringUtils.isBlank(policy.getToolName()) || !SUPPORTED_TOOL_NAMES.contains(policy.getToolName())) {
            return "toolName must be one of: " + SUPPORTED_TOOL_NAMES;
        }
        if (StringUtils.isBlank(policy.getEnforcedValueJson())) {
            return "enforcedValueJson is required";
        }
        try {
            objectMapper.readTree(policy.getEnforcedValueJson());
        } catch (Exception e) {
            return "enforcedValueJson must be valid JSON";
        }
        return null;
    }

    public String createConfigFieldPolicy() {
        try {
            String validationError = validatePolicy();
            if (validationError != null) {
                addActionError(validationError);
                return ERROR.toUpperCase();
            }

            User user = getSUser();
            int currentTime = Context.now();
            boolean isUpdate = StringUtils.isNotBlank(hexId);

            Bson filter = isUpdate
                    ? Filters.eq(ConfigFieldPolicy.ID, new ObjectId(hexId))
                    : Filters.eq(ConfigFieldPolicy.POLICY_NAME, policy.getPolicyName());

            List<Bson> updates = new ArrayList<>();
            updates.add(Updates.set(ConfigFieldPolicy.POLICY_NAME, policy.getPolicyName()));
            updates.add(Updates.set(ConfigFieldPolicy.DESCRIPTION, policy.getDescription()));
            updates.add(Updates.set(ConfigFieldPolicy.STATUS,
                    StringUtils.isNotBlank(policy.getStatus()) ? policy.getStatus() : "ACTIVE"));
            updates.add(Updates.set(ConfigFieldPolicy.TOOL_NAME, policy.getToolName()));
            updates.add(Updates.set(ConfigFieldPolicy.FIELD_PATH, policy.getFieldPath()));
            updates.add(Updates.set(ConfigFieldPolicy.ENFORCED_VALUE_JSON, policy.getEnforcedValueJson()));
            updates.add(Updates.set(ConfigFieldPolicy.DEVICES, policy.getDevices()));
            updates.add(Updates.set(ConfigFieldPolicy.UPDATED_AT, currentTime));
            updates.add(Updates.set(ConfigFieldPolicy.UPDATED_BY, user.getLogin()));
            updates.add(Updates.setOnInsert(ConfigFieldPolicy.CREATED_AT, currentTime));
            updates.add(Updates.setOnInsert(ConfigFieldPolicy.CREATED_BY, user.getLogin()));

            ConfigFieldPolicyDao.instance.getMCollection().updateOne(
                    filter, Updates.combine(updates), new UpdateOptions().upsert(true));

            loggerMaker.info((isUpdate ? "Updated" : "Created") + " config field policy: "
                    + policy.getPolicyName() + " by user: " + user.getLogin());

            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error creating config field policy: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    public String deleteConfigFieldPolicies() {
        try {
            if (policyIds == null || policyIds.isEmpty()) {
                addActionError("policyIds is required");
                return ERROR.toUpperCase();
            }
            List<ObjectId> objectIds = new ArrayList<>();
            for (String id : policyIds) {
                objectIds.add(new ObjectId(id));
            }
            ConfigFieldPolicyDao.instance.getMCollection().deleteMany(
                    Filters.in(ConfigFieldPolicy.ID, objectIds));

            loggerMaker.info("Deleted " + policyIds.size() + " config field policies by user: " + getSUser().getLogin());
            return SUCCESS.toUpperCase();
        } catch (Exception e) {
            loggerMaker.errorAndAddToDb("Error deleting config field policies: " + e.getMessage(), LogDb.DASHBOARD);
            return ERROR.toUpperCase();
        }
    }

    public ConfigFieldPolicy getPolicy() {
        return policy;
    }

    public void setPolicy(ConfigFieldPolicy policy) {
        this.policy = policy;
    }

    public void setHexId(String hexId) {
        this.hexId = hexId;
    }

    public List<ConfigFieldPolicy> getConfigFieldPolicies() {
        return configFieldPolicies;
    }

    public long getTotal() {
        return total;
    }

    public void setPolicyIds(List<String> policyIds) {
        this.policyIds = policyIds;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
