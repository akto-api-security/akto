package com.akto.utils.clickup;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ClickUpIngestServiceTest {

    @Test
    public void extractTraceSummaries_prefersDataArray() {
        JsonObject root = new JsonObject();
        JsonArray data = new JsonArray();
        JsonObject a = new JsonObject();
        a.addProperty("id", "1");
        data.add(a);
        root.add("data", data);
        List<JsonObject> list = ClickUpIngestService.extractTraceSummaries(root);
        assertEquals(1, list.size());
        assertEquals("1", list.get(0).get("id").getAsString());
    }

    @Test
    public void extractTraceSummaries_topLevelArray() {
        JsonArray arr = new JsonArray();
        JsonObject a = new JsonObject();
        a.addProperty("traceSummaryId", "x");
        arr.add(a);
        List<JsonObject> list = ClickUpIngestService.extractTraceSummaries(arr);
        assertEquals(1, list.size());
    }

    @Test
    public void pickTraceId_order() {
        JsonObject o = new JsonObject();
        o.addProperty("traceSummaryId", "ts");
        assertEquals("ts", ClickUpIngestService.pickTraceId(o));
        JsonObject o2 = new JsonObject();
        o2.addProperty("id", 99);
        assertEquals("99", ClickUpIngestService.pickTraceId(o2));
        assertNull(ClickUpIngestService.pickTraceId(new JsonObject()));
    }

    @Test
    public void pickAgentViewId_stringOrNumber() {
        JsonObject o = new JsonObject();
        o.addProperty("agentViewId", "av-1");
        assertEquals("av-1", ClickUpIngestService.pickAgentViewId(o));
        JsonObject o2 = new JsonObject();
        o2.addProperty("agent_view_id", 42);
        assertEquals("42", ClickUpIngestService.pickAgentViewId(o2));
        assertNull(ClickUpIngestService.pickAgentViewId(new JsonObject()));
    }

    @Test
    public void ensureBatchDataWrapper_singleObject() {
        assertEquals(
                "{\"batchData\":[{\"path\":\"/p\"}]}",
                ClickUpIngestService.ensureBatchDataWrapper("{\"path\":\"/p\"}"));
    }

    @Test
    public void ensureBatchDataWrapper_alreadyWrapped() {
        String w = "{\"batchData\":[{\"path\":\"/x\"}]}";
        assertEquals(w, ClickUpIngestService.ensureBatchDataWrapper(w));
    }

    @Test
    public void ensureBatchDataWrapper_rawArray() {
        assertEquals(
                "{\"batchData\":[{\"a\":1},{\"a\":2}]}",
                ClickUpIngestService.ensureBatchDataWrapper("[{\"a\":1},{\"a\":2}]"));
    }
}
