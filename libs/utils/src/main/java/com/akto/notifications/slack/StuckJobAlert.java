package com.akto.notifications.slack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StuckJobAlert extends SlackAlerts {

    private static final String HEADER_TITLE = ":warning: Stuck Job Alert - Job Not Completed";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int accountId;
    private final String jobStatus;
    private final String projectId;
    private final String summaryId;
    private final String testingRunId;
    private final long runningForMinutes;

    public StuckJobAlert(int accountId, String jobStatus, String projectId,
                         String summaryId, String testingRunId, long runningForMinutes) {
        super(SlackAlertType.STUCK_JOB_ALERT);
        this.accountId = accountId;
        this.jobStatus = jobStatus;
        this.projectId = projectId;
        this.summaryId = summaryId;
        this.testingRunId = testingRunId;
        this.runningForMinutes = runningForMinutes;
    }

    @Override
    public String toJson() {
        List<Map<String, Object>> blocks = new ArrayList<>();

        blocks.add(headerBlock(HEADER_TITLE));
        blocks.add(dividerBlock());

        List<Map<String, String>> fields = new ArrayList<>();
        fields.add(mrkdwnField("*Account ID:*\n" + accountId));
        fields.add(mrkdwnField("*Job Status:*\n" + jobStatus));

        if (projectId != null && !projectId.isEmpty()) {
            fields.add(mrkdwnField("*Project ID:*\n" + projectId));
        }
        if (summaryId != null && !summaryId.isEmpty()) {
            fields.add(mrkdwnField("*Summary ID:*\n" + summaryId));
        }
        if (testingRunId != null && !testingRunId.isEmpty()) {
            fields.add(mrkdwnField("*Testing Run ID:*\n" + testingRunId));
        }

        fields.add(mrkdwnField("*Running For:*\n" + runningForMinutes + " minutes"));

        blocks.add(sectionBlock(fields));
        blocks.add(dividerBlock());

        long unixTime = System.currentTimeMillis() / 1000L;
        String dateText = "<!date^" + unixTime + "^Detected at {date_pretty} {time_secs}|Detected at unknown time>";
        blocks.add(contextBlock(dateText));

        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("blocks", blocks);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("attachments", Collections.singletonList(attachment));

        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static Map<String, Object> headerBlock(String title) {
        Map<String, Object> textObj = new LinkedHashMap<>();
        textObj.put("type", "plain_text");
        textObj.put("text", title);

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "header");
        block.put("text", textObj);
        return block;
    }

    private static Map<String, Object> dividerBlock() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "divider");
        return block;
    }

    private static Map<String, Object> sectionBlock(List<Map<String, String>> fields) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "section");
        block.put("fields", fields);
        return block;
    }

    private static Map<String, Object> contextBlock(String text) {
        Map<String, String> textObj = new LinkedHashMap<>();
        textObj.put("type", "mrkdwn");
        textObj.put("text", text);

        Map<String, Object> block = new LinkedHashMap<>();
        block.put("type", "context");
        block.put("elements", Collections.singletonList(textObj));
        return block;
    }

    private static Map<String, String> mrkdwnField(String text) {
        Map<String, String> field = new LinkedHashMap<>();
        field.put("type", "mrkdwn");
        field.put("text", text);
        return field;
    }
}
