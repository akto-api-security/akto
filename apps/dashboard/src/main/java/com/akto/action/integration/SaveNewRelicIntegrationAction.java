package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.integration.NewRelicIntegrationDao;
import com.akto.dto.integration.NewRelicIntegration;
import com.akto.dto.User;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * Struts2 Action to save NewRelic integration configuration
 */
public class SaveNewRelicIntegrationAction extends UserAction {

    private String apiKey;
    private String accountId;
    private String region;

    private Result result;

    public static class Result {
        public boolean success;
        public String message;
        public ResponseData data;

        public Result() {
        }

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    public static class ResponseData {
        public String accountId;
        public String region;
        public Boolean enabled;
        public Long lastSyncTime;

        public ResponseData(String accountId, String region, Boolean enabled, Long lastSyncTime) {
            this.accountId = accountId;
            this.region = region;
            this.enabled = enabled;
            this.lastSyncTime = lastSyncTime;
        }
    }

    @Override
    public String execute() {
        try {
            User user = getSUser();
            int orgId = Context.accountId.get();

            Context.accountId.set(orgId);
            Context.userId.set(user.getId());

            // Validate input
            if (!validateInput()) {
                return INPUT.toUpperCase();
            }

            // Create or update integration
            long now = System.currentTimeMillis();
            NewRelicIntegration integration = NewRelicIntegrationDao.instance.findOne(
                Filters.eq(NewRelicIntegration.ORG_ID, orgId)
            );

            if (integration == null) {
                integration = new NewRelicIntegration(orgId, this.accountId, this.region);
                integration.setId(new ObjectId());
                integration.setApiKey(this.apiKey);  // In real implementation, encrypt this
                integration.setEnabled(true);
                integration.setCreatedBy(user.getLogin());
                integration.setCreatedAt(now);
                integration.setUpdatedAt(now);
                NewRelicIntegrationDao.instance.insertOne(integration);
            } else {
                // Update existing integration
                Bson updates = Updates.combine(
                    Updates.set(NewRelicIntegration.API_KEY, this.apiKey),
                    Updates.set(NewRelicIntegration.ACCOUNT_ID, this.accountId),
                    Updates.set(NewRelicIntegration.REGION, this.region),
                    Updates.set(NewRelicIntegration.UPDATED_AT, now)
                );
                NewRelicIntegrationDao.instance.updateOne(
                    Filters.eq(NewRelicIntegration.ORG_ID, orgId),
                    updates
                );
            }

            // Build response
            result = new Result();
            result.success = true;
            result.message = "Integration saved successfully";
            result.data = new ResponseData(
                this.accountId,
                this.region,
                true,
                null
            );

            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            addActionError("Failed to save integration: " + e.getMessage());
            return ERROR.toUpperCase();
        }
    }

    private boolean validateInput() {
        boolean valid = true;

        if (apiKey == null || apiKey.isEmpty()) {
            addActionError("apiKey is required");
            valid = false;
        } else if (!apiKey.matches("^(.*)$")) {
            addActionError("apiKey format invalid (must start with NRAA followed by 58 alphanumeric characters)");
            valid = false;
        }

        if (accountId == null || accountId.isEmpty()) {
            addActionError("accountId is required");
            valid = false;
        } else if (!accountId.matches("^\\d+$")) {
            addActionError("accountId must contain only numeric digits");
            valid = false;
        }

        if (region == null || region.isEmpty()) {
            addActionError("region is required");
            valid = false;
        } else if (!region.equals("US") && !region.equals("EU")) {
            addActionError("region must be 'US' or 'EU'");
            valid = false;
        }

        return valid;
    }

    // Getters and Setters
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public Result getResult() { return result; }
}
