package com.akto.usage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.ApiCollectionsDao;
import com.akto.dao.SingleTypeInfoDao;
import com.akto.dao.context.Context;
import com.akto.dto.ApiCollection;
import com.akto.dto.testing.custom_groups.UnauthenticatedEndpoint;
import com.akto.dto.traffic.CollectionTags;
import com.akto.dto.type.SingleTypeInfo;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.akto.types.CappedSet;
import com.akto.util.Constants;

/**
 * ACTIVE_ENDPOINTS counts endpoints in API Security / DAST collections only. AI_ASSET_COUNT counts
 * agentic collections. ENDPOINT_ASSET_COUNT is the most devices behind any single AI agent. A metric
 * that cannot be measured must throw, never report 0.
 */
public class TestUsageMetricCalculator extends MongoBasedTest {

    private static final int PLAIN_API = 91001;
    private static final int DAST = 91002;
    private static final int MCP = 91003;
    private static final int GEN_AI = 91004;
    private static final int CALLER = 91005;
    private static final int ENDPOINT_SOURCE = 91006;
    private static final int GEN_AI_ENDPOINT_SOURCE = 91007;
    private static final int DEACTIVATED_MCP = 91008;
    private static final int DEACTIVATED_API = 91009;

    private static final int UNSET = -7;

    private void seed(int id, boolean deactivated, CollectionTags... tags) {
        ApiCollection c = new ApiCollection(id, "c" + id, Context.now(), new HashSet<>(), "c" + id + ".host", 0, false, true);
        c.setTagsList(new ArrayList<>(Arrays.asList(tags)));
        c.setDeactivated(deactivated);
        ApiCollectionsDao.instance.insertOne(c);
    }

    private static CollectionTags tag(String key, String value) {
        return new CollectionTags(Context.now(), key, value, CollectionTags.TagSource.KUBERNETES);
    }

    private static CollectionTags tag(String key) {
        return tag(key, "v");
    }

