package com.akto.utils.search;

import static org.junit.jupiter.api.Assertions.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.akto.utils.elasticsearch.ElasticSearchClientTestAccess;

/**
 * The unified Argus view relies on Elasticsearch returning only records with no session from
 * searchPrompts(..., onlyWithoutSession = true). These pin the clause it adds, since the merge
 * tests use a stub and would not notice the flag being dropped.
 */
public class NoSessionFilterTest {

    @Test
    public void esExcludesRecordsThatCarryASession() throws Exception {
        JSONObject query = new JSONObject().put("bool", new JSONObject().put("must", new JSONArray()));
        ElasticSearchClientTestAccess.excludeSessioned(query);

        JSONArray mustNot = query.getJSONObject("bool").getJSONArray("must_not");
        assertEquals(1, mustNot.length());
        assertEquals("sessionIdentifier",
            mustNot.getJSONObject(0).getJSONObject("exists").getString("field"));
    }

    @Test
    public void esKeepsExistingMustNotClauses() throws Exception {
        JSONObject atlas = new JSONObject().put("term", new JSONObject().put("isAtlasTraffic", true));
        JSONObject query = new JSONObject().put("bool", new JSONObject()
            .put("must", new JSONArray())
            .put("must_not", new JSONArray().put(atlas)));
        ElasticSearchClientTestAccess.excludeSessioned(query);

        JSONArray mustNot = query.getJSONObject("bool").getJSONArray("must_not");
        assertEquals(2, mustNot.length(), "the Argus (not-Atlas) clause must survive");
        assertTrue(mustNot.getJSONObject(0).has("term"));
        assertTrue(mustNot.getJSONObject(1).has("exists"));
    }
}
