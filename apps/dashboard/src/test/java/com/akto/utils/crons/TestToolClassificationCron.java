package com.akto.utils.crons;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestToolClassificationCron {

    private static final String FULL_SAMPLE = "{"
            + "\"method\":\"POST\","
            + "\"path\":\"/mcp/tools/call/drop_table\","
            + "\"requestHeaders\":\"{\\\"authorization\\\":\\\"Bearer sk-secret\\\"}\","
            + "\"responseHeaders\":\"{\\\"set-cookie\\\":\\\"session=abc\\\"}\","
            + "\"requestPayload\":\"{\\\"table\\\":\\\"users\\\"}\","
            + "\"responsePayload\":\"{}\","
            + "\"statusCode\":200,"
            + "\"status\":\"OK\","
            + "\"time\":1758600000"
            + "}";

    @Test
    public void stripHeaders_dropsBothHeaderBlocks() {
        JSONObject out = new JSONObject(ToolClassificationCron.stripHeaders(FULL_SAMPLE));
        assertFalse(out.has("requestHeaders"));
        assertFalse(out.has("responseHeaders"));
    }

    @Test
    public void stripHeaders_keepsEverythingElse() {
        JSONObject out = new JSONObject(ToolClassificationCron.stripHeaders(FULL_SAMPLE));
        assertEquals("POST", out.getString("method"));
        assertEquals("/mcp/tools/call/drop_table", out.getString("path"));
        assertEquals("{\"table\":\"users\"}", out.getString("requestPayload"));
        assertEquals("{}", out.getString("responsePayload"));
        assertEquals(200, out.getInt("statusCode"));
        assertEquals("OK", out.getString("status"));
        assertEquals(1758600000, out.getInt("time"));
    }

    @Test
    public void stripHeaders_leaksNoSecrets() {
        String out = ToolClassificationCron.stripHeaders(FULL_SAMPLE);
        assertFalse(out.contains("sk-secret"));
        assertFalse(out.contains("session=abc"));
    }

    @Test
    public void stripHeaders_returnsEmptyOnNonJson() {
        assertEquals("", ToolClassificationCron.stripHeaders("not json at all"));
        assertEquals("", ToolClassificationCron.stripHeaders(""));
        assertEquals("", ToolClassificationCron.stripHeaders(null));
    }

    @Test
    public void stripHeaders_sampleWithoutHeadersIsUnchangedInSubstance() {
        JSONObject out = new JSONObject(ToolClassificationCron.stripHeaders("{\"method\":\"GET\"}"));
        assertEquals("GET", out.getString("method"));
        assertEquals(1, out.length());
    }

    @Test
    public void toolNameFromUrl_takesLastSegment() {
        assertEquals("drop_table", ToolClassificationCron.toolNameFromUrl("/mcp/tools/call/drop_table"));
        assertEquals("read_file", ToolClassificationCron.toolNameFromUrl("/tool/read_file"));
    }

    @Test
    public void toolNameFromUrl_handlesTrailingSlashAndNoSlash() {
        assertEquals("/tools/call/", ToolClassificationCron.toolNameFromUrl("/tools/call/"));
        assertEquals("bare", ToolClassificationCron.toolNameFromUrl("bare"));
        assertEquals("", ToolClassificationCron.toolNameFromUrl(""));
    }

    @Test
    public void toolUrlPattern_matchesToolCallShapes() {
        assertTrue(matchesToolUrl("/mcp/tools/call/drop_table"));
        assertTrue(matchesToolUrl("/tool/read_file"));
        assertTrue(matchesToolUrl("/tools/list"));
    }

    @Test
    public void toolUrlPattern_ignoresNonToolUrls() {
        assertFalse(matchesToolUrl("/messages"));
        assertFalse(matchesToolUrl("/api/toolkit"));
        assertFalse(matchesToolUrl("/sse"));
    }

    private static boolean matchesToolUrl(String url) {
        return java.util.regex.Pattern.compile("/tools?/").matcher(url).find();
    }
}
