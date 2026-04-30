package com.akto.utils.clickup;

import com.akto.util.Constants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ClickUpToHttpProxyMapperTest {

    @Test
    public void buildIngestTagJson_containsServiceGenAiAndAiAgentFields() {
        String tag = ClickUpToHttpProxyMapper.buildIngestTagJson("trace-42");
        JsonObject o = JsonParser.parseString(tag).getAsJsonObject();
        assertEquals(Constants.INGEST_SERVICE_VALUE_CLICKUP, o.get(Constants.INGEST_TAG_SERVICE).getAsString());
        assertEquals(ClickUpToHttpProxyMapper.ARGUS_GEN_AI_DISPLAY_VALUE, o.get(Constants.AKTO_GEN_AI_TAG).getAsString());
        assertEquals(Constants.AI_AGENT_SOURCE_CLICKUP, o.get(Constants.AI_AGENT_TAG_SOURCE).getAsString());
        assertEquals("trace-42", o.get(Constants.AI_AGENT_TAG_BOT_NAME).getAsString());
    }

    @Test
    public void toHttpProxyJsonLine_realRequestBody_wrappedResponseAndTag() {
        String rowJson = "{\"traceSummaryId\":\"ts-1\",\"x\":1}";
        String line = ClickUpToHttpProxyMapper.toHttpProxyJsonLine(
                "ts-1",
                "ws9",
                "{}",
                rowJson,
                1700000000,
                "1000000",
                "0",
                "frontdoor-prod-us-east-2-2.clickup.com",
                "agent-view-99"
        );
        assertTrue(line.contains("/clickup/workspaces/ws9/trace-summaries/ts-1"));
        assertTrue(line.contains("\"source\":\"MIRRORING\""));
        JsonObject root = JsonParser.parseString(line).getAsJsonObject();
        assertEquals("{}", root.get("requestPayload").getAsString());
        JsonObject resp = JsonParser.parseString(root.get("responsePayload").getAsString()).getAsJsonObject();
        assertTrue(resp.has(Constants.CLICKUP_TRACE_METADATA_KEY));
        assertEquals(1, resp.getAsJsonObject(Constants.CLICKUP_TRACE_METADATA_KEY).get("x").getAsInt());
        String tagField = root.get("tag").getAsString();
        JsonObject tagObj = JsonParser.parseString(tagField).getAsJsonObject();
        assertEquals(Constants.INGEST_SERVICE_VALUE_CLICKUP, tagObj.get(Constants.INGEST_TAG_SERVICE).getAsString());
        assertEquals(Constants.AI_AGENT_SOURCE_CLICKUP, tagObj.get(Constants.AI_AGENT_TAG_SOURCE).getAsString());
        assertEquals("agent-view-99", tagObj.get(Constants.AI_AGENT_TAG_BOT_NAME).getAsString());
    }

    @Test
    public void resolveIngestHostHeader_prefersFetchUrlHost() {
        assertEquals(
                "frontdoor-prod-us-east-2-2.clickup.com",
                ClickUpToHttpProxyMapper.resolveIngestHostHeader(
                        "https://frontdoor-prod-us-east-2-2.clickup.com/auto-auditlog-service/v1/ws",
                        "https://api.clickup.com/api/v2"));
    }

    @Test
    public void resolveIngestHostHeader_fallsBackToApiBaseUrl() {
        assertEquals(
                "api.clickup.com",
                ClickUpToHttpProxyMapper.resolveIngestHostHeader(
                        "",
                        "https://api.clickup.com/api/v2"));
    }

    @Test
    public void resolveIngestHostHeader_blankWhenUnparseable() {
        assertEquals("", ClickUpToHttpProxyMapper.resolveIngestHostHeader("not-a-url", ""));
    }
}
