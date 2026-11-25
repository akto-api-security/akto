package com.akto.utils.elasticsearch;

import org.json.JSONObject;

/** Exposes package-private ElasticSearchClient helpers to tests in other packages. */
public final class ElasticSearchClientTestAccess {
    private ElasticSearchClientTestAccess() {}

    public static JSONObject excludeSessioned(JSONObject query) throws Exception {
        return ElasticSearchClient.excludeSessioned(query);
    }
}
