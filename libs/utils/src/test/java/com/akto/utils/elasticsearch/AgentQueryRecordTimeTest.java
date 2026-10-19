package com.akto.utils.elasticsearch;

import com.akto.dao.context.Context;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Polled connector traffic keeps the time the conversation happened; live traffic is stamped now. */
class AgentQueryRecordTimeTest {

    private static final int HAPPENED_AT = 1760000000;

    private static HttpResponseParams traffic(int time) {
        HttpRequestParams request = new HttpRequestParams("POST", "/runs", "HTTP/1.1", new HashMap<>(), "{}", 0);
        return new HttpResponseParams("HTTP/1.1", 200, "OK", new HashMap<>(), "{}", request, time, "1000", false,
                HttpResponseParams.Source.MIRRORING, "", "", "", "1", "", new ArrayList<>());
    }

    private static Map<String, String> source(String source) {
        return Collections.singletonMap("source", source);
    }

    @Test
    void ibmWatsonxTrafficKeepsItsTime() {
        assertEquals(HAPPENED_AT * 1000L, AgentQueryRecord.recordTimeMs(traffic(HAPPENED_AT), source("IBM_WATSONX")));
    }

    @Test
    void otherTrafficIsStampedNow() {
        long before = Context.now() * 1000L;
        assertTrue(AgentQueryRecord.recordTimeMs(traffic(HAPPENED_AT), source("AWS_BEDROCK")) >= before);
        assertTrue(AgentQueryRecord.recordTimeMs(traffic(HAPPENED_AT), null) >= before);
    }

    @Test
    void ibmWatsonxTrafficWithoutATimeIsStampedNow() {
        long before = Context.now() * 1000L;
        assertTrue(AgentQueryRecord.recordTimeMs(traffic(0), source("IBM_WATSONX")) >= before);
    }
}
