package com.akto.service.trafficcleanup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

public class TestTrafficRetentionPreview {

    @Test
    public void allOkSumsTotalAndIsComplete() {
        TrafficRetentionPreview p = TrafficRetentionPreview.from(180, 1000, Arrays.asList(
                CollectionCleanupResult.ok(1, "a", 3, 0),
                CollectionCleanupResult.ok(2, "b", 0, 0)));

        assertTrue(p.isComplete());
        assertEquals(3, p.getTotalStaleApis());
        assertEquals(180, p.getDays());
        assertEquals(1000, p.getCutoffEpoch());
    }

    @Test
    public void failedCollectionMakesPreviewIncompleteAndIsNotCountedAsZero() {
        CollectionCleanupResult failed = CollectionCleanupResult.failed(2, "b", 0, "mongo timeout");
        TrafficRetentionPreview p = TrafficRetentionPreview.from(180, 1000, Arrays.asList(
                CollectionCleanupResult.ok(1, "a", 5, 0), failed));

        assertFalse(p.isComplete());
        assertEquals(5, p.getTotalStaleApis());
        assertNull(failed.getStaleApis());
        assertEquals(CollectionCleanupResult.State.FAILED, failed.getState());
        assertEquals("mongo timeout", failed.getError());
    }

    @Test
    public void onlyFailuresGiveIncompleteWithZeroKnownTotal() {
        TrafficRetentionPreview p = TrafficRetentionPreview.from(30, 1, Arrays.asList(
                CollectionCleanupResult.failed(1, "a", 0, "boom")));

        assertFalse(p.isComplete());
        assertEquals(0, p.getTotalStaleApis());
    }

    @Test
    public void noEligibleCollectionsIsCompleteAndEmpty() {
        TrafficRetentionPreview p = TrafficRetentionPreview.from(30, 1, new ArrayList<>());

        assertTrue(p.isComplete());
        assertEquals(0, p.getTotalStaleApis());
        assertTrue(p.getCollections().isEmpty());
    }

    /** The frontend (trafficRetention.js / TrafficRetentionResults.jsx) reads exactly these field names,
     *  and treats staleApis=null on a FAILED row as "unavailable". Same serializer the struts json result uses. */
    @Test
    public void serializesToTheShapeTheFrontendReads() throws Exception {
        TrafficRetentionPreview p = TrafficRetentionPreview.from(180, 1000, Arrays.asList(
                CollectionCleanupResult.ok(1, "a", 3, 0),
                CollectionCleanupResult.failed(2, "b", 0, "boom")));

        org.apache.struts2.json.JSONUtil util = new org.apache.struts2.json.JSONUtil();
        util.setWriter(new org.apache.struts2.json.DefaultJSONWriter());
        String json = util.serialize(p, false);

        assertTrue(json, json.contains("\"complete\":false"));
        assertTrue(json, json.contains("\"cutoffEpoch\":1000"));
        assertTrue(json, json.contains("\"days\":180"));
        assertTrue(json, json.contains("\"totalStaleApis\":3"));
        assertTrue(json, json.contains("\"apiCollectionId\":1"));
        assertTrue(json, json.contains("\"state\":\"OK\""));
        assertTrue(json, json.contains("\"state\":\"FAILED\""));
        assertTrue("a failed collection must serialize its count as null, not 0", json.contains("\"staleApis\":null"));
        assertTrue(json, json.contains("\"error\":\"boom\""));
    }

    @Test
    public void failedDeleteKeepsTrueDeletedCount() {
        CollectionCleanupResult failed = CollectionCleanupResult.failed(7, "c", 120, "bulkWrite failed");

        assertEquals(120, failed.getDeletedApis());
        assertNull(failed.getStaleApis());
    }
}
