package com.akto.action.integration;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.integration.NewRelicIntegrationDao;
import com.akto.dto.integration.NewRelicIntegration;
import com.akto.dto.User;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;

/**
 * Struts2 Action to disable NewRelic integration
 */
public class DisableNewRelicIntegrationAction extends UserAction {

    private Result result;

    public static class Result {
        public boolean success;
        public String message;
        public String error;

        public Result() {
        }

        public Result(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public Result(boolean success, String message, String error) {
            this.success = success;
            this.message = message;
            this.error = error;
        }
    }

    @Override
    public String execute() {
        try {
            User user = getSUser();
            int orgId = Context.accountId.get();

            Context.accountId.set(orgId);
            Context.userId.set(user.getId());

            NewRelicIntegration integration = NewRelicIntegrationDao.instance.findOne(
                Filters.eq(NewRelicIntegration.ORG_ID, orgId)
            );

            if (integration == null) {
                result = new Result(false, null, "No NewRelic integration configured for this organization");
                return "NOT_FOUND";
            }

            // Disable integration by updating enabled flag
            NewRelicIntegrationDao.instance.updateOne(
                Filters.eq(NewRelicIntegration.ORG_ID, orgId),
                Updates.combine(
                    Updates.set(NewRelicIntegration.ENABLED, false),
                    Updates.set(NewRelicIntegration.UPDATED_AT, System.currentTimeMillis())
                )
            );

            result = new Result(true, "Integration disabled");
            return SUCCESS.toUpperCase();

        } catch (Exception e) {
            addActionError("Failed to disable integration");
            return ERROR.toUpperCase();
        }
    }

    public Result getResult() { return result; }
}
