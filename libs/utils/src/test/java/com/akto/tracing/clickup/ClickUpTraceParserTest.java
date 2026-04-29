package com.akto.tracing.clickup;

import com.akto.dto.tracing.Trace;
import com.akto.tracing.TraceParseResult;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClickUpTraceParserTest {

    private static final String MINIMAL = "{\"traceSummaryId\":\"ts-1\",\"name\":\"My trace\",\"status\":\"success\"}";

    @Test
    public void canParse_minimal() {
        assertTrue(ClickUpTraceParser.getInstance().canParse(MINIMAL));
    }

    @Test
    public void canParse_rejectsEmpty() {
        assertFalse(ClickUpTraceParser.getInstance().canParse("{}"));
    }

    @Test
    public void parse_minimal_producesTraceAndSpan() throws Exception {
        TraceParseResult r = ClickUpTraceParser.getInstance().parse(MINIMAL);
        Trace t = r.getTrace();
        assertNotNull(t);
        assertEquals("ts-1", t.getId());
        assertNotNull(r.getSpans());
        assertEquals(1, r.getSpans().size());
        assertEquals("ts-1-root", t.getRootSpanId());
    }

    @Test
    public void parse_withEvents_addsChildSpans() throws Exception {
        String json = "{\"id\":\"t2\",\"title\":\"T\",\"events\":[{\"eventType\":\"TOOL_CALL\",\"timestamp\":\"2020-01-01T00:00:00Z\"}]}";
        TraceParseResult r = ClickUpTraceParser.getInstance().parse(json);
        assertTrue(r.getSpans().size() >= 2);
    }
}
