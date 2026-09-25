package com.akto.dao;

import com.akto.MongoBasedTest;
import com.akto.dao.context.Context;
import com.akto.dto.rbac.UsersCollectionsList;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.mongodb.client.model.Filters;

import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;

/**
 * Covers SingleTypeInfoDao.responseSensitiveSubtypeApiCounts(): response-only scoping
 * (responseCode &gt; -1, the exact regression class documented in this feature's CLAUDE.md for a
 * sibling method), distinct-API counting (not a raw hit/document count -- SingleTypeInfo.count is
 * not a reliable hit counter), RBAC scoping via UsersCollectionsList, and the method's
 * exception-swallow-to-empty-map convention.
 */
public class TestSingleTypeInfoDaoResponseSensitiveSubtypeApiCounts extends MongoBasedTest {

    @Before
    public void setUp() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(1);
        SingleTypeInfoDao.instance.getMCollection().drop();
    }

    private void insertSti(int apiCollectionId, String url, String method, String param, SingleTypeInfo.SubType subType, int responseCode) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, method, responseCode, false, param, subType, apiCollectionId, false);
        SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 1, 0, 0,
                new CappedSet<>(), SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        SingleTypeInfoDao.instance.insertOne(sti);
    }

    private Map<String, Integer> callUnscoped(org.bson.conversions.Bson extraFilter) {
        try (MockedStatic<UsersCollectionsList> m = mockStatic(UsersCollectionsList.class)) {
            m.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt())).thenReturn(null);
            return SingleTypeInfoDao.instance.responseSensitiveSubtypeApiCounts(extraFilter);
        }
    }

    @Test
    public void testEmptyCollection_returnsEmptyMap_noThrow() {
        Map<String, Integer> result = callUnscoped(Filters.empty());
        assertTrue(result.isEmpty());
    }

    @Test
    public void testResponseSideHit_counted() {
        insertSti(1, "/api/resp", "GET", "email", SingleTypeInfo.EMAIL, 200);

        Map<String, Integer> result = callUnscoped(Filters.empty());

        assertEquals(Integer.valueOf(1), result.get(SingleTypeInfo.EMAIL.getName()));
    }

    /** responseCode == -1 means request-side -- must NOT be counted. This is the exact regression
     *  class flagged in ask/CLAUDE.md ("SingleTypeInfoDao.generateFilterForSubtypes's
     *  inResponseOnly parameter is dead code"): responseSensitiveSubtypeApiCounts builds its own
     *  response-only filter specifically to avoid that bug. */
    @Test
    public void testRequestSideOnlyHit_responseCodeMinusOne_notCounted() {
        insertSti(1, "/api/req", "GET", "email", SingleTypeInfo.EMAIL, -1);

        Map<String, Integer> result = callUnscoped(Filters.empty());

        assertTrue("a request-side-only row must not be counted by a response-only aggregation", result.isEmpty());
    }

    @Test
    public void testMixedRequestAndResponseRows_onlyResponseCounted() {
        insertSti(1, "/api/a", "GET", "p1", SingleTypeInfo.EMAIL, -1);  // request side
        insertSti(1, "/api/b", "GET", "p2", SingleTypeInfo.EMAIL, 200); // response side

        Map<String, Integer> result = callUnscoped(Filters.empty());

        assertEquals(Integer.valueOf(1), result.get(SingleTypeInfo.EMAIL.getName()));
    }

    /** Two params on the SAME {apiCollectionId, url, method} with the same subType must count as
     *  ONE distinct API, not two -- this is the "SingleTypeInfo.count is not a reliable hit
     *  counter" guard: the method groups by endpoint identity and counts endpoints, not raw
     *  documents. */
    @Test
    public void testSameApiMultipleParams_sameSubType_countedOnceNotPerDocument() {
        insertSti(1, "/api/same", "GET", "param1", SingleTypeInfo.EMAIL, 200);
        insertSti(1, "/api/same", "GET", "param2", SingleTypeInfo.EMAIL, 200);
        insertSti(1, "/api/same", "GET", "param3", SingleTypeInfo.EMAIL, 200);

        Map<String, Integer> result = callUnscoped(Filters.empty());

        assertEquals("3 documents on the same endpoint must count as 1 distinct API",
                Integer.valueOf(1), result.get(SingleTypeInfo.EMAIL.getName()));
    }

    @Test
    public void testDifferentEndpoints_sameSubType_eachCountedSeparately() {
        insertSti(1, "/api/one", "GET", "p", SingleTypeInfo.EMAIL, 200);
        insertSti(1, "/api/two", "GET", "p", SingleTypeInfo.EMAIL, 200);
        insertSti(1, "/api/one", "POST", "p", SingleTypeInfo.EMAIL, 200); // different method -> different API

        Map<String, Integer> result = callUnscoped(Filters.empty());

        assertEquals(Integer.valueOf(3), result.get(SingleTypeInfo.EMAIL.getName()));
    }

    // ── RBAC scoping via UsersCollectionsList.getCollectionsIdForUser ───────────────────

    @Test
    public void testRbac_docOutsideCallersScope_notCounted() {
        insertSti(1, "/api/in-scope", "GET", "p", SingleTypeInfo.EMAIL, 200);
        insertSti(2, "/api/out-of-scope", "GET", "p", SingleTypeInfo.EMAIL, 200);

        try (MockedStatic<UsersCollectionsList> m = mockStatic(UsersCollectionsList.class)) {
            m.when(() -> UsersCollectionsList.getCollectionsIdForUser(anyInt(), anyInt()))
                    .thenReturn(Arrays.asList(1));

            Map<String, Integer> result = SingleTypeInfoDao.instance.responseSensitiveSubtypeApiCounts(Filters.empty());

            assertEquals("only the in-scope collection's API must be counted",
                    Integer.valueOf(1), result.get(SingleTypeInfo.EMAIL.getName()));
        }
    }

    @Test
    public void testRbac_nullCollectionIds_meansUnrestricted() {
        insertSti(1, "/api/a", "GET", "p", SingleTypeInfo.EMAIL, 200);
        insertSti(2, "/api/b", "GET", "p", SingleTypeInfo.EMAIL, 200);

        Map<String, Integer> result = callUnscoped(Filters.empty());

        assertEquals(Integer.valueOf(2), result.get(SingleTypeInfo.EMAIL.getName()));
    }

    // ── extraFilter is ANDed in ─────────────────────────────────────────────────────

    @Test
    public void testExtraFilter_excludesMatchingCollection() {
        insertSti(1, "/api/a", "GET", "p", SingleTypeInfo.EMAIL, 200);
        insertSti(2, "/api/b", "GET", "p", SingleTypeInfo.EMAIL, 200);

        org.bson.conversions.Bson excludeCollection2 = Filters.nin(SingleTypeInfo._API_COLLECTION_ID, Arrays.asList(2));
        Map<String, Integer> result = callUnscoped(excludeCollection2);

        assertEquals(Integer.valueOf(1), result.get(SingleTypeInfo.EMAIL.getName()));
    }

    // ── exception swallowing: return an empty map, never propagate ─────────────────

    /** Forces an exception from inside the method's own outer try block (sensitiveSubTypeNames()/
     *  sensitiveSubTypeInResponseNames() both call SingleTypeInfo.getAktoDataTypeMap) -- this is
     *  outside the method's own inner best-effort RBAC try/catch, so it must reach the outer catch
     *  and come back as an empty map rather than propagating to the caller. */
    @Test
    public void testInternalFailure_exceptionSwallowed_returnsEmptyMapNotThrown() {
        insertSti(1, "/api/a", "GET", "p", SingleTypeInfo.EMAIL, 200);

        try (MockedStatic<SingleTypeInfo> m = mockStatic(SingleTypeInfo.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {
            m.when(() -> SingleTypeInfo.getAktoDataTypeMap(anyInt())).thenThrow(new RuntimeException("forced failure"));

            Map<String, Integer> result = SingleTypeInfoDao.instance.responseSensitiveSubtypeApiCounts(Filters.empty());

            assertTrue("an internal failure must degrade to an empty map, never propagate", result.isEmpty());
        }
    }
}
