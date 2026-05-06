package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.List;

import static com.akto.notifications.slack.SlackAlertType.USER_BLOCKED_NO_SCOPE_ACCESS_ALERT;

/**
 * Slack alert for users denied access to a specific product scope due to NO_ACCESS role
 * Sent when RoleAccessInterceptor blocks access to a scope
 */
public class UserBlockedNoScopeAccessAlert extends SlackAlerts {
    private final String color;
    private List<FieldsModel> fieldsModelList;

    /**
     * @param userEmail the email of the user denied access
     * @param scopeDisplayName the user-friendly display name of the denied scope (e.g., "Akto ATLAS")
     * @param scopeCode the scope code (e.g., "ENDPOINT", "AGENTIC")
     * @param accountId the account ID where access was denied
     */
    public UserBlockedNoScopeAccessAlert(String userEmail, String scopeDisplayName, String scopeCode, String accountId) {
        super(USER_BLOCKED_NO_SCOPE_ACCESS_ALERT);
        this.color = "#FF6B6B"; // Red color for warning/error
        init(userEmail, scopeDisplayName, scopeCode, accountId);
    }

    private void init(String userEmail, String scopeDisplayName, String scopeCode, String accountId) {
        fieldsModelList = new ArrayList<>();
        fieldsModelList.add(new FieldsModel("User Email", userEmail));
        fieldsModelList.add(new FieldsModel("Denied Scope", scopeDisplayName != null ? scopeDisplayName : scopeCode));
        fieldsModelList.add(new FieldsModel("Scope Code", scopeCode));
        fieldsModelList.add(new FieldsModel("Account ID", accountId));
        fieldsModelList.add(new FieldsModel("Issue", "User has NO_ACCESS role for this product scope"));
    }

    @Override
    public String toJson() {
        long unixTime = System.currentTimeMillis() / 1000L;
        String dateText = "<!date^"+unixTime+"^{date_pretty} at {time_secs}|Month Date, Year at Time TimeZone>";

        BasicDBList blocksList = new BasicDBList();
        blocksList.add(createHeader("🔐 User Blocked - No Scope Access"));
        blocksList.add(createTextContext("A user attempted to access a product scope they don't have permission for."));
        blocksList.add(createFieldSection(fieldsModelList));
        blocksList.add(createTextContext("⚠️ *Action Required:* Check if this is expected. Update user roles if access should be granted."));
        blocksList.add(createTextContext(dateText));

        BasicDBObject blockObj = new BasicDBObject("blocks", blocksList).append("color", color);

        return toAttachment(blockObj).toJson();
    }
}
