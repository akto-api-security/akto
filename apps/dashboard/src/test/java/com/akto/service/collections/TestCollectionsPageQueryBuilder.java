package com.akto.service.collections;

import com.mongodb.MongoClientSettings;
import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestCollectionsPageQueryBuilder {

    private static BsonDocument json(Bson bson) {
        return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
    }

    private static CollectionsPageRequest request(String tab, String query, Map<String, List<String>> filters, Map<String, List<String>> tags) {
        return new CollectionsPageRequest(0, 50, "urlsCount", -1, tab, query, filters, tags, false);
    }

    @Test
    public void emptyRequestHasNoFilter() {
        assertEquals("{}", json(CollectionsPageQueryBuilder.buildFilter(request(null, null, null, null))).toJson());
        assertEquals("{}", json(CollectionsPageQueryBuilder.buildFilter(request("ALL", " ", null, null))).toJson());
    }

    @Test
    public void tabIsUpperCasedAndExact() {
        String filter = json(CollectionsPageQueryBuilder.buildFilter(request("hostname", null, null, null))).toJson();
        assertTrue(filter, filter.contains("\"tab\": \"HOSTNAME\""));
    }

    @Test
    public void searchTextIsQuotedNotARegex() {
        String filter = json(CollectionsPageQueryBuilder.buildFilter(request(null, "a.b(", null, null))).toJson();
        assertTrue(filter, filter.contains("\\\\Qa.b(\\\\E"));
        assertTrue(filter, filter.contains("\"displayName\""));
    }

    @Test
    public void tagFiltersAreAndedAcrossKeysAndOredWithinAKey() {
        Map<String, List<String>> tags = new HashMap<>();
        tags.put("env", Arrays.asList("prod", "stage"));
        tags.put("team", Collections.singletonList("x"));
        tags.put("empty", Collections.emptyList());
        BsonDocument filter = json(CollectionsPageQueryBuilder.buildFilter(request(null, null, null, tags)));
        assertEquals(2, filter.getArray("$and").size());
    }

    @Test
    public void columnFilters() {
        Map<String, List<String>> filters = new HashMap<>();
        filters.put("isOutOfTestingScope", Collections.singletonList("true"));
        filters.put("accessType", Collections.singletonList("Internal"));
        BsonDocument filter = json(CollectionsPageQueryBuilder.buildFilter(request(null, null, filters, null)));
        assertEquals(2, filter.getArray("$and").size());
    }

    @Test
    public void numericSortsTieBreakOnTheMirrorOfTheIndexDirection() {
        assertEquals("{\"endpointsCount\": -1, \"_id\": 1}", json(CollectionsPageQueryBuilder.buildSort("urlsCount", -1)).toJson());
        assertEquals("{\"riskScore\": 1, \"_id\": -1}", json(CollectionsPageQueryBuilder.buildSort("riskScore", 1)).toJson());
        assertEquals("{\"lastSeen\": -1, \"_id\": 1}", json(CollectionsPageQueryBuilder.buildSort("detectedTimestamp", -1)).toJson());
        assertEquals("{\"startTs\": -1, \"_id\": 1}", json(CollectionsPageQueryBuilder.buildSort("startTs", -1)).toJson());
    }

    @Test
    public void nameSortsTieBreakInTheSameDirection() {
        assertEquals("{\"displayName\": 1, \"_id\": 1}", json(CollectionsPageQueryBuilder.buildSort("customGroupsSort", 1)).toJson());
        assertEquals("{\"displayName\": -1, \"_id\": -1}", json(CollectionsPageQueryBuilder.buildSort("customGroupsSort", -1)).toJson());
    }

    @Test
    public void unknownOrMissingSortFallsBackToMostEndpointsFirst() {
        String expected = "{\"endpointsCount\": -1, \"_id\": 1}";
        assertEquals(expected, json(CollectionsPageQueryBuilder.buildSort("nope", 1)).toJson());
        assertEquals(expected, json(CollectionsPageQueryBuilder.buildSort(null, -1)).toJson());
    }

    @Test
    public void limitIsCappedAndSkipNeverNegative() {
        CollectionsPageRequest r = new CollectionsPageRequest(-5, 100000, null, 0, null, null, null, null, false);
        assertEquals(0, r.getSkip());
        assertEquals(CollectionsPageRequest.MAX_LIMIT, r.getLimit());
        assertEquals(CollectionsPageRequest.DEFAULT_LIMIT, new CollectionsPageRequest(0, 0, null, 0, null, null, null, null, false).getLimit());
    }
}