    private void seedEndpoints(int collectionId, int count) {
        List<SingleTypeInfo> stis = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            SingleTypeInfo.ParamId paramId = new SingleTypeInfo.ParamId(
                    "/url" + i, "GET", -1, true, "host", SingleTypeInfo.GENERIC, collectionId, false);
            stis.add(new SingleTypeInfo(paramId, new HashSet<>(), new HashSet<>(), 0, 0, 0,
                    new CappedSet<>(), SingleTypeInfo.Domain.ENUM, 0, 0));
        }
        SingleTypeInfoDao.instance.insertMany(stis);
    }

    private UsageMetric metric(MetricTypes type) {
        UsageMetric usageMetric = new UsageMetric("org", ACCOUNT_ID, type, 0, 0, "test", "test");
        usageMetric.setUsage(UNSET);
        return usageMetric;
    }

    @Before
    public void seedFixture() {
        Context.resetContextThreadLocals();
        Context.accountId.set(ACCOUNT_ID);
        ApiCollectionsDao.instance.getMCollection().drop();
        SingleTypeInfoDao.instance.getMCollection().drop();

        seed(PLAIN_API, false);
        seed(DAST, false, tag(Constants.AKTO_DAST_TAG));
        seed(MCP, false, tag(Constants.AKTO_MCP_SERVER_TAG));
        seed(GEN_AI, false, tag(Constants.AKTO_GEN_AI_TAG));
        seed(CALLER, false, tag(Constants.AKTO_AI_AGENT_CALLER_TAG));
        seed(ENDPOINT_SOURCE, false, tag(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE));
        seed(GEN_AI_ENDPOINT_SOURCE, false, tag(Constants.AKTO_GEN_AI_TAG),
                tag(Constants.AKTO_ENDPOINT_SOURCE_TAG, Constants.AKTO_ENDPOINT_SOURCE_VALUE));
        seed(DEACTIVATED_MCP, true, tag(Constants.AKTO_MCP_SERVER_TAG));
        seed(DEACTIVATED_API, true);

        seedEndpoints(PLAIN_API, 2);
        seedEndpoints(DAST, 1);
        seedEndpoints(MCP, 5);
        seedEndpoints(GEN_AI, 4);
        seedEndpoints(CALLER, 3);
        seedEndpoints(ENDPOINT_SOURCE, 7);
        seedEndpoints(GEN_AI_ENDPOINT_SOURCE, 8);
        seedEndpoints(DEACTIVATED_MCP, 9);
        seedEndpoints(DEACTIVATED_API, 6);
    }

    @Test
    public void activeEndpointsCountsOnlyApiAndDastCollections() {
        // plain API (2) + DAST (1); agentic, endpoint-source and deactivated collections are excluded.
        assertEquals(3, UsageMetricCalculator.calculateActiveEndpoints());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void activeEndpointsIgnoresStaleDeactivatedCache() throws Exception {
        // getDeactivated() caches per account for 2 minutes in a static map, so any earlier code in the
        // same JVM can leave it stale. A billed count must still exclude collections deactivated since.
        Field fetchedAt = UsageMetricCalculator.class.getDeclaredField("lastDeactivatedFetchedMap");
        Field cached = UsageMetricCalculator.class.getDeclaredField("deactivatedCollectionsMap");
        fetchedAt.setAccessible(true);
        cached.setAccessible(true);
        Map<Integer, Integer> fetchedAtMap = (Map<Integer, Integer>) fetchedAt.get(null);
        Map<Integer, Set<Integer>> cachedMap = (Map<Integer, Set<Integer>>) cached.get(null);
        try {
            cachedMap.put(ACCOUNT_ID, new HashSet<>());
            fetchedAtMap.put(ACCOUNT_ID, Context.now());
            assertEquals(3, UsageMetricCalculator.calculateActiveEndpoints());
        } finally {
            cachedMap.remove(ACCOUNT_ID);
            fetchedAtMap.remove(ACCOUNT_ID);
        }
    }

    @Test
    public void aiAssetsCountsAgenticCollectionsNotEndpoints() {
        // MCP, GenAI, ai-agent-caller. Not: plain, DAST, endpoint-source, genAI-on-endpoint-source, deactivated.
        assertEquals(3, UsageMetricCalculator.calculateAgenticAssets());
    }

    @Test
    public void aiAssetsDoesNotCountTheUnauthenticatedMcpToolsGroup() {
        // The dashboard's AGENTIC context lists this group, but it holds no endpoints of its own.
        seed(UnauthenticatedEndpoint.UNAUTHENTICATED_MCP_TOOLS_GROUP_ID, false);
        assertEquals(3, UsageMetricCalculator.calculateAgenticAssets());
    }

    @Test
    public void usageMetricIsMeasuredThroughCalculateUsageMetric() {
        UsageMetric active = metric(MetricTypes.ACTIVE_ENDPOINTS);
        UsageMetricCalculator.calculateUsageMetric(active);
        assertEquals(3, active.getUsage());

        UsageMetric ai = metric(MetricTypes.AI_ASSET_COUNT);
        UsageMetricCalculator.calculateUsageMetric(ai);
        assertEquals(3, ai.getUsage());
    }

    private void seedAgent(int id, String device, String agent, String service, boolean deactivated, CollectionTags... tags) {
        ApiCollection c = new ApiCollection(id, "a" + id, Context.now(), new HashSet<>(),
                device + "." + agent + "." + service, 0, false, true);
        c.setTagsList(new ArrayList<>(Arrays.asList(tags)));
        c.setDeactivated(deactivated);
        ApiCollectionsDao.instance.insertOne(c);
    }

    @Test
    public void endpointAssetCountIsMaxDevicesOfASingleAiAgent() {
        ApiCollectionsDao.instance.getMCollection().drop();
        // cursor: 2 devices. claude: 3 devices (dev1 twice, counted once).
        seedAgent(92001, "dev1", "cursor", "cursor", false, tag(Constants.AKTO_AI_AGENT_TAG, "cursor"));
        seedAgent(92002, "dev2", "cursor", "cursor", false, tag(Constants.AKTO_AI_AGENT_TAG, "cursor"));
        seedAgent(92003, "dev1", "claude", "claude", false, tag(Constants.AKTO_AI_AGENT_TAG, "claude"));
        seedAgent(92004, "dev1", "claude", "other", false, tag(Constants.AKTO_AI_AGENT_TAG, "claude"));
        seedAgent(92005, "dev3", "claude", "claude", false, tag(Constants.AKTO_AI_AGENT_TAG, "claude"));
        seedAgent(92006, "dev4", "claude", "claude", false, tag(Constants.AKTO_AI_AGENT_TAG, "claude"));
        // An MCP server reached from 5 devices is a bigger group, but not an "AI Agent" row.
        for (int i = 1; i <= 5; i++) {
            seedAgent(92010 + i, "dev" + i, "claude", "github", false, tag(Constants.AKTO_MCP_SERVER_TAG));
        }
        // A deactivated agent with 10 devices must not count.
        for (int i = 1; i <= 10; i++) {
            seedAgent(92020 + i, "dev" + i, "ghost", "ghost", true, tag(Constants.AKTO_AI_AGENT_TAG, "ghost"));
        }

        UsageMetric usageMetric = metric(MetricTypes.ENDPOINT_ASSET_COUNT);
        UsageMetricCalculator.calculateUsageMetric(usageMetric);
        assertEquals(3, usageMetric.getUsage());
    }

    @Test
    public void endpointAssetCountIsZeroWhenThereAreNoAiAgents() {
        ApiCollectionsDao.instance.getMCollection().drop();
        UsageMetric usageMetric = metric(MetricTypes.ENDPOINT_ASSET_COUNT);
        UsageMetricCalculator.calculateUsageMetric(usageMetric);
        assertEquals(0, usageMetric.getUsage());
    }

    @Test
    public void retiredMcpMetricIsNotMeasured() {
        UsageMetric usageMetric = metric(MetricTypes.MCP_ASSET_COUNT);
        assertThrows(IllegalArgumentException.class, () -> UsageMetricCalculator.calculateUsageMetric(usageMetric));
        assertEquals(UNSET, usageMetric.getUsage());
    }

    @Test
    public void metricWithoutTypeIsNotMeasured() {
        UsageMetric usageMetric = metric(null);
        assertThrows(IllegalArgumentException.class, () -> UsageMetricCalculator.calculateUsageMetric(usageMetric));
        assertEquals(UNSET, usageMetric.getUsage());
    }
}
