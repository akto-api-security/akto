package com.akto.utils.crons;

import com.akto.DaoInit;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.ApiInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.ApiInfo;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.URLMethods;
import com.akto.dao.insights.InsightClassificationCacheDao;
import com.akto.dto.insights.InsightClassificationCache;
import com.akto.service.insights.InsightClassificationHelper;
import com.akto.service.insights.InsightUtil;
import com.akto.util.Constants;
import com.mongodb.ConnectionString;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import com.mongodb.client.model.WriteModel;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestToolClassificationCronIntegration {

    private static final int ACCOUNT_ID = 1_000_000;
    private static final int ARGUS_COLL = 999_000_001;
    private static final int ATLAS_COLL = 999_000_002;
    private static final int THIRTY_DAYS = 30 * 24 * 60 * 60;

    private final ToolClassificationCron cron = new ToolClassificationCron();

    @BeforeClass
    public static void connect() {
        DaoInit.init(new ConnectionString("mongodb://localhost:27017"));
    }

    @Before
    public void seed() {
        Context.accountId.set(ACCOUNT_ID);
        Context.userId.set(null);
        Context.contextSource.set(null);
        cleanup();

        ApiCollectionsDao.instance.insertOne(collection(ARGUS_COLL, "seeded-argus-mcp",
                tag(Constants.AKTO_MCP_SERVER_TAG, "MCP Server")));
        ApiCollectionsDao.instance.insertOne(collection(ATLAS_COLL, "seeded.atlas.endpoint",
                tag(Constants.AKTO_GEN_AI_TAG, "Gen AI"),
                tag(Constants.AKTO_ENDPOINT_SOURCE_TAG, "ENDPOINT")));
    }

    @After
    public void cleanup() {
        ApiCollectionsDao.instance.deleteAll(Filters.in(Constants.ID, ARGUS_COLL, ATLAS_COLL));
        ApiInfoDao.instance.deleteAll(Filters.in(ApiInfo.ID_API_COLLECTION_ID, ARGUS_COLL, ATLAS_COLL));
    }

    private static CollectionTags tag(String key, String value) {
        return new CollectionTags(Context.now(), key, value, CollectionTags.TagSource.KUBERNETES);
    }

    private static ApiCollection collection(int id, String host, CollectionTags... tags) {
        ApiCollection c = new ApiCollection(id, host, Context.now(), new java.util.HashSet<>(), host, id, false, true);
        c.setTagsList(new ArrayList<>(Arrays.asList(tags)));
        return c;
    }

    private static ApiInfo toolRow(int collectionId, String url, int lastSeen, Integer calculatedAt) {
        ApiInfo info = new ApiInfo(new ApiInfo.ApiInfoKey(collectionId, url, URLMethods.Method.POST));
        info.setLastSeen(lastSeen);
        ApiInfoDao.instance.insertOne(info);
        if (calculatedAt != null) {
            ApiInfoDao.instance.updateOneNoUpsert(ApiInfoDao.getFilter(info.getId()),
                    Updates.combine(Updates.set(ApiInfo.TOOL_INFO_CAPABILITY, "SAFE"),
                            Updates.set(ApiInfo.TOOL_INFO_CALCULATED_AT, calculatedAt)));
        }
        return info;
    }

    private static List<String> urlsOf(List<ApiInfo> rows) {
        List<String> urls = new ArrayList<>();
        for (ApiInfo r : rows) urls.add(r.getId().getUrl());
        return urls;
    }

    private List<ApiInfo> seededCandidates() {
        List<ApiInfo> mine = new ArrayList<>();
        for (ApiInfo r : cron.findCandidates()) {
            if (r.getId().getApiCollectionId() == ARGUS_COLL || r.getId().getApiCollectionId() == ATLAS_COLL) {
                mine.add(r);
            }
        }
        return mine;
    }

    @Test
    public void unclassifiedToolRowIsACandidate() {
        toolRow(ARGUS_COLL, "/mcp/tools/call/drop_table", Context.now(), null);
        assertEquals(Arrays.asList("/mcp/tools/call/drop_table"), urlsOf(seededCandidates()));
    }

    @Test
    public void nonToolUrlIsNotACandidate() {
        toolRow(ARGUS_COLL, "/mcp/initialize", Context.now(), null);
        toolRow(ARGUS_COLL, "/chat/completions", Context.now(), null);
        assertTrue(seededCandidates().isEmpty());
    }

    @Test
    public void freshlyClassifiedRowIsNotACandidate() {
        toolRow(ARGUS_COLL, "/mcp/tools/call/fresh", Context.now(), Context.now());
        assertTrue(seededCandidates().isEmpty());
    }

    @Test
    public void staleClassifiedRowIsACandidateAgain() {
        toolRow(ARGUS_COLL, "/mcp/tools/call/stale", Context.now(), Context.now() - THIRTY_DAYS - 60);
        assertEquals(Arrays.asList("/mcp/tools/call/stale"), urlsOf(seededCandidates()));
    }

    @Test
    public void rowJustInsideTheThresholdIsNotACandidate() {
        toolRow(ARGUS_COLL, "/mcp/tools/call/borderline", Context.now(), Context.now() - THIRTY_DAYS + 600);
        assertTrue(seededCandidates().isEmpty());
    }

    @Test
    public void atlasCollectionToolsAreNeverCandidates() {
        toolRow(ATLAS_COLL, "/mcp/tools/call/atlas_tool", Context.now(), null);
        assertTrue("source=ENDPOINT collections belong to Atlas, not Argus", seededCandidates().isEmpty());
    }

    @Test
    public void candidatesComeBackNewestSeenFirst() {
        int now = Context.now();
        toolRow(ARGUS_COLL, "/mcp/tools/call/oldest", now - 5000, null);
        toolRow(ARGUS_COLL, "/mcp/tools/call/newest", now, null);
        toolRow(ARGUS_COLL, "/mcp/tools/call/middle", now - 2500, null);
        assertEquals(Arrays.asList("/mcp/tools/call/newest", "/mcp/tools/call/middle", "/mcp/tools/call/oldest"),
                urlsOf(seededCandidates()));
    }

    @Test
    public void candidateBatchIsCappedPerAccount() {
        int now = Context.now();
        for (int i = 0; i < 260; i++) {
            toolRow(ARGUS_COLL, "/mcp/tools/call/bulk_" + i, now - i, null);
        }
        assertEquals(200, cron.findCandidates().size());
    }

    @Test
    public void classificationRoundTripsThroughBulkWrite() {
        ApiInfo row = toolRow(ARGUS_COLL, "/mcp/tools/call/persist_me", Context.now(), null);
        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        updates.add(new UpdateOneModel<>(ApiInfoDao.getFilter(row.getId()),
                Updates.combine(Updates.set(ApiInfo.TOOL_INFO_CAPABILITY, "RESOURCE_DELETE"),
                        Updates.set(ApiInfo.TOOL_INFO_CALCULATED_AT, Context.now()))));
        ApiInfoDao.instance.bulkWrite(updates, new com.mongodb.client.model.BulkWriteOptions().ordered(false));

        ApiInfo stored = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(row.getId()));
        assertNotNull(stored);
        assertNotNull(stored.getToolInfo());
        assertEquals("RESOURCE_DELETE", stored.getToolInfo().getCapability());
        assertTrue(stored.getToolInfo().getCalculatedAt() > 0);

        assertTrue("a written row must drop out of the candidate set", seededCandidates().isEmpty());
    }

    @Test
    public void realArgusDataHasNoUnclassifiedToolsLeft() {
        cleanup();
        for (ApiInfo r : cron.findCandidates()) {
            assertFalse("unexpected leftover candidate: " + r.getId().getUrl(),
                    r.getId().getUrl().contains("/tools/call/"));
        }
    }

    @Test
    public void genAiCollectionWithoutEndpointTagIsArgus() {
        ApiCollectionsDao.instance.deleteAll(Filters.eq(Constants.ID, ARGUS_COLL));
        ApiCollectionsDao.instance.insertOne(collection(ARGUS_COLL, "seeded-genai",
                tag(Constants.AKTO_GEN_AI_TAG, "Gen AI")));
        toolRow(ARGUS_COLL, "/mcp/tools/call/genai_tool", Context.now(), null);
        assertEquals(1, seededCandidates().size());
    }

    @Test
    public void untaggedCollectionIsNotArgus() {
        ApiCollectionsDao.instance.deleteAll(Filters.eq(Constants.ID, ARGUS_COLL));
        ApiCollectionsDao.instance.insertOne(collection(ARGUS_COLL, "seeded-untagged"));
        toolRow(ARGUS_COLL, "/mcp/tools/call/untagged_tool", Context.now(), null);
        assertTrue(seededCandidates().isEmpty());
    }

    @Test
    public void deactivatedArgusCollectionStillYieldsCandidates() {
        ApiCollectionsDao.instance.updateOneNoUpsert(Filters.eq(Constants.ID, ARGUS_COLL),
                Updates.set(ApiCollection._DEACTIVATED, true));
        toolRow(ARGUS_COLL, "/mcp/tools/call/on_deactivated", Context.now(), null);
        assertEquals("documents current behaviour: the agentic filter is tags-only, it ignores deactivated",
                1, seededCandidates().size());
    }

    @Test
    public void capabilityWithoutCalculatedAtIsACandidate() {
        ApiInfo row = toolRow(ARGUS_COLL, "/mcp/tools/call/half_written", Context.now(), null);
        ApiInfoDao.instance.updateOneNoUpsert(ApiInfoDao.getFilter(row.getId()),
                Updates.set(ApiInfo.TOOL_INFO_CAPABILITY, "SAFE"));
        assertEquals(1, seededCandidates().size());
    }

    @Test
    public void uppercaseToolUrlIsNotMatched() {
        toolRow(ARGUS_COLL, "/mcp/TOOLS/call/shouty", Context.now(), null);
        assertTrue("the tool-url regex is case sensitive", seededCandidates().isEmpty());
    }

    @Test
    public void exactlyTheLimitIsReturnedWhole() {
        int now = Context.now();
        for (int i = 0; i < 200; i++) toolRow(ARGUS_COLL, "/mcp/tools/call/exact_" + i, now - i, null);
        assertEquals(200, seededCandidates().size());
    }

    @Test
    public void rowsWithNoLastSeenSortLast() {
        int now = Context.now();
        toolRow(ARGUS_COLL, "/mcp/tools/call/seen", now, null);
        toolRow(ARGUS_COLL, "/mcp/tools/call/never_seen", 0, null);
        assertEquals(Arrays.asList("/mcp/tools/call/seen", "/mcp/tools/call/never_seen"),
                urlsOf(seededCandidates()));
    }

    @Test
    public void corruptCacheEntryFailsInsteadOfReturningSafe() {
        String toolName = "corrupt_cache_probe";
        String sample = "{}";
        String id = "tool:" + InsightUtil.md5(toolName.toLowerCase() + "|" + sample);
        InsightClassificationCache entry = new InsightClassificationCache(
                id, "ToolCapabilityClassifier", "this is not json",
                Context.now(), new java.util.Date((Context.now() + 3600L) * 1000L));
        InsightClassificationCacheDao.instance.bulkPut(Collections.singletonList(entry));
        try {
            assertNull("a corrupt cache entry must not be read back as SAFE",
                    InsightClassificationHelper.classifyToolDanger(toolName, sample));
        } finally {
            InsightClassificationCacheDao.instance.deleteAll(Filters.eq(Constants.ID, id));
        }
    }

    @Test
    public void validCacheEntryIsReturnedWithoutCallingTheModel() {
        String toolName = "cached_probe";
        String sample = "{}";
        String id = "tool:" + InsightUtil.md5(toolName.toLowerCase() + "|" + sample);
        InsightClassificationCache entry = new InsightClassificationCache(
                id, "ToolCapabilityClassifier", "{\"capability\":\"FILE_WRITE\",\"dangerous\":true}",
                Context.now(), new java.util.Date((Context.now() + 3600L) * 1000L));
        InsightClassificationCacheDao.instance.bulkPut(Collections.singletonList(entry));
        try {
            InsightClassificationHelper.ToolDangerVerdict v =
                    InsightClassificationHelper.classifyToolDanger(toolName, sample);
            assertNotNull(v);
            assertEquals("FILE_WRITE", v.capability);
            assertTrue(v.dangerous);
        } finally {
            InsightClassificationCacheDao.instance.deleteAll(Filters.eq(Constants.ID, id));
        }
    }

    @Test
    public void processAccountSkipsEntirelyWhenFeatureNotGranted() {
        ApiInfo row = toolRow(ARGUS_COLL, "/mcp/tools/call/gated", Context.now(), null);
        try (org.mockito.MockedStatic<com.akto.billing.UsageMetricUtils> usage =
                     org.mockito.Mockito.mockStatic(com.akto.billing.UsageMetricUtils.class)) {
            usage.when(() -> com.akto.billing.UsageMetricUtils.getFeatureAccessSaas(
                    org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyString()))
                    .thenReturn(new com.akto.dto.billing.FeatureAccess(false));
            cron.processAccount(new com.akto.dto.Account(ACCOUNT_ID, "test"));
        }
        ApiInfo stored = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(row.getId()));
        assertNotNull(stored);
        assertNull("an ungranted account must not be classified at all", stored.getToolInfo());
    }

    @Test
    public void processAccountSkipsWhenFeatureAccessIsNull() {
        ApiInfo row = toolRow(ARGUS_COLL, "/mcp/tools/call/null_access", Context.now(), null);
        try (org.mockito.MockedStatic<com.akto.billing.UsageMetricUtils> usage =
                     org.mockito.Mockito.mockStatic(com.akto.billing.UsageMetricUtils.class)) {
            usage.when(() -> com.akto.billing.UsageMetricUtils.getFeatureAccessSaas(
                    org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyString())).thenReturn(null);
            cron.processAccount(new com.akto.dto.Account(ACCOUNT_ID, "test"));
        }
        assertNull(ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(row.getId())).getToolInfo());
    }

    @Test
    public void processAccountIsANoOpWhenThereAreNoCandidates() {
        cleanup();
        try (org.mockito.MockedStatic<com.akto.billing.UsageMetricUtils> usage =
                     org.mockito.Mockito.mockStatic(com.akto.billing.UsageMetricUtils.class)) {
            usage.when(() -> com.akto.billing.UsageMetricUtils.getFeatureAccessSaas(
                    org.mockito.Mockito.anyInt(), org.mockito.Mockito.anyString()))
                    .thenReturn(new com.akto.dto.billing.FeatureAccess(true));
            cron.processAccount(new com.akto.dto.Account(ACCOUNT_ID, "test"));
        }
    }

    @Test
    public void sameRowTwiceInOneBatchConvergesOnOneValue() {
        ApiInfo row = toolRow(ARGUS_COLL, "/mcp/tools/call/dupe", Context.now(), null);
        List<WriteModel<ApiInfo>> updates = new ArrayList<>();
        for (String cap : new String[]{"SAFE", "RESOURCE_DELETE"}) {
            updates.add(new UpdateOneModel<>(ApiInfoDao.getFilter(row.getId()),
                    Updates.combine(Updates.set(ApiInfo.TOOL_INFO_CAPABILITY, cap),
                            Updates.set(ApiInfo.TOOL_INFO_CALCULATED_AT, Context.now()))));
        }
        ApiInfoDao.instance.bulkWrite(updates, new com.mongodb.client.model.BulkWriteOptions().ordered(false));
        ApiInfo stored = ApiInfoDao.instance.findOne(ApiInfoDao.getFilter(row.getId()));
        assertNotNull(stored.getToolInfo());
        assertTrue(Arrays.asList("SAFE", "RESOURCE_DELETE").contains(stored.getToolInfo().getCapability()));
    }

    @Test
    public void urlWithRegexMetacharactersIsHandled() {
        toolRow(ARGUS_COLL, "/mcp/tools/call/a+b(c)[d]", Context.now(), null);
        assertEquals(1, seededCandidates().size());
    }
}
