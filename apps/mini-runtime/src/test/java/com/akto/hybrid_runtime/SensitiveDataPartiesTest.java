package com.akto.hybrid_runtime;

import com.akto.dto.ApiCollection;
import com.akto.dto.HttpRequestParams;
import com.akto.dto.HttpResponseParams;
import com.akto.dto.traffic.SensitiveDataCount;
import com.akto.hybrid_parsers.HttpCallParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Resolving the caller of a message, and deciding whether a count is worth keeping.
 *
 * Both are pure functions of the message - no database - so this runs as an ordinary unit test.
 * {@link SensitiveDataPartiesIT} covers the same logic end to end through APICatalogSync, but that
 * needs Mongo and so does not run in CI.
 */
public class SensitiveDataPartiesTest {

    private static final int ACCOUNT_ID = 1000000;

    private static HttpResponseParams message(String direction, String tagsJson) {
        Map<String, List<String>> reqHeaders = new HashMap<>();
        reqHeaders.put("host", Collections.singletonList("customerapi.sdlab.svc.cluster.local"));
        reqHeaders.put("content-type", Collections.singletonList("application/json"));
        HttpRequestParams requestParams =
                new HttpRequestParams("GET", "/api/booking", "HTTP/1.1", reqHeaders, "", 0);
        return new HttpResponseParams(
                "HTTP/1.1", 200, "OK", new HashMap<>(), "{\"id\":1}",
                requestParams, 0, String.valueOf(ACCOUNT_ID), false, HttpResponseParams.Source.MIRRORING,
                "", "10.0.1.15", "10.0.2.20", direction, tagsJson == null ? "" : tagsJson, new ArrayList<>());
    }

    private static String labels(String service) {
        return "{\"" + HttpCallParser.SERVICE_TAG_KEY + "\":\"" + service + "\",\"pod-template-hash\":\"7d8dbb7df6\"}";
    }

    /**
     * The id must be the one the caller's OWN collection is keyed by. If these ever diverge, the two
     * ends of a call resolve to two different services and the alert reads as nonsense.
     */
    @Test
    public void outboundResolvesTheCallersOwnCollectionId() {
        int expected = ApiCollection.generateServiceTagCollectionId("pma-smile");

        assertEquals(expected,
                SensitiveDataParties.senderCollectionId(message(HttpCallParser.DIRECTION_OUTBOUND, labels("pma-smile"))));
    }

    /** Inbound pod labels describe the callee, so they name nobody worth attributing. */
    @Test
    public void inboundHasNoCallerEvenWithLabels() {
        assertEquals(SensitiveDataCount.UNKNOWN_COLLECTION_ID,
                SensitiveDataParties.senderCollectionId(message("1", labels("customerapi"))));
    }

    /** Everything that leaves the caller unknown. */
    @Test
    public void unresolvableCallersAreUnknown() {
        assertEquals(SensitiveDataCount.UNKNOWN_COLLECTION_ID, SensitiveDataParties.senderCollectionId(null));
        assertEquals(SensitiveDataCount.UNKNOWN_COLLECTION_ID,
                SensitiveDataParties.senderCollectionId(message(HttpCallParser.DIRECTION_OUTBOUND, null)));
        assertEquals(SensitiveDataCount.UNKNOWN_COLLECTION_ID,
                SensitiveDataParties.senderCollectionId(message(HttpCallParser.DIRECTION_OUTBOUND, "{}")));
        assertEquals(SensitiveDataCount.UNKNOWN_COLLECTION_ID,
                SensitiveDataParties.senderCollectionId(
                        message(HttpCallParser.DIRECTION_OUTBOUND, "{\"pod-template-hash\":\"7d8dbb7df6\"}")));
        assertEquals(SensitiveDataCount.UNKNOWN_COLLECTION_ID,
                SensitiveDataParties.senderCollectionId(message(HttpCallParser.DIRECTION_OUTBOUND, labels("   "))));
    }

    /** A padded label still names the same service, not a second one. */
    @Test
    public void serviceNameIsTrimmedSoPaddingDoesNotSplitTheService() {
        assertEquals(ApiCollection.generateServiceTagCollectionId("pma-smile"),
                SensitiveDataParties.senderCollectionId(
                        message(HttpCallParser.DIRECTION_OUTBOUND, labels("  pma-smile  "))));
    }

    /** Adjacent service names must not collide - the ids they produce are consecutive. */
    @Test
    public void similarServiceNamesGetDistinctIds() {
        int one = SensitiveDataParties.senderCollectionId(message(HttpCallParser.DIRECTION_OUTBOUND, labels("caller-1")));
        int five = SensitiveDataParties.senderCollectionId(message(HttpCallParser.DIRECTION_OUTBOUND, labels("caller-5")));

        assertEquals(ApiCollection.generateServiceTagCollectionId("caller-1"), one);
        assertEquals(ApiCollection.generateServiceTagCollectionId("caller-5"), five);
        assertEquals(4, five - one);
    }

    /** Only a named caller is worth storing; a count nobody can attribute cannot reach an alert. */
    @Test
    public void onlyNamedCallersAreRecorded() {
        assertTrue(SensitiveDataParties.shouldRecord(ApiCollection.generateServiceTagCollectionId("pma-smile")));
        assertTrue(SensitiveDataParties.shouldRecord(ApiCollection.generateServiceTagCollectionId("caller-1")));
        assertFalse(SensitiveDataParties.shouldRecord(SensitiveDataCount.UNKNOWN_COLLECTION_ID));
    }
}
