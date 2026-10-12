package com.akto.service.collections;

import com.akto.MongoBasedTest;
import com.akto.dao.AccountSettingsDao;
import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionStatsMetaDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.EndpointInfoViewDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiType;
import com.akto.dto.ApiInfo.AuthType;
import com.akto.dto.ApiStats;
import com.akto.dto.AccountSettings;
import com.akto.dto.EndpointInfoView;
import com.akto.dto.type.URLMethods.Method;
import com.akto.service.collections.CollectionStatsRefresher.Metric;
import com.akto.dto.ApiCollectionStatsMeta;
import com.mongodb.client.model.Updates;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The accounts that read their api numbers from endpoint_info_views. */
public class TestViewPosture extends MongoBasedTest {

    @Before
    public void reset() {
        EndpointInfoViewDao.instance.getMCollection().drop();
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionStatsDao.instance.getMCollection().drop();
        ApiCollectionStatsMetaDao.instance.getMCollection().drop();
        AccountSettingsDao.instance.getMCollection().updateOne(AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.ENABLE_ENDPOINT_INFO_VIEW, true), new com.mongodb.client.model.UpdateOptions().upsert(true));
    }

    /** The embedded database is shared with the other tests: leave it as an account without the view. */
    @After
    public void turnTheViewOff() {
        EndpointInfoViewDao.instance.getMCollection().drop();
        AccountSettingsDao.instance.getMCollection().updateOne(AccountSettingsDao.generateFilter(),
                Updates.set(AccountSettings.ENABLE_ENDPOINT_INFO_VIEW, false));
    }

    private EndpointInfoView endpoint(int collectionId, String url, float risk, String auth, ApiAccessType access,
                                      boolean sensitive, int lastTested, int discoveredAgo) {
        EndpointInfoView view = new EndpointInfoView();
        view.setApiCollectionId(collectionId);
        view.setUrl(url);
        view.setMethod(Method.GET);
        view.setRiskScore(risk);
        view.setActualAuthType(Collections.singletonList(auth));
        view.setApiAccessTypes(new HashSet<>(Collections.singletonList(access)));
        view.setActualAccessType(Collections.singletonList(access.name()));
        view.setApiType(ApiType.REST);
        view.setSensitiveSubTypes(sensitive ? Collections.singletonList("EMAIL") : Collections.emptyList());
        view.setLastTested(lastTested);
        view.setDiscoveredTimestamp(Context.now() - discoveredAgo);
        view.setIsOutOfTestingScope(false);
        return view;
    }

    private void seed() {
        EndpointInfoViewDao.instance.insertMany(Arrays.asList(
                endpoint(1, "/a", 4.5f, AuthType.UNAUTHENTICATED, ApiAccessType.PUBLIC, true, 0, 100),
                endpoint(1, "/b", 1f, AuthType.JWT, ApiAccessType.THIRD_PARTY, false, Context.now() - 10, 100),
                endpoint(2, "/c", 4f, AuthType.JWT, ApiAccessType.THIRD_PARTY, true, 0, 10)));
    }

    private void markAllMetricsFresh() {
        for (Metric metric : Metric.values()) {
            ApiCollectionStatsMeta meta = new ApiCollectionStatsMeta();
            meta.setId(metric.name());
            meta.setRefreshedAt(Context.now());
            ApiCollectionStatsMetaDao.instance.getMCollection().insertOne(meta);
        }
    }

    @Test
    public void anAccountWithAFilledViewReadsFromIt() {
        assertTrue("an empty view is not read from", !PostureSource.viewHasRows());
        seed();
        assertTrue(PostureSource.viewHasRows());
        assertTrue(PostureSource.forAccount() instanceof ViewPostureSource);
    }

    @Test
    public void snapshotComesFromTheView() {
        seed();
        markAllMetricsFresh();

        PostureSnapshotResponse snapshot = new PostureSnapshotService().fetch(Context.now() - 50, Context.now(), false);

        ApiStats end = snapshot.getApiStatsEnd();
        assertEquals(3, end.getTotalAPIs());
        assertEquals(1, end.getAuthTypeMap().get(AuthType.UNAUTHENTICATED).intValue());
        assertEquals(2, end.getAccessTypeMap().get(ApiAccessType.THIRD_PARTY).intValue());
        assertEquals("only the apis discovered by the start of the range", 2, snapshot.getApiStatsStart().getTotalAPIs());

        PostureSnapshotResponse.ActionItemCounts counts = snapshot.getActionItemCounts();
        assertEquals(2, counts.getHighRisk());
        assertEquals(1, counts.getHighRiskThirdParty());
        assertEquals(1, counts.getUnauthenticated());
        assertEquals(2, counts.getSensitiveApis());
        assertEquals(1, counts.getSensitiveAndUnauthenticated());
        assertEquals(2, counts.getNotTested());
        assertEquals("the view has no times tested", -1, counts.getOnlyOnceTested());
        assertEquals(0, snapshot.getMissing().getNetMissing());
    }

    @Test
    public void actionItemListsComeFromTheViewAndAgreeWithTheCounts() {
        seed();

        PostureActionItemsResponse highRisk = new PostureActionItemsService().fetch(PostureActionItemsService.Type.HIGH_RISK, 0, 10);
        assertEquals(2, highRisk.getTotal());
        assertEquals(2, highRisk.getRows().size());
        assertEquals("/a", highRisk.getRows().get(0).getUrl());

        PostureActionItemsResponse firstOfTwo = new PostureActionItemsService().fetch(PostureActionItemsService.Type.SENSITIVE, 0, 1);
        assertEquals(2, firstOfTwo.getTotal());
        assertEquals(1, firstOfTwo.getRows().size());

        PostureActionItemsResponse onlyOnce = new PostureActionItemsService().fetch(PostureActionItemsService.Type.ONLY_ONCE_TESTED, 0, 10);
        assertEquals(-1, onlyOnce.getTotal());
        assertEquals(0, onlyOnce.getRows().size());
    }
}
