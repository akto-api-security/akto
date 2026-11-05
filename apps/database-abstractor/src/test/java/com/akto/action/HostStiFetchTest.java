package com.akto.action;

import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.types.CappedSet;
import com.akto.util.Constants;
import com.akto.utils.MongoBasedTest;
import com.mongodb.BasicDBObject;
import org.apache.struts2.json.JSONPopulator;
import org.apache.struts2.json.JSONUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The host-header STI endpoints a mini-runtime refreshes its view of known APIs from: fetchStiBasedOnHostHeaders
 * (now with an optional lastSeenGte) and fetchStiCountBasedOnHostHeaders. Driven through DbAction, against Mongo.
 */
public class HostStiFetchTest extends MongoBasedTest {

    private static final int COLLECTION_ID = 7001;
    private static final int T = 1_700_000_000;

    @Before
    public void emptyStis() {
        Context.accountId.set(ACCOUNT_ID);
        SingleTypeInfoDao.instance.getMCollection().deleteMany(new BasicDBObject());
    }

    @After
    public void restoreDefaultAccount() {
        Context.accountId.set(ACCOUNT_ID);
    }

    private static SingleTypeInfo sti(String url, String param, boolean isHeader, int responseCode, int lastSeen) {
        SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(url, "GET", responseCode, isHeader, param, SingleTypeInfo.GENERIC, COLLECTION_ID, false);
        SingleTypeInfo sti = new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 1, T, 0, new CappedSet<>(),
                SingleTypeInfo.Domain.ENUM, SingleTypeInfo.ACCEPTED_MAX_VALUE, SingleTypeInfo.ACCEPTED_MIN_VALUE);
        sti.setLastSeen(lastSeen);
        return sti;
    }

    private static SingleTypeInfo hostSti(String url, int lastSeen) {
        return sti(url, "host", true, -1, lastSeen);
    }

    private static void insert(List<SingleTypeInfo> stis) {
        SingleTypeInfoDao.instance.insertMany(stis);
    }

    private static List<String> urls(List<SingleTypeInfo> stis) {
        return stis.stream().map(SingleTypeInfo::getUrl).collect(Collectors.toList());
    }

    private static List<SingleTypeInfo> fetchPage(String lastStiId, int lastSeenGte) {
        DbAction action = new DbAction();
        action.setLastStiId(lastStiId);
        action.setLastSeenGte(lastSeenGte);
        assertEquals("SUCCESS", action.fetchStiBasedOnHostHeaders());
        return action.getStis();
    }

    /** Pages the way ClientActor does: cursor = hex _id of the last STI returned, until a page comes back empty. */
    private static List<SingleTypeInfo> walk(int lastSeenGte) {
        List<SingleTypeInfo> all = new ArrayList<>();
        String cursor = null;
        while (true) {
            List<SingleTypeInfo> page = fetchPage(cursor, lastSeenGte);
            if (page.isEmpty()) {
                return all;
            }
            all.addAll(page);
            cursor = page.get(page.size() - 1).getId().toHexString();
        }
    }

    private static long count() {
        DbAction action = new DbAction();
        assertEquals("SUCCESS", action.fetchStiCountBasedOnHostHeaders());
        return action.getCount();
    }

    @Test
    public void lastSeenGteKeepsOnlyStisSeenAtOrAfterIt() {
        insert(Arrays.asList(hostSti("/a", T), hostSti("/b", T + 100), hostSti("/c", T + 200)));

        assertEquals(Arrays.asList("/a", "/b", "/c"), urls(walk(0)));
        assertEquals("the boundary is inclusive", Arrays.asList("/b", "/c"), urls(walk(T + 100)));
        assertTrue(walk(T + 201).isEmpty());
    }

    @Test
    public void requestsWithoutLastSeenGteBehaveAsBefore() {
        insert(Arrays.asList(hostSti("/a", T), hostSti("/b", T + 100)));

        // an old client never sends lastSeenGte, so the action sees the default
        assertEquals(Arrays.asList("/a", "/b"), urls(fetchPage(null, 0)));
        assertEquals(urls(fetchPage(null, 0)), urls(com.akto.data_actor.DbLayer.fetchStiBasedOnHostHeaders(null)));
    }

    @Test
    public void lastSeenGteComposesWithTheIdCursor() {
        insert(Arrays.asList(hostSti("/a", T + 300), hostSti("/b", T), hostSti("/c", T + 300)));

        String afterA = fetchPage(null, 0).get(0).getId().toHexString();
        // /b is after the cursor but too old, /c is after the cursor and recent enough
        assertEquals(Arrays.asList("/c"), urls(fetchPage(afterA, T + 300)));
    }

    @Test
    public void onlyHostHeaderStisAreReturnedAndCounted() {
        insert(Arrays.asList(
                hostSti("/a", T),
                sti("/a", "x-other", true, -1, T),     // another request header
                sti("/a", "host", true, 200, T),       // a response header
                sti("/a", "host", false, -1, T)));     // a body parameter

        assertEquals(Arrays.asList("/a"), urls(walk(0)));
        assertEquals(1, count());
    }

    @Test
    public void walkingPagesCorrectlyAcrossSeveralPagesWithAndWithoutTheFilter() {
        List<SingleTypeInfo> stis = new ArrayList<>();
        for (int i = 0; i < 2500; i++) {
            stis.add(hostSti("/api/" + i, T + i));
        }
        insert(stis);

        assertEquals(2500, walk(0).size());
        List<SingleTypeInfo> recent = walk(T + 500);
        assertEquals(2000, recent.size());
        assertEquals("/api/500", recent.get(0).getUrl());
        assertEquals("/api/2499", recent.get(recent.size() - 1).getUrl());
        assertEquals("no STI is returned twice", 2500, new HashSet<>(urls(walk(0))).size());
    }

    @Test
    public void countEqualsWhatAFullWalkReturns() {
        assertEquals(0, count());

        List<SingleTypeInfo> stis = new ArrayList<>();
        for (int i = 0; i < 1234; i++) {
            stis.add(hostSti("/api/" + i, T));
        }
        insert(stis);

        assertEquals(walk(0).size(), count());
        assertEquals(1234, count());
    }

    @Test
    public void countIsMinusOneWhereResultsAreFilteredInJavaAfterTheQuery() {
        insert(Arrays.asList(hostSti("/a", T)));

        for (int accountId : new int[]{Constants.ROUTING_SKIP_ACCOUNT_ID, Constants.MERGED_URLS_FILTER_ACCOUNT_ID}) {
            Context.accountId.set(accountId);
            assertEquals("a DB count would overstate what a walk returns for account " + accountId, -1, count());
        }
        Context.accountId.set(ACCOUNT_ID);
        assertEquals(1, count());
    }

    /*
     * Mini-runtimes are updated by customers at their own pace, so this server has to answer every client version.
     * These bind request bodies the way the json interceptor does, and run the action on the result.
     */
    private static DbAction actionFromRequestBody(String requestBody) throws Exception {
        DbAction action = new DbAction();
        new JSONPopulator().populateObject(action, (Map) JSONUtil.deserialize(requestBody));
        return action;
    }

    @Test
    public void aClientThatDoesNotKnowLastSeenGteGetsEverySti() throws Exception {
        insert(Arrays.asList(hostSti("/a", T), hostSti("/b", T + 100), hostSti("/c", T + 200)));

        // what every mini-runtime older than lastSeenGte sends
        DbAction action = actionFromRequestBody("{\"lastStiId\":null}");

        assertEquals("SUCCESS", action.fetchStiBasedOnHostHeaders());
        assertEquals(Arrays.asList("/a", "/b", "/c"), urls(action.getStis()));
    }

    @Test
    public void aClientThatKnowsLastSeenGteGetsOnlyTheRecentOnes() throws Exception {
        insert(Arrays.asList(hostSti("/a", T), hostSti("/b", T + 100), hostSti("/c", T + 200)));

        DbAction action = actionFromRequestBody("{\"lastStiId\":null,\"lastSeenGte\":" + (T + 100) + "}");

        assertEquals("SUCCESS", action.fetchStiBasedOnHostHeaders());
        assertEquals(Arrays.asList("/b", "/c"), urls(action.getStis()));
    }

    @Test
    public void fieldsThisServerDoesNotKnowAreIgnored() throws Exception {
        insert(Arrays.asList(hostSti("/a", T)));

        // a client newer than this server must not break it
        DbAction action = actionFromRequestBody("{\"lastStiId\":null,\"someFutureField\":1}");

        assertEquals("SUCCESS", action.fetchStiBasedOnHostHeaders());
        assertEquals(Arrays.asList("/a"), urls(action.getStis()));
    }

    @Test
    public void theCountEndpointWorksWithTheBodyTheClientSends() throws Exception {
        insert(Arrays.asList(hostSti("/a", T), hostSti("/b", T)));

        DbAction action = actionFromRequestBody("{}");

        assertEquals("SUCCESS", action.fetchStiCountBasedOnHostHeaders());
        assertEquals(2, action.getCount());
    }
}
