package com.akto.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dto.traffic.Key;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.types.CappedSet;

/**
 * Covers SingleTypeInfoDao.fetchApisNotSeenSince(): per-API max lastSeen over host-header STIs of
 * ANY subType (a stale GENERIC host STI must not condemn an API whose IP-host STI is fresh), the
 * "unknown is not stale" rule, collection isolation and the method-name restriction that keeps the
 * preview count consistent with what the delete helpers can actually remove.
 */
public class TestSingleTypeInfoDaoFetchApisNotSeenSince extends MongoBasedTest {

    private static final int CUTOFF = 1_000_000;
    private static final int OLD = CUTOFF - 100;
    private static final int FRESH = CUTOFF + 100;

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        SingleTypeInfoDao.instance.getMCollection().drop();
    }

    private void insertSti(int apiCollectionId, String url, String method, String param, boolean isHeader,
                           int responseCode, SingleTypeInfo.SubType subType, long lastSeen) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method, responseCode, isHeader, param, subType, apiCollectionId, false);
        SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 1, 0, 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        sti.setLastSeen(lastSeen);
        SingleTypeInfoDao.instance.insertOne(sti);
    }

    private void insertHost(int coll, String url, String method, SingleTypeInfo.SubType subType, long lastSeen) {
        insertSti(coll, url, method, "host", true, -1, subType, lastSeen);
    }

    private List<Key> fetch(int coll) {
        return SingleTypeInfoDao.instance.fetchApisNotSeenSince(coll, CUTOFF);
    }

    @Test
    public void staleHostStiIsReturned() {
        insertHost(1, "/old", "GET", SingleTypeInfo.GENERIC, OLD);

        List<Key> stale = fetch(1);

        assertEquals(1, stale.size());
        assertEquals(1, stale.get(0).getApiCollectionId());
        assertEquals("/old", stale.get(0).getUrl());
        assertEquals(Method.GET, stale.get(0).getMethod());
    }

    @Test
    public void freshHostStiIsNotReturned() {
        insertHost(1, "/fresh", "GET", SingleTypeInfo.GENERIC, FRESH);

        assertTrue(fetch(1).isEmpty());
    }

    /** The trap behind filterForHostHeader's GENERIC-only clause: stale GENERIC host STI, fresh IP host STI. */
    @Test
    public void staleGenericButFreshIpHostStiIsNotReturned() {
        insertHost(1, "/live", "GET", SingleTypeInfo.GENERIC, OLD);
        insertHost(1, "/live", "GET", SingleTypeInfo.IP_ADDRESS, FRESH);

        assertTrue("a live API must not be deleted because only its GENERIC host STI is old", fetch(1).isEmpty());
    }

    @Test
    public void allHostStisOldIsReturnedOnce() {
        insertHost(1, "/gone", "GET", SingleTypeInfo.GENERIC, OLD - 5);
        insertHost(1, "/gone", "GET", SingleTypeInfo.IP_ADDRESS, OLD);

        assertEquals(1, fetch(1).size());
    }

    @Test
    public void apiWithoutHostStiIsUnknownNotStale() {
        insertSti(1, "/nohost", "GET", "authorization", true, -1, SingleTypeInfo.GENERIC, OLD);
        insertSti(1, "/nohost", "GET", "host", false, -1, SingleTypeInfo.GENERIC, OLD);
        insertSti(1, "/nohost", "GET", "host", true, 200, SingleTypeInfo.GENERIC, OLD);

        assertTrue("non-host / non-header / response-side rows must not make an API a candidate", fetch(1).isEmpty());
    }

    @Test
    public void otherCollectionsAreNotReturned() {
        insertHost(1, "/a", "GET", SingleTypeInfo.GENERIC, OLD);
        insertHost(2, "/b", "GET", SingleTypeInfo.GENERIC, OLD);

        List<Key> stale = fetch(1);

        assertEquals(1, stale.size());
        assertEquals("/a", stale.get(0).getUrl());
    }

    @Test
    public void methodsTheDeleteCannotMatchAreExcluded() {
        insertHost(1, "/lower", "get", SingleTypeInfo.GENERIC, OLD);
        insertHost(1, "/other", "OTHER", SingleTypeInfo.GENERIC, OLD);
        insertHost(1, "/weird", "PROPFIND", SingleTypeInfo.GENERIC, OLD);
        insertHost(1, "/post", "POST", SingleTypeInfo.GENERIC, OLD);

        List<Key> stale = fetch(1);

        assertEquals(1, stale.size());
        assertEquals("/post", stale.get(0).getUrl());
    }

    @Test
    public void sameUrlDifferentMethodsAreSeparateApis() {
        insertHost(1, "/x", "GET", SingleTypeInfo.GENERIC, OLD);
        insertHost(1, "/x", "POST", SingleTypeInfo.GENERIC, FRESH);

        List<Key> stale = fetch(1);

        assertEquals(1, stale.size());
        assertEquals(Method.GET, stale.get(0).getMethod());
    }

    @Test
    public void emptyCollectionReturnsEmptyList() {
        assertTrue(fetch(99).isEmpty());
    }

    @Test
    public void cutoffIsExclusive() {
        insertHost(1, "/edge", "GET", SingleTypeInfo.GENERIC, CUTOFF);

        assertTrue("lastSeen == cutoff means seen at the cutoff, not older than it", fetch(1).isEmpty());
    }
}
