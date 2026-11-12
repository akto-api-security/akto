package com.akto.action.testing;

import com.akto.action.UserAction;
import com.akto.dao.context.Context;
import com.akto.dao.testing.SmartTestingEventDao;
import com.akto.dao.testing.SmartTestingMessageDao;
import com.akto.dao.testing.SmartTestingRunDao;
import com.akto.dto.User;
import com.akto.dto.testing.SmartTestingEvent;
import com.akto.dto.testing.SmartTestingMessage;
import com.akto.dto.testing.SmartTestingRun;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The live view of a test run's smart testing: every API's agent steps in one conversation, and messages from the
 * person watching, which every agent of the run reads (including agents of APIs that start later).
 */
public class SmartTestingLiveAction extends UserAction {

    static final int MAX_RUNS = 500;
    static final int MAX_EVENTS = 2000;
    static final int MAX_MESSAGES = 200;
    static final int MAX_MESSAGE_CHARS = 2000;

    // request
    private String testingRunResultSummaryHexId;
    private int afterTs; // events at or after this time (seconds); the client drops the ones it already has
    private String text;

    // response
    private List<SmartTestingRun> smartTestingRuns;
    private List<SmartTestingEvent> smartTestingEvents;
    private List<SmartTestingMessage> smartTestingMessages;

    private String summaryId() {
        return StringUtils.trimToEmpty(testingRunResultSummaryHexId);
    }

    /** The run's APIs (newest first), their steps since afterTs, and the messages sent to the run. */
    public String fetchSmartTestingLive() {
        if (summaryId().isEmpty()) {
            addActionError("Test run summary is required");
            return ERROR.toUpperCase();
        }
        smartTestingRuns = SmartTestingRunDao.instance.findAll(
                Filters.eq(SmartTestingRun.TESTING_RUN_RESULT_SUMMARY_ID, summaryId()),
                0, MAX_RUNS, Sorts.descending(SmartTestingRun.STARTED_TS));
        List<String> runKeys = new ArrayList<>();
        for (SmartTestingRun run : smartTestingRuns) {
            runKeys.add(run.getRunKey());
        }
        smartTestingEvents = runKeys.isEmpty() ? new ArrayList<>() : SmartTestingEventDao.instance.findAll(
                Filters.and(Filters.in(SmartTestingEvent.RUN_KEY, runKeys), Filters.gte(SmartTestingEvent.TIMESTAMP, Math.max(afterTs, 0))),
                0, MAX_EVENTS, Sorts.ascending(SmartTestingEvent.TIMESTAMP, SmartTestingEvent.SEQ));
        // the newest MAX_MESSAGES, oldest first
        smartTestingMessages = SmartTestingMessageDao.instance.findAll(
                Filters.eq(SmartTestingMessage.RUN_KEY, SmartTestingMessage.summaryRunKey(summaryId())),
                0, MAX_MESSAGES, Sorts.descending(SmartTestingMessage.TIMESTAMP));
        Collections.reverse(smartTestingMessages);
        return SUCCESS.toUpperCase();
    }

    /** A message to every agent of the run: more context, or another direction. */
    public String sendSmartTestingRunMessage() {
        String message = StringUtils.trimToEmpty(text);
        if (summaryId().isEmpty() || message.isEmpty()) {
            addActionError("Test run summary and message are required");
            return ERROR.toUpperCase();
        }
        if (message.length() > MAX_MESSAGE_CHARS) {
            addActionError("Message is longer than " + MAX_MESSAGE_CHARS + " characters");
            return ERROR.toUpperCase();
        }
        User user = getSUser();
        SmartTestingMessage doc = new SmartTestingMessage();
        doc.setRunKey(SmartTestingMessage.summaryRunKey(summaryId()));
        doc.setKind(SmartTestingMessage.Kind.MESSAGE);
        doc.setText(message);
        doc.setTimestamp(Context.now());
        doc.setCreatedBy(user == null ? null : user.getLogin());
        SmartTestingMessageDao.instance.insertOne(doc);
        return SUCCESS.toUpperCase();
    }

    public void setTestingRunResultSummaryHexId(String testingRunResultSummaryHexId) { this.testingRunResultSummaryHexId = testingRunResultSummaryHexId; }
    public void setAfterTs(int afterTs) { this.afterTs = afterTs; }
    public void setText(String text) { this.text = text; }

    public List<SmartTestingRun> getSmartTestingRuns() { return smartTestingRuns; }
    public List<SmartTestingEvent> getSmartTestingEvents() { return smartTestingEvents; }
    public List<SmartTestingMessage> getSmartTestingMessages() { return smartTestingMessages; }
}
