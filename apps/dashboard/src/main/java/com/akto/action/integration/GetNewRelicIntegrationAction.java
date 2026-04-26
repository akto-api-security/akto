package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.integration.NewRelicIntegrationDao;
import com.akto.dto.integration.NewRelicIntegration;
import com.akto.dto.User;
import com.mongodb.client.model.Filters;

/**
 * Struts2 Action to get NewRelic integration configuration
 */
public class GetNewRelicIntegrationAction extends UserAction {

    private Result result;

    public static class Result {
        public boolean success;
        public ResponseData data;

        public Result(boolean success, ResponseData data) {
            this.success = success;
            this.data = data;
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

            // Find NewRelic integration for this organization
            NewRelicIntegration integration = NewRelicIntegrationDao.instance.findOne(
                Filters.eq(NewRelicIntegration.ORG_ID, orgId)
            );

            if (integration == null) {
                result = new Result(true, null);
            } else {
                ResponseData data = new ResponseData(
                    integration.getAccountId(),
                    integration.getRegion(),
                    integration.getEnabled(),
                    integration.getLastSyncTime()
                );
                result = new Result(true, data);
            }

            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            addActionError("Failed to retrieve configuration");
            return ERROR.toUpperCase();
        }
    }

    public Result getResult() { return result; }
}
