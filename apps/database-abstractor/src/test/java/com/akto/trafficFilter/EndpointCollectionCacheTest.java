package com.akto.trafficFilter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.akto.dto.traffic.CollectionTags;

public class EndpointCollectionCacheTest {

    private static CollectionTags tag(String key, String value) {
        return new CollectionTags(0, key, value, CollectionTags.TagSource.AKTO);
    }

    @Test
    public void endpointSourceTagIsDetected() {
        List<CollectionTags> tags = Arrays.asList(tag("gen-ai", "Gen AI"), tag("source", "ENDPOINT"));
        assertTrue(EndpointCollectionCache.hasEndpointSourceTag(tags));
    }

    @Test
    public void otherSourcesAreNotEndpoint() {
        assertFalse(EndpointCollectionCache.hasEndpointSourceTag(Arrays.asList(tag("source", "MIRRORING"))));
        assertFalse(EndpointCollectionCache.hasEndpointSourceTag(Arrays.asList(tag("browser-llm", "ENDPOINT"))));
    }

    @Test
    public void missingTagsAreNotEndpoint() {
        assertFalse(EndpointCollectionCache.hasEndpointSourceTag(null));
        assertFalse(EndpointCollectionCache.hasEndpointSourceTag(new ArrayList<>()));
    }
}
