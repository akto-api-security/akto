package com.akto.service.collections;

import com.akto.MongoBasedTest;
import com.akto.dto.ApiCollectionStats.Tab;
import com.akto.dto.ApiCollectionStatsMeta;
import com.akto.service.TimedService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestCollectionsPageResponse extends MongoBasedTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void metaIsServedWithTheKeysTheTableReads() {
        Map<Tab, Long> counts = new EnumMap<>(Tab.class);
        counts.put(Tab.HOSTNAME, 3L);
        counts.put(Tab.GROUP, 1L);
        counts.put(Tab.DEACTIVATED, 2L);
        ApiCollectionStatsMeta summary = new ApiCollectionStatsMeta();

        JsonNode json = mapper.valueToTree(new CollectionsPageResponse.Meta(
                new CollectionsPageResponse.TabCounts(counts, 5), new CollectionsPageResponse.Summary(summary),
                Collections.singletonMap("env", Collections.singletonList("prod")), true,
                Collections.singletonMap("RISK_SCORE", 7), false));

        // tab ids as the table names them; "all" is the sum of the stored tabs
        assertEquals(6, json.get("tabCounts").get("all").asLong());
        assertEquals(3, json.get("tabCounts").get("hostname").asLong());
        assertEquals(1, json.get("tabCounts").get("groups").asLong());
        assertEquals(0, json.get("tabCounts").get("custom").asLong());
        assertEquals(2, json.get("tabCounts").get("deactivated").asLong());
        assertEquals(5, json.get("tabCounts").get("untracked").asLong());
        assertEquals("prod", json.get("tagChoices").get("env").get(0).asText());
        assertTrue(json.get("hasUsageEndpoints").asBoolean());
        assertEquals(7, json.get("statsUpdatedAt").get("RISK_SCORE").asInt());
    }

    @Test
    public void emptyPageHasEmptyCollectionsNotNulls() {
        JsonNode json = mapper.valueToTree(new CollectionsPageResponse.Page());
        for (String field : new String[]{"apiCollections", "untrackedRows", "riskScoreMap", "lastSeenMap",
                "sensitiveInfoMap", "severityInfoMap", "statsUpdatedAt"}) {
            assertEquals(field, 0, json.get(field).size());
        }
        assertEquals(0, json.get("total").asLong());
        assertEquals(false, json.get("statsPending").asBoolean());
    }

    @Test
    public void detailsReportAColumnWhoseQueryFailedAsUnavailableNotAsZero() {
        JsonNode json = mapper.valueToTree(new CollectionsPageResponse.Details(Collections.singletonMap(1, 4)));
        assertEquals(4, json.get("coverageMap").get("1").asInt());
        assertEquals(false, json.get("coverageUnavailable").asBoolean());

        JsonNode failed = mapper.valueToTree(new CollectionsPageResponse.Details(null));
        assertEquals(0, failed.get("coverageMap").size());
        assertEquals(true, failed.get("coverageUnavailable").asBoolean());
    }

    private static class Probe extends TimedService {
        <T> T run(java.util.function.Supplier<T> body) {
            return timed("probe", body, x -> 1);
        }
    }

    @Test
    public void timedReturnsTheResultAndRethrowsFailures() {
        assertEquals("done", new Probe().run(() -> "done"));
        try {
            new Probe().run(() -> {
                throw new IllegalStateException("boom");
            });
            fail("expected the failure to propagate");
        } catch (IllegalStateException e) {
            assertEquals("boom", e.getMessage());
        }
    }
}
