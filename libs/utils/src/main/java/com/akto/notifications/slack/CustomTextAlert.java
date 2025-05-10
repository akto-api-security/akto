package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import static com.akto.notifications.slack.SlackAlertType.CUSTOM_TEXT_ALERT;

public class CustomTextAlert extends SlackAlerts {
    private final String text;

    public CustomTextAlert(String text) {
        super(CUSTOM_TEXT_ALERT);
        this.text = text;
    }


    @Override
    public String toJson() {
        long unixTime = System.currentTimeMillis() / 1000L;
        String dateText = "<!date^"+unixTime+"^{date_pretty} at {time_secs}|Month Date, Year at Time TimeZone>"; // Get time from unix timestamp or just get fallback text

        BasicDBList blocksList = new BasicDBList();
        blocksList.add(createTextContext(text));
        blocksList.add(createTextContext(dateText));

        BasicDBObject blockObj = new BasicDBObject("blocks", blocksList);

        return toAttachment(blockObj).toJson();
    }
}
