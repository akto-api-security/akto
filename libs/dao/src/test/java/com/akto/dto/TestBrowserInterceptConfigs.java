package com.akto.dto;

import org.bson.Document;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class TestBrowserInterceptConfigs {

    private static Document cfg(String id, int version) {
        return new Document(BrowserInterceptConfigs.ID, id).append(BrowserInterceptConfigs.VERSION, version);
    }

    private static Document stamped(String id, int version, long updatedTs) {
        return cfg(id, version).append(BrowserInterceptConfigs.UPDATED_TIMESTAMP, updatedTs);
    }

    private static Document byId(List<Document> configs, String id) {
        for (Document c : configs) {
            if (id.equals(c.get(BrowserInterceptConfigs.ID))) {
                return c;
            }
        }
        return null;
    }

    @Test
    public void testFilterByCapabilitiesDropsUnsupported() {
        Document ok = cfg("chatgpt", 1).append(BrowserInterceptConfigs.REQUIRES, Arrays.asList("json", "sse"));
        Document missing = cfg("grok", 1).append(BrowserInterceptConfigs.REQUIRES, Collections.singletonList("webchannel"));
        Document noReq = cfg("claude", 1);

        List<String> caps = Arrays.asList("json", "sse");
        List<String> unsupported = new ArrayList<>();
        List<Document> out = BrowserInterceptConfigs.filterByCapabilities(
                Arrays.asList(ok, missing, noReq), caps, unsupported);

        assertEquals(2, out.size());
        assertNotNull(byId(out, "chatgpt"));
        assertNotNull(byId(out, "claude"));   // no requires -> always supported
        assertNull(byId(out, "grok"));
        assertEquals(Collections.singletonList("grok"), unsupported);
    }

    @Test
    public void testChangedSinceReturnsOnlyNewer() {
        List<Document> all = Arrays.asList(stamped("chatgpt", 1, 100), stamped("claude", 2, 200), stamped("gemini", 3, 300));

        List<Document> delta = BrowserInterceptConfigs.changedSince(all, 150);

        assertEquals(2, delta.size());
        assertNotNull(byId(delta, "claude"));
        assertNotNull(byId(delta, "gemini"));
        assertNull(byId(delta, "chatgpt"));
    }

    @Test
    public void testChangedSinceZeroReturnsAll() {
        List<Document> all = Arrays.asList(stamped("chatgpt", 1, 100), stamped("claude", 2, 200));
        assertEquals(2, BrowserInterceptConfigs.changedSince(all, 0).size());
    }

    @Test
    public void testChangedSinceEmptyWhenCurrent() {
        List<Document> all = Arrays.asList(stamped("chatgpt", 1, 100), stamped("claude", 2, 200));
        assertTrue(BrowserInterceptConfigs.changedSince(all, 200).isEmpty());
    }

    @Test
    public void testMaxUpdatedTimestamp() {
        List<Document> all = Arrays.asList(stamped("chatgpt", 1, 100), stamped("claude", 2, 300), stamped("gemini", 3, 200));
        assertEquals(300L, BrowserInterceptConfigs.maxUpdatedTimestamp(all));
    }

    @Test
    public void testMaxUpdatedTimestampEmpty() {
        assertEquals(0L, BrowserInterceptConfigs.maxUpdatedTimestamp(new ArrayList<>()));
    }

    @Test
    public void testFilterByEngineVersionGatesOnMinMax() {
        Document noDep = cfg("a", 1);                                                              // runs everywhere
        Document needsNewer = cfg("b", 1).append(BrowserInterceptConfigs.MIN_ENGINE_VERSION, "2.5"); // needs >= 2.5
        Document tooNew = cfg("c", 1).append(BrowserInterceptConfigs.MAX_ENGINE_VERSION, "2.3");      // only <= 2.3
        List<Document> all = Arrays.asList(noDep, needsNewer, tooNew);

        List<String> dropped = new ArrayList<>();
        List<Document> out = BrowserInterceptConfigs.filterByEngineVersion(all, "2.4", dropped);

        assertNotNull(byId(out, "a"));                 // no dependency -> kept
        assertNull(byId(out, "b"));                    // needs 2.5, client 2.4 -> dropped
        assertNull(byId(out, "c"));                    // max 2.3, client 2.4 -> dropped
        assertEquals(2, dropped.size());
        assertTrue(dropped.contains("b") && dropped.contains("c"));
    }

    @Test
    public void testFilterByEngineVersionBoundaryIsInclusive() {
        Document exact = cfg("a", 1).append(BrowserInterceptConfigs.MIN_ENGINE_VERSION, "2.3.0");
        // "2.3" == "2.3.0", and the bound is inclusive -> client exactly at the min is allowed
        List<Document> out = BrowserInterceptConfigs.filterByEngineVersion(
                Arrays.asList(exact), "2.3", new ArrayList<>());
        assertEquals(1, out.size());
    }

    @Test
    public void testFilterByEngineVersionUnparseableClientNoGate() {
        Document needsNewer = cfg("b", 1).append(BrowserInterceptConfigs.MIN_ENGINE_VERSION, "2.5");
        // an unparseable client version disables the gate (everything passes), matching the client rule
        List<Document> out = BrowserInterceptConfigs.filterByEngineVersion(
                Arrays.asList(needsNewer), "dev", new ArrayList<>());
        assertEquals(1, out.size());
    }
}
