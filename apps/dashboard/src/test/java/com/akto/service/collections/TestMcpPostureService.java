package com.akto.service.collections;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionStatsDao;
import com.akto.dao.ApiCollectionStatsMetaDao;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.McpAuditInfoDao;
import com.akto.dao.context.Context;
import com.akto.dao.threat_detection.ApiHitCountInfoDao;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.ApiInfo.ApiAccessType;
import com.akto.dto.ApiInfo.ApiInfoKey;
import com.akto.dto.McpAuditInfo;
import com.akto.dto.threat_detection.ApiHitCountInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.URLMethods.Method;
import com.akto.util.Constants;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class TestMcpPostureService extends MongoBasedTest {

    @Before
    public void reset() {
        ApiCollectionsDao.instance.getMCollection().drop();
        ApiCollectionStatsDao.instance.getMCollection().drop();
        ApiCollectionStatsMetaDao.instance.getMCollection().drop();
        ApiInfoDao.instance.getMCollection().drop();
        McpAuditInfoDao.instance.getMCollection().drop();
        ApiHitCountInfoDao.instance.getMCollection().drop();
    }

    private ApiCollection tagged(int id, String tag) {
        ApiCollection c = new ApiCollection(id, "c" + id, 100 + id, new HashSet<>(), "h" + id + ".com", 0, false, true);
        c.setTagsList(Collections.singletonList(new CollectionTags(Context.now(), tag, "value", CollectionTags.TagSource.USER)));
        return c;
    }

    private ApiInfo api(int collectionId, String url, float risk, boolean thirdParty, int discoveredAgo) {
        ApiInfo info = new ApiInfo(new ApiInfoKey(collectionId, url, Method.GET));
        info.setRiskScore(risk);
        info.setApiAccessTypes(new HashSet<>(Collections.singletonList(thirdParty ? ApiAccessType.THIRD_PARTY : ApiAccessType.PRIVATE)));
        info.setDiscoveredTimestamp(Context.now() - discoveredAgo);
        return info;
    }

    private void seed() {
        ApiCollectionsDao.instance.insertMany(Arrays.asList(
                tagged(1, Constants.AKTO_MCP_SERVER_TAG), tagged(2, Constants.AKTO_MCP_SERVER_TAG), tagged(3, Constants.AKTO_GUARD_RAIL_TAG),
                new ApiCollection(4, "plain", 100, new HashSet<>(), "plain.com", 0, false, true)));
        new CollectionAttrsSync().syncAll();
        ApiCollectionStatsDao.instance.getMCollection().updateOne(
                com.mongodb.client.model.Filters.eq(com.akto.dto.ApiCollectionStats.ID, 3),
                com.mongodb.client.model.Updates.set(com.akto.dto.ApiCollectionStats.ENDPOINTS_COUNT, 7));
    }

    @Test
    public void countsAreOnlyOfTheTaggedCollections() {
        seed();
        ApiInfoDao.instance.insertMany(Arrays.asList(
                api(1, "/a", 4f, true, 100), api(1, "/b", 1f, false, 30 * 24 * 3600), api(2, "/c", 5f, true, 10),
                // not an mcp collection
                api(4, "/d", 5f, true, 10)));
        McpAuditInfoDao.instance.insertMany(Arrays.asList(
                new McpAuditInfo(1, "", Constants.AKTO_MCP_TOOL, 1, "t1", "", null, 1, "h"),
                new McpAuditInfo(1, "", Constants.AKTO_MCP_TOOL, 1, "t2", "Rejected", null, 1, "h"),
                new McpAuditInfo(1, "", Constants.AKTO_MCP_SERVER, 1, "s1", "", null, 1, "h")));

        McpPostureResponses.Counts counts = new McpPostureService().counts();

        assertEquals(3, counts.getTotalApis());
        assertEquals(2, counts.getNewApis7Days());
        assertEquals(2, counts.getThirdPartyApis());
        assertEquals("risk score of 4 or more", 2, counts.getCriticalApis());
        assertEquals("rejected audit infos are not counted", 1, counts.getTools());
        assertEquals(1, counts.getServers());
        assertEquals(0, counts.getPrompts());
        assertEquals("the guardrail collection's endpoint count", 7, counts.getPolicyGuardrailApis());
    }

    @Test
    public void topApplicationsArePagedByTraffic() {
        seed();
        int ts = Context.now() / 60;
        ApiHitCountInfoDao.instance.insertMany(Arrays.asList(
                new ApiHitCountInfo(1, "/a", "GET", 5, ts), new ApiHitCountInfo(1, "/b", "GET", 5, ts),
                new ApiHitCountInfo(2, "/c", "GET", 3, ts), new ApiHitCountInfo(4, "/d", "GET", 100, ts)));

        McpPostureResponses.Data first = new McpPostureService().data(McpPostureService.Section.TOP_APPLICATIONS, 0, 1);
        McpPostureResponses.Data second = new McpPostureService().data(McpPostureService.Section.TOP_APPLICATIONS, 1, 1);

        assertEquals(2, first.getTotal());
        assertEquals(1, first.getTopApplications().get(0).getCollectionId());
        assertEquals(10, first.getTopApplications().get(0).getHitCount());
        assertEquals(2, second.getTopApplications().get(0).getCollectionId());
    }

    @Test
    public void apiCallSeriesSumsTheTaggedCollectionsPerBucket() {
        seed();
        int now = Context.now();
        int ts = now / 60;
        ApiHitCountInfoDao.instance.insertMany(Arrays.asList(
                new ApiHitCountInfo(1, "/a", "GET", 5, ts), new ApiHitCountInfo(2, "/c", "GET", 3, ts),
                new ApiHitCountInfo(4, "/d", "GET", 100, ts)));

        McpPostureResponses.Series series = new McpPostureService().apiCallSeries(McpPostureService.Tag.MCP, now - 1800, now + 60);

        assertEquals(1, series.getPoints().size());
        assertEquals(ts, series.getPoints().get(0).getTs());
        assertEquals(8, series.getPoints().get(0).getCount());
    }

    @Test
    public void theSeriesGetsCoarserWithTheRange() {
        assertEquals(1, McpPostureService.bucketMinutes(600));
        assertEquals(5, McpPostureService.bucketMinutes(McpPostureService.DAY_SECONDS));
        assertEquals(60, McpPostureService.bucketMinutes(3 * McpPostureService.DAY_SECONDS));
        assertEquals(360, McpPostureService.bucketMinutes(20 * McpPostureService.DAY_SECONDS));
    }
}
