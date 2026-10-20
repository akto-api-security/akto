package com.akto.utils.search;

import com.akto.utils.elasticsearch.AgentQueryRecord;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TraceSessionBackfillTest {

    private static final String BEDROCK = "bedrock-runtime.us-east-1.amazonaws.com";
    private static final String TASK = "{\"messages\":[{\"role\":\"user\",\"content\":\"ORION task\"}]}";
    private static final String SUMMARY = "{\"messages\":[{\"role\":\"user\",\"content\":\"Summarize video\"}]}";
    private static final long T0 = 1792397986000L;
    private static final long MINUTE = 60_000L;

    private final TraceSessionBackfill backfill = new TraceSessionBackfill();

    @Test
    public void samePromptWithinGapOfLastCall_isOneConversation() {
        String first = backfill.sessionIdFor(record(BEDROCK, TASK, T0));
        assertEquals(first, backfill.sessionIdFor(record(BEDROCK, TASK, T0 + 15 * MINUTE)));
        assertEquals(first, backfill.sessionIdFor(record(BEDROCK, TASK, T0 + 30 * MINUTE)));
        assertTrue(first.startsWith(TraceSessionBackfill.SESSION_ID_PREFIX));
    }

    @Test
    public void samePromptAfterGap_isNewConversation() {
        String first = backfill.sessionIdFor(record(BEDROCK, TASK, T0));
        assertNotEquals(first, backfill.sessionIdFor(record(BEDROCK, TASK, T0 + 21 * MINUTE)));
    }

    @Test
    public void differentPromptOrService_isSeparate() {
        String first = backfill.sessionIdFor(record(BEDROCK, TASK, T0));
        assertNotEquals(first, backfill.sessionIdFor(record(BEDROCK, SUMMARY, T0)));
        assertNotEquals(first, backfill.sessionIdFor(record("bedrock-runtime.us-west-2.amazonaws.com", TASK, T0)));
    }

    private static AgentQueryRecord record(String serviceId, String queryPayload, long timestampMs) {
        return new AgentQueryRecord("id", 1703087742, serviceId, "", "admin@example.com", "",
            queryPayload, "{}", timestampMs, 0, 0, "", "", false, "", "");
    }
}
