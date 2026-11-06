package com.akto.utils.crons;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.json.JSONObject;
import org.junit.Test;

public class McpToolDescriptionCronTest {

    private static final String TOOLS_LIST_RESULT = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
            + "{\"name\":\"search_hotels\",\"description\":\"Search hotels by city\",\"inputSchema\":{}},"
            + "{\"name\":\"cancel_booking\",\"description\":\"Cancel a booking\",\"inputSchema\":{}},"
            + "{\"name\":\"no_description\",\"inputSchema\":{}}]}}";

    private static String sample(String responsePayload, String contentType) {
        return new JSONObject()
                .put("responsePayload", responsePayload)
                .put("responseHeaders", new JSONObject().put("Content-Type", contentType).toString())
                .toString();
    }

    @Test
    public void parsesJsonToolsList() {
        Map<String, String> descriptions = McpToolDescriptionCron.parseToolDescriptions(
                sample(TOOLS_LIST_RESULT, "application/json"));
        assertEquals(2, descriptions.size());
        assertEquals("Search hotels by city", descriptions.get("search_hotels"));
        assertEquals("Cancel a booking", descriptions.get("cancel_booking"));
    }

    @Test
    public void parsesSseToolsList() {
        String sse = "event: message\ndata: " + TOOLS_LIST_RESULT + "\n\n";
        Map<String, String> descriptions = McpToolDescriptionCron.parseToolDescriptions(
                sample(sse, "text/event-stream"));
        assertEquals("Search hotels by city", descriptions.get("search_hotels"));
        assertEquals(2, descriptions.size());
    }

    @Test
    public void skipsSamplesThatAreNotToolsList() {
        assertTrue(McpToolDescriptionCron.parseToolDescriptions(sample("", "application/json")).isEmpty());
        assertTrue(McpToolDescriptionCron.parseToolDescriptions(
                sample("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32601}}", "application/json")).isEmpty());
        assertTrue(McpToolDescriptionCron.parseToolDescriptions("not json").isEmpty());
    }

    @Test
    public void extractsToolNameFromCallUrl() {
        assertEquals("search_hotels", McpToolDescriptionCron.toolNameFromUrl("/mcp/tools/call/search_hotels"));
        assertEquals("search_hotels", McpToolDescriptionCron.toolNameFromUrl("tools/call/search_hotels"));
    }
}
