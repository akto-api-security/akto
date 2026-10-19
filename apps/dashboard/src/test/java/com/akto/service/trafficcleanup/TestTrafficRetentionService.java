package com.akto.service.trafficcleanup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.Key;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.types.CappedSet;
import com.mongodb.client.model.Filters;

public class TestTrafficRetentionService extends MongoBasedTest {

    private static final int NOW = 2_000_000_000;
    private static final int DAY = 24 * 60 * 60;

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(null);
        ApiCollectionsDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
    }

    private static ApiCollection collection(int id, String host) {
        return new ApiCollection(id, "c" + id, 0, new HashSet<>(), host, 0, false, true);
    }

    private static List<Key> keys(int collectionId, int count) {
        List<Key> keys = new ArrayList<>();
        for (int i = 0; i < count; i++) keys.add(new Key(collectionId, "/u" + i, Method.GET, -1, 0, 0));
        return keys;
    }

    private static TrafficRetentionService service(TrafficRetentionService.StaleApiFetcher fetcher,
                                                   TrafficRetentionService.ApiDeleter deleter,
                                                   ApiCollection... collections) {
        return new TrafficRetentionService(fetcher, deleter, () -> Arrays.asList(collections));
    }

    // ---- validation ----

    @Test
    public void daysOutsideBoundsAreRejected() {
        for (int bad : new int[]{-1, 0, TrafficRetentionService.MIN_DAYS - 1, TrafficRetentionService.MAX_DAYS + 1}) {
            try {
                TrafficRetentionService.validateDays(bad);
                fail("expected rejection for days=" + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        TrafficRetentionService.validateDays(TrafficRetentionService.MIN_DAYS);
        TrafficRetentionService.validateDays(TrafficRetentionService.MAX_DAYS);
    }

    @Test
    public void cutoffIsDaysBeforeNow() {
        assertEquals(NOW - 180 * DAY, TrafficRetentionService.cutoffForDays(180, NOW));
    }

    @Test
    public void cutoffYoungerThanTheFloorOrNonPositiveIsRejected() {
        for (int bad : new int[]{0, -5, NOW, NOW - 29 * DAY}) {
            try {
                TrafficRetentionService.validateCutoff(bad, NOW);
                fail("expected rejection for cutoff=" + bad);
            } catch (IllegalArgumentException expected) {
                // ok
            }
        }
        TrafficRetentionService.validateCutoff(NOW - 30 * DAY, NOW);
    }

    // ---- preview ----

    @Test
    public void previewReportsPerCollectionCountsAndDeletesNothing() {
        AtomicInteger deleteCalls = new AtomicInteger();
        TrafficRetentionService svc = service((id, cutoff) -> keys(id, id == 1 ? 3 : 0),
                k -> deleteCalls.incrementAndGet(), collection(1, "a.com"), collection(2, "b.com"));

        TrafficRetentionPreview p = svc.preview(180, NOW);

        assertTrue(p.isComplete());
        assertEquals(3, p.getTotalStaleApis());
        assertEquals(NOW - 180 * DAY, p.getCutoffEpoch());
        assertEquals(2, p.getCollections().size());
        assertEquals(0, deleteCalls.get());
    }

    @Test
    public void previewFailedCollectionIsFailedNotZeroAndMakesPreviewIncomplete() {
        TrafficRetentionService svc = service((id, cutoff) -> {
            if (id == 2) throw new IllegalStateException("mongo down");
            return keys(id, 4);
        }, k -> { }, collection(1, "a.com"), collection(2, "b.com"));

        TrafficRetentionPreview p = svc.preview(180, NOW);

        assertFalse(p.isComplete());
        assertEquals(4, p.getTotalStaleApis());
        CollectionCleanupResult failed = p.getCollections().get(1);
        assertEquals(CollectionCleanupResult.State.FAILED, failed.getState());
        assertNull(failed.getStaleApis());
        assertTrue(failed.getError().contains("mongo down"));
    }

    @Test
    public void previewPropagatesCollectionListFailure() {
        TrafficRetentionService svc = new TrafficRetentionService((id, c) -> keys(id, 1), k -> { }, () -> {
            throw new IllegalStateException("cannot list collections");
        });
        try {
            svc.preview(180, NOW);
            fail("a collection-list failure must not look like an empty preview");
        } catch (IllegalStateException expected) {
            // ok
        }
    }

    @Test
    public void previewRejectsBadDaysBeforeTouchingAnything() {
        TrafficRetentionService svc = new TrafficRetentionService((id, c) -> { throw new AssertionError("must not fetch"); },
                k -> { }, () -> { throw new AssertionError("must not list"); });
        try {
            svc.preview(5, NOW);
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ---- delete ----

    @Test
    public void deleteRunsInBatchesOfFiveHundred() {
        List<Integer> batchSizes = new ArrayList<>();
        TrafficRetentionService svc = service((id, cutoff) -> keys(id, 1200), k -> batchSizes.add(k.size()), collection(1, "a.com"));

        List<CollectionCleanupResult> results = svc.runDelete(NOW - 100 * DAY);

        assertEquals(Arrays.asList(500, 500, 200), batchSizes);
        assertEquals(1200, results.get(0).getDeletedApis());
        assertEquals(Integer.valueOf(1200), results.get(0).getStaleApis());
    }

    @Test
    public void deleteFailureKeepsTrueCountAndNextCollectionStillRuns() {
        AtomicInteger calls = new AtomicInteger();
        TrafficRetentionService svc = service((id, cutoff) -> keys(id, 1200), k -> {
            if (k.get(0).getApiCollectionId() == 1 && calls.incrementAndGet() == 2) throw new RuntimeException("bulkWrite failed");
        }, collection(1, "a.com"), collection(2, "b.com"));

        List<CollectionCleanupResult> results = svc.runDelete(NOW - 100 * DAY);

        CollectionCleanupResult first = results.get(0);
        assertEquals(CollectionCleanupResult.State.FAILED, first.getState());
        assertEquals("only the first batch was deleted before the failure", 500, first.getDeletedApis());
        assertNull(first.getStaleApis());
        assertEquals(CollectionCleanupResult.State.OK, results.get(1).getState());
        assertEquals(1200, results.get(1).getDeletedApis());
    }

    @Test
    public void deleteFetchFailureDeletesNothingForThatCollection() {
        AtomicInteger deleteCalls = new AtomicInteger();
        TrafficRetentionService svc = service((id, cutoff) -> { throw new RuntimeException("read failed"); },
                k -> deleteCalls.incrementAndGet(), collection(1, "a.com"));

        List<CollectionCleanupResult> results = svc.runDelete(NOW - 100 * DAY);

        assertEquals(CollectionCleanupResult.State.FAILED, results.get(0).getState());
        assertEquals(0, deleteCalls.get());
    }

    // ---- guard ----

    @Test
    public void secondDeleteForSameAccountIsRefusedUntilReleased() {
        int account = 424242;
        int now = Context.now();
        assertTrue(TrafficRetentionService.tryAcquire(account));
        try {
            assertFalse("a delete is already marked running for this account",
                    TrafficRetentionService.instance.startDelete(account, now - 100 * DAY, now));
        } finally {
            TrafficRetentionService.release(account);
        }
        assertTrue("released accounts can acquire again", TrafficRetentionService.tryAcquire(account));
        TrafficRetentionService.release(account);
    }

    @Test
    public void startDeleteRejectsBadCutoffWithoutAcquiringTheGuard() {
        int account = 424243;
        try {
            TrafficRetentionService.instance.startDelete(account, Context.now(), Context.now());
            fail();
        } catch (IllegalArgumentException expected) {
            // ok
        }
        assertTrue("a rejected request must not leave the account marked as running", TrafficRetentionService.tryAcquire(account));
        TrafficRetentionService.release(account);
    }

    // ---- eligibility + real DAOs ----

    @Test
    public void onlyActiveNonDemoHostCollectionsAreEligible() {
        ApiCollection active = collection(10, "active.com");
        ApiCollection deactivated = collection(11, "off.com");
        deactivated.setDeactivated(true);
        ApiCollection noHost = collection(12, null);
        ApiCollection group = collection(13, null);
        group.setType(ApiCollection.Type.API_GROUP);
        ApiCollection groupWithHost = collection(14, "grp.com");
        groupWithHost.setType(ApiCollection.Type.API_GROUP);
        ApiCollection demo = collection(1111111111, "demo.com");
        ApiCollectionsDao.instance.insertMany(Arrays.asList(active, deactivated, noHost, group, groupWithHost, demo));

        List<ApiCollection> eligible = TrafficRetentionService.fetchEligibleCollections();

        assertEquals(1, eligible.size());
        assertEquals(10, eligible.get(0).getId());
    }

    @Test
    public void deletesStaleApiEverywhereAndKeepsFreshOnesWithRealDaos() {
        ApiCollectionsDao.instance.insertOne(collection(20, "real.com"));
        insertHostSti(20, "/stale", NOW - 400 * DAY);
        insertHostSti(20, "/fresh", NOW - 10 * DAY);
        ApiInfoDao.instance.insertOne(new ApiInfo(20, "/stale", Method.GET));
        ApiInfoDao.instance.insertOne(new ApiInfo(20, "/fresh", Method.GET));

        List<CollectionCleanupResult> results = TrafficRetentionService.instance.runDelete(NOW - 180 * DAY);

        assertEquals(1, results.size());
        assertEquals(1, results.get(0).getDeletedApis());
        assertEquals(0, SingleTypeInfoDao.instance.count(Filters.eq("url", "/stale")));
        assertEquals(0, ApiInfoDao.instance.count(Filters.eq("_id.url", "/stale")));
        assertEquals(1, SingleTypeInfoDao.instance.count(Filters.eq("url", "/fresh")));
        assertEquals(1, ApiInfoDao.instance.count(Filters.eq("_id.url", "/fresh")));

        List<CollectionCleanupResult> rerun = TrafficRetentionService.instance.runDelete(NOW - 180 * DAY);
        assertEquals("a re-run finds nothing left to delete", 0, rerun.get(0).getDeletedApis());
        assertEquals(CollectionCleanupResult.State.OK, rerun.get(0).getState());
    }

    private void insertHostSti(int coll, String url, long lastSeen) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, "GET", -1, true, "host", SingleTypeInfo.GENERIC, coll, false);
        SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 1, 0, 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        sti.setLastSeen(lastSeen);
        SingleTypeInfoDao.instance.insertOne(sti);
    }
}
