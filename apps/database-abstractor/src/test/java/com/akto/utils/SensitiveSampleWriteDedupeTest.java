package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.akto.dto.bulk_updates.BulkUpdates;
import com.akto.dto.bulk_updates.UpdatePayload;

public class SensitiveSampleWriteDedupeTest {

    private static final long MIN = 60 * 1000L;
    private static final long T0 = 1_000_000_000_000L;

    @Before
    public void setUp() {
        SensitiveSampleWriteDedupe.resetForTests();
    }

    private static BulkUpdates push(String url, String param, String sample) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("_id.url", url);
        filters.put("_id.method", "GET");
        filters.put("_id.responseCode", 200);
        filters.put("_id.isHeader", false);
        filters.put("_id.param", param);
        filters.put("_id.subType", "EMAIL");
        filters.put("_id.apiCollectionId", 1);
        ArrayList<String> updates = new ArrayList<>();
        updates.add(new UpdatePayload("sampleData", Arrays.asList(sample), "pushEach").toString());
        updates.add(new UpdatePayload("collectionIds", Arrays.asList(1), "setOnInsert").toString());
        return new BulkUpdates(filters, updates);
    }

    private static List<BulkUpdates> one(BulkUpdates b) {
        return new ArrayList<>(Collections.singletonList(b));
    }

    @Test
    public void firstWriteIsKept() {
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0).size());
    }

    @Test
    public void sameSampleWithinWindowIsDropped() {
        SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0);
        assertEquals(0, SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0 + 2 * MIN).size());
    }

    @Test
    public void differentSampleForSameParamIsKept() {
        SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0);
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s2")), T0 + 2 * MIN).size());
    }

    @Test
    public void sameSampleForDifferentParamOrAccountIsKept() {
        SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0);
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(push("/b", "email", "s1")), T0 + MIN).size());
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(push("/a", "mail", "s1")), T0 + MIN).size());
        assertEquals(1, SensitiveSampleWriteDedupe.filter(2, one(push("/a", "email", "s1")), T0 + MIN).size());
    }

    @Test
    public void sameSampleAfterQuietWindowIsKeptAgain() {
        SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0);
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0 + 11 * MIN).size());
    }

    @Test
    public void windowSlidesWhileRepeatsKeepComing() {
        SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0);
        // a repeat every 6 minutes: each one is within 10 minutes of the previous sighting
        for (int i = 1; i <= 5; i++) {
            assertEquals(0, SensitiveSampleWriteDedupe.filter(1, one(push("/a", "email", "s1")), T0 + i * 6 * MIN).size());
        }
    }

    @Test
    public void duplicatesInOneBatchKeepOne() {
        List<BulkUpdates> batch = new ArrayList<>(Arrays.asList(push("/a", "email", "s1"), push("/a", "email", "s1"), push("/a", "other", "s1")));
        assertEquals(2, SensitiveSampleWriteDedupe.filter(1, batch, T0).size());
    }

    @Test
    public void deleteWritesAreNeverDropped() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("_id.url", "/a");
        ArrayList<String> updates = new ArrayList<>(Collections.singletonList(new UpdatePayload("x", null, "delete").toString()));
        BulkUpdates delete = new BulkUpdates(filters, updates);
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(delete), T0).size());
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(delete), T0 + MIN).size());
    }

    @Test
    public void unparseableUpdateIsNeverDropped() {
        Map<String, Object> filters = new HashMap<>();
        filters.put("_id.url", "/a");
        ArrayList<String> updates = new ArrayList<>(Collections.singletonList("not json"));
        BulkUpdates odd = new BulkUpdates(filters, updates);
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(odd), T0).size());
        assertEquals(1, SensitiveSampleWriteDedupe.filter(1, one(odd), T0 + MIN).size());
    }

    @Test
    public void nullAndEmptyListsPassThrough() {
        assertNull(SensitiveSampleWriteDedupe.filter(1, null, T0));
        List<BulkUpdates> empty = new ArrayList<>();
        assertSame(empty, SensitiveSampleWriteDedupe.filter(1, empty, T0));
    }

    @Test
    public void disabledByDefault() {
        // env DEDUPE_SENSITIVE_SAMPLE_WRITES is not set in the test environment
        if (System.getenv(SensitiveSampleWriteDedupe.ENV_FLAG) == null) {
            assertEquals(false, SensitiveSampleWriteDedupe.isEnabled());
        }
    }
}
