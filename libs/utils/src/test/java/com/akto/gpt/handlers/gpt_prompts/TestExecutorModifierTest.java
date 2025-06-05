package com.akto.gpt.handlers.gpt_prompts;

import com.mongodb.BasicDBObject;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class TestExecutorModifierTest {
    private final TestExecutorModifier modifier = new TestExecutorModifier();

    @Test
    public void testProcessResponseWithValidJsonString() {
        String rawResponse = "{\"model\":\"llama3:8b\",\"response\":\"{\\\"add_header\\\":\\\"Authorization\\\"}\"}";
        BasicDBObject result = modifier.processResponse(rawResponse);
        assertTrue(result.containsKey("add_header"));
        assertEquals("Authorization", result.get("add_header"));
    }

    @Test
    public void testProcessResponseWithValidJsonObject() {
        String rawResponse = "{\"model\":\"llama3:8b\",\"response\":\"{\\\"modify_header\\\":{\\\"header1\\\":\\\"value1\\\"}}\"}";
        BasicDBObject result = modifier.processResponse(rawResponse);
        assertTrue(result.containsKey("modify_header"));
        Object val = result.get("modify_header");
        assertTrue(val instanceof JSONObject || val instanceof Map);
    }

    @Test
    public void testProcessResponseWithValidJsonArray() {
        String rawResponse = "{\"model\":\"llama3:8b\",\"response\":\"{\\\"add_header\\\":[{\\\"Authorization\\\":\\\"\\\"}]}\"}";
        BasicDBObject result = modifier.processResponse(rawResponse);
        assertTrue(result.containsKey("add_header"));
        Object val = result.get("add_header");
        assertTrue(val instanceof JSONArray || val instanceof List);
    }

    @Test
    public void testProcessResponseWithNotFound() {
        String rawResponse = "{\"model\":\"llama3:8b\",\"response\":\"not_found\"}";
        BasicDBObject result = modifier.processResponse(rawResponse);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testProcessResponseWithMalformedJson() {
        String rawResponse = "{\"model\":\"llama3:8b\",\"response\":\"{invalid_json}\"}";
        BasicDBObject result = modifier.processResponse(rawResponse);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testProcessResponseWithAddHeaderObject() {
        String rawResponse = "{" +
                "\"model\":\"llama3:8b\"," +
                "\"created_at\":\"2025-06-03T07:43:04.117353591Z\"," +
                "\"response\":\"{ \\\"add_header\\\": {\\\"Authorization\\\": \\\"Bearer <your_token>\\\" } }\\n\\nNote: You would need to replace \\\"<your_token>\\\" with a valid API token for this operation to be successful.\"," +
                "\"done\":true," +
                "\"done_reason\":\"stop\",\n\"context\":[128006,882,128007,271],\n\"total_duration\":5084313861}";
        BasicDBObject result = modifier.processResponse(rawResponse);
        assertTrue(result.containsKey("add_header"));
        Object addHeaderObj = result.get("add_header");
        assertTrue(addHeaderObj instanceof JSONObject || addHeaderObj instanceof Map);
        if (addHeaderObj instanceof JSONObject) {
            JSONObject obj = (JSONObject) addHeaderObj;
            try {
                assertEquals("Bearer <your_token>", obj.getString("Authorization"));
            } catch (JSONException e) {
                fail("JSONException thrown: " + e.getMessage());
            }
        } else if (addHeaderObj instanceof Map) {
            Map<?,?> map = (Map<?,?>) addHeaderObj;
            assertTrue(map.containsKey("Authorization"));
            assertEquals("Bearer <your_token>", map.get("Authorization"));
        }
    }
}
