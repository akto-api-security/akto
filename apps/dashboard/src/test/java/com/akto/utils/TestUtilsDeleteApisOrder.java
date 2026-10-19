package com.akto.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountsContextDaoWithRbac;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.SampleDataDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.Key;
import com.akto.dto.traffic.SampleData;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.type.URLMethods.Method;
import com.akto.types.CappedSet;
import com.mongodb.client.model.Filters;

/**
 * deleteApisStiLast must touch single_type_info only after every other collection succeeded, so a
 * half-failed delete leaves the API as an STI candidate and a re-run finishes it. deleteApis (used
 * by existing callers) must keep deleting STI first.
 */
public class TestUtilsDeleteApisOrder extends MongoBasedTest {

    private static final int COLL = 5;

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(null);
        SingleTypeInfoDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        SampleDataDao.instance.getMCollection().drop();
    }

    private List<Key> keys() {
        return Arrays.asList(new Key(COLL, "/a", Method.GET, -1, 0, 0));
    }

    private List<Object> recordDaoOrder(Runnable call, Object failOn) {
        List<Object> order = new ArrayList<>();
        try (MockedStatic<AccountsContextDaoWithRbac> m = mockStatic(AccountsContextDaoWithRbac.class)) {
            m.when(() -> AccountsContextDaoWithRbac.deleteApisPerDao(any(), any(), anyString())).thenAnswer(inv -> {
                Object dao = inv.getArgument(1);
                if (dao == failOn) throw new RuntimeException("simulated failure");
                order.add(dao);
                return null;
            });
            try {
                call.run();
            } catch (RuntimeException expected) {
                // recorded order is what the assertions look at
            }
        }
        return order;
    }

    @Test
    public void deleteApisKeepsStiFirst() {
        List<Object> order = recordDaoOrder(() -> Utils.deleteApis(keys()), null);

        assertEquals(7, order.size());
        assertEquals(SingleTypeInfoDao.instance, order.get(0));
    }

    @Test
    public void deleteApisStiLastTouchesAllSevenDaosWithStiLast() {
        List<Object> order = recordDaoOrder(() -> Utils.deleteApisStiLast(keys()), null);

        assertEquals(7, order.size());
        assertEquals(SingleTypeInfoDao.instance, order.get(6));
        assertEquals(1, order.stream().filter(d -> d == SingleTypeInfoDao.instance).count());
    }

    @Test
    public void failureInAnotherDaoNeverReachesSti() {
        List<Object> order = recordDaoOrder(() -> Utils.deleteApisStiLast(keys()), SampleDataDao.instance);

        assertFalse("STI must survive so the API stays a candidate for the re-run", order.contains(SingleTypeInfoDao.instance));
    }

    @Test
    public void deletesOnlyTheTargetedApiAcrossCollections() {
        insertApi("/a");
        insertApi("/b");

        Utils.deleteApisStiLast(keys());

        assertEquals(0, SingleTypeInfoDao.instance.count(Filters.eq("url", "/a")));
        assertEquals(0, ApiInfoDao.instance.count(Filters.eq("_id.url", "/a")));
        assertEquals(0, SampleDataDao.instance.count(Filters.eq("_id.url", "/a")));
        assertEquals(1, SingleTypeInfoDao.instance.count(Filters.eq("url", "/b")));
        assertEquals(1, ApiInfoDao.instance.count(Filters.eq("_id.url", "/b")));
        assertEquals(1, SampleDataDao.instance.count(Filters.eq("_id.url", "/b")));
    }

    @Test
    public void rerunAfterCompletionIsANoOp() {
        insertApi("/a");

        Utils.deleteApisStiLast(keys());
        try {
            Utils.deleteApisStiLast(keys());
        } catch (Exception e) {
            fail("deleting an already-deleted API must be idempotent: " + e);
        }

        assertTrue(SingleTypeInfoDao.instance.count(Filters.eq("url", "/a")) == 0);
    }

    private void insertApi(String url) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, "GET", -1, true, "host", SingleTypeInfo.GENERIC, COLL, false);
        SingleTypeInfoDao.instance.insertOne(new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 1, 0, 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE));
        ApiInfoDao.instance.insertOne(new ApiInfo(COLL, url, Method.GET));
        SampleDataDao.instance.insertOne(new SampleData(new Key(COLL, url, Method.GET, 0, 0, 0), new ArrayList<>()));
    }
}
