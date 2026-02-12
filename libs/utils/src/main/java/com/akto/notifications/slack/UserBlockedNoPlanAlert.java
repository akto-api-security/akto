package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.ArrayList;
import java.util.List;

import static com.akto.notifications.slack.SlackAlertType.USER_BLOCKED_NO_PLAN_ALERT;

public class UserBlockedNoPlanAlert extends SlackAlerts {
    private final String color;
    private List<FieldsModel> fieldsModelList;

    public UserBlockedNoPlanAlert(String userEmail, String organizationId) {
        super(USER_BLOCKED_NO_PLAN_ALERT);
        this.color = "#FF6B6B"; // Red color for warning/error
        init(userEmail, organizationId, null);
    }
    
    public UserBlockedNoPlanAlert(String userEmail, String organizationId, String invalidPlanType) {
        super(USER_BLOCKED_NO_PLAN_ALERT);
        this.color = "#FF6B6B"; // Red color for warning/error
        init(userEmail, organizationId, invalidPlanType);
    }

    private void init(String userEmail, String organizationId, String invalidPlanType) {
        fieldsModelList = new ArrayList<>();
        fieldsModelList.add(new FieldsModel("User Email", userEmail));
        fieldsModelList.add(new FieldsModel("Organization ID", organizationId));
        
        String issueDescription;
        if (invalidPlanType == null || invalidPlanType.isEmpty()) {
            issueDescription = "Plan type is null or empty";
        } else {
            issueDescription = "Invalid plan type: '" + invalidPlanType + "' (allowed: enterprise, professional, trial)";
        }
        fieldsModelList.add(new FieldsModel("Issue", issueDescription));
    }

    @Override
    public String toJson() {
        long unixTime = System.currentTimeMillis() / 1000L;
        String dateText = "<!date^"+unixTime+"^{date_pretty} at {time_secs}|Month Date, Year at Time TimeZone>"; // Get time from unix timestamp or just get fallback text

        BasicDBList blocksList = new BasicDBList();
        blocksList.add(createHeader("🚫 User Blocked - Invalid Plan Type"));
        blocksList.add(createTextContext("A user has been blocked from accessing the dashboard due to invalid plan type."));
        blocksList.add(createFieldSection(fieldsModelList));
        blocksList.add(createTextContext("⚠️ *Action Required:* Please check organization configuration and assign proper plan type."));
        blocksList.add(createTextContext(dateText));

        BasicDBObject blockObj = new BasicDBObject("blocks", blocksList).append("color", color);

        return toAttachment(blockObj).toJson();
    }
}