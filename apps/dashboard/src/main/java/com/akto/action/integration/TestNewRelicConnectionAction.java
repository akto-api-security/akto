package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dto.User;
import com.akto.integration.NewRelicConfiguration;
import com.akto.integration.NewRelicApiClient;

/**
 * Struts2 Action to test NewRelic connection
 */
public class TestNewRelicConnectionAction extends UserAction {

    private String apiKey;
    private String accountId;
    private String region;

    private Result result;

    public static class Result {
        public boolean success;
        public String message;
        public String error;
        public ResponseData data;

        public Result() {
        }

        public Result(boolean success, String message, String error) {
            this.success = success;
            this.message = message;
            this.error = error;
        }
    }

    public static class ResponseData {
        public String accountName;
        public String accountId;
        public String region;

        public ResponseData(String accountName, String accountId, String region) {
            this.accountName = accountName;
            this.accountId = accountId;
            this.region = region;
        }
    }

    @Override
    public String execute() {
        try {
            User user = getSUser();
            int orgId = Context.accountId.get();

            Context.accountId.set(orgId);
            Context.userId.set(user.getId());

            if (!validateInput()) {
                return INPUT.toUpperCase();
            }

            // Create NewRelicApiClient and test connection
            NewRelicConfiguration config = NewRelicConfiguration.getInstance();
            NewRelicApiClient apiClient = new NewRelicApiClient(config);

            try {
                boolean isValid = apiClient.testConnection(this.apiKey, this.accountId, this.region);
                if (!isValid) {
                    result = new Result(false, "Connection failed", "Invalid credentials or unable to reach NewRelic API");
                    return INPUT.toUpperCase();
                }
            } catch (Exception e) {
                result = new Result(false, "Connection failed", e.getMessage());
                return INPUT.toUpperCase();
            }

            // Connection successful
            String accountName = "NewRelic Account";  // Default name

            result = new Result();
            result.success = true;
            result.message = "Connection successful";
            result.data = new ResponseData(accountName, this.accountId, this.region);

            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            result = new Result(false, "Connection failed", e.getMessage());
            return INPUT.toUpperCase();
        }
    }

    private boolean validateInput() {
        boolean valid = true;

        if (apiKey == null || !apiKey.matches("^(.*)$")) {
            addActionError("apiKey format invalid");
            valid = false;
        }

        if (accountId == null || !accountId.matches("^\\d+$")) {
            addActionError("accountId must be numeric");
            valid = false;
        }

        if (region == null || (!region.equals("US") && !region.equals("EU"))) {
            addActionError("region must be US or EU");
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
