package com.akto.runtime.parser;

import com.akto.dto.HttpResponseParams;
import com.akto.util.Constants;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class IngestRootTagParserTest {

    @Test
    public void normalizeTagField_stringJson() {
        String raw = "{\"" + Constants.INGEST_TAG_SERVICE + "\":\"" + Constants.INGEST_SERVICE_VALUE_CLICKUP + "\",\""
                + Constants.AKTO_GEN_AI_TAG + "\":\"Gen AI\"}";
        String normalized = IngestRootTagParser.normalizeTagField(raw);
        assertTrue(IngestRootTagParser.isClickUpIngestService(normalized));
    }

    @Test
    public void normalizeTagField_map() {
        Map<String, String> m = new HashMap<>();
        m.put(Constants.INGEST_TAG_SERVICE, Constants.INGEST_SERVICE_VALUE_CLICKUP);
        m.put(Constants.AKTO_GEN_AI_TAG, "Gen AI");
        String out = IngestRootTagParser.normalizeTagField(m);
        assertNotNull(out);
        assertTrue(IngestRootTagParser.isClickUpIngestService(out));
    }

    @Test
    public void normalizeTagField_invalid_returnsNull() {
        assertFalse(IngestRootTagParser.isClickUpIngestService(IngestRootTagParser.normalizeTagField("{")));
        assertFalse(IngestRootTagParser.isClickUpIngestService(null));
        assertFalse(IngestRootTagParser.isClickUpIngestService(""));
    }

    @Test
    public void parseSampleMessage_withStringTag() throws Exception {
        String msg = "{\"method\":\"POST\",\"path\":\"/x\",\"type\":\"HTTP/1.1\",\"requestHeaders\":\"{}\","
                + "\"requestPayload\":\"{}\",\"statusCode\":\"200\",\"status\":\"OK\",\"responseHeaders\":\"{}\","
                + "\"responsePayload\":\"{}\",\"time\":\"1700000000\",\"akto_account_id\":\"1\",\"akto_vxlan_id\":\"0\","
                + "\"is_pending\":\"false\",\"source\":\"MIRRORING\","
                + "\"tag\":\"{\\\"service\\\":\\\"clickup\\\",\\\"gen-ai\\\":\\\"Gen AI\\\",\\\"source\\\":\\\"CLICKUP\\\",\\\"bot-name\\\":\\\"tid1\\\"}\"}";
        HttpResponseParams p = SampleParser.parseSampleMessage(msg);
        assertNotNull(p);
        assertTrue(IngestRootTagParser.isClickUpIngestService(p.getTags()));
    }
}
