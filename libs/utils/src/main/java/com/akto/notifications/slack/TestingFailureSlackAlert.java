package com.akto.notifications.slack;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;

import static com.akto.notifications.slack.SlackAlertType.TESTING_FAILURE_ALERT;

public class TestingFailureSlackAlert extends SlackAlerts {

    private static final int SLACK_SECTION_TEXT_MAX = 2800;

    private final String color;
    private final String title;
    private final String detailMessage;

    public TestingFailureSlackAlert(String title, String detailMessage) {
        super(TESTING_FAILURE_ALERT);
        this.color = "#D72C0D";
        this.title = title;
        String safe = detailMessage == null ? "" : detailMessage;
        if (safe.length() > SLACK_SECTION_TEXT_MAX) {
            safe = safe.substring(0, SLACK_SECTION_TEXT_MAX) + "…";
        }
        this.detailMessage = safe;
    }

    @Override
    public String toJson() {
        long unixTime = System.currentTimeMillis() / 1000L;
        String dateText = "<!date^" + unixTime + "^{date_pretty} at {time_secs}|Month Date, Year at Time TimeZone>";

        BasicDBList blocksList = new BasicDBList();
        blocksList.add(createHeader("⚠️ " + title));
        blocksList.add(createTextSection(detailMessage));
        blocksList.add(createTextContext(dateText));

        BasicDBObject blockObj = new BasicDBObject("blocks", blocksList).append("color", color);
        return toAttachment(blockObj).toJson();
    }
}
