package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import java.util.List;

import static com.akto.notifications.slack.SlackAlertType.NEW_USER_JOINING_ALERT;

public class NewUserJoiningAlert extends SlackAlerts {
    private final String color;
    private final List<FieldsModel> fieldsModelList;

    public NewUserJoiningAlert(List<FieldsModel> fieldsModelList) {
        super(NEW_USER_JOINING_ALERT);
        this.color = "#6D3BEF";
        this.fieldsModelList = fieldsModelList;
    }

    @Override
    public String toJson() {
        long unixTime = System.currentTimeMillis() / 1000L;
        String dateText = "<!date^"+unixTime+"^{date_pretty} at {time_secs}|Month Date, Year at Time TimeZone>"; // Get time from unix timestamp or just get fallback text

        BasicDBList blocksList = new BasicDBList();
        blocksList.add(createHeader("\uD83D\uDC64 New user joined the org")); // \uD83D\uDC64 -> is an emoji
        blocksList.add(createTextContext("New user has joined your organization."));
        blocksList.add(createFieldSection(fieldsModelList));
        blocksList.add(createTextContext(dateText));

        BasicDBObject blockObj = new BasicDBObject("blocks", blocksList).append("color", color);

        return toAttachment(blockObj).toJson();
    }
}
