package com.akto.action;

import com.mongodb.BasicDBObject;
import com.opensymphony.xwork2.Action;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/** Request checks of the live smart-testing endpoints that reject before touching the database. */
public class SmartTestingRunActionTest {

    private static final String ERROR = Action.ERROR.toUpperCase();

    @Test
    public void startRejectsMissingOrBlankRunKey() {
        DbAction action = new DbAction();
        assertEquals(ERROR, action.startSmartTestingRun());

        action = new DbAction();
        action.setSmartTestingRun(new BasicDBObject("runKey", "  ").append("method", "GET"));
        assertEquals(ERROR, action.startSmartTestingRun());
        assertFalse(action.getActionErrors().isEmpty());
    }

    @Test
    public void insertEventsRejectsTooManyEvents() {
        List<BasicDBObject> events = new ArrayList<>();
        for (int i = 0; i <= DbAction.MAX_SMART_TESTING_EVENTS_PER_CALL; i++) {
            events.add(new BasicDBObject("seq", i + 1).append("phase", "plan").append("content", "x"));
        }
        DbAction action = new DbAction();
        action.setRunKey("run-1");
        action.setSmartTestingEvents(events);
        assertEquals(ERROR, action.insertSmartTestingEvents());
        assertFalse(action.getActionErrors().isEmpty());
    }

    @Test
    public void insertEventsRejectsBlankRunKey() {
        DbAction action = new DbAction();
        assertEquals(ERROR, action.insertSmartTestingEvents());
    }

    @Test
    public void finishRejectsUnknownStatus() {
        for (String status : new String[]{null, "", "DONE", "RUNNING", "completed"}) {
            DbAction action = new DbAction();
            action.setRunKey("run-1");
            action.setSmartTestingRunStatus(status);
            assertEquals("status " + status, ERROR, action.finishSmartTestingRun());
        }
    }

    @Test
    public void fetchMessagesRejectsBlankRunKey() {
        DbAction action = new DbAction();
        action.setMessagesAfter(123);
        assertEquals(ERROR, action.fetchSmartTestingMessages());
    }
}
