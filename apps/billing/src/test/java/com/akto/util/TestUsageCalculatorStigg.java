package com.akto.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.akto.MongoBasedTest;
import com.akto.dao.billing.OrganizationUsageDao;
import com.akto.dao.usage.UsageMetricsDao;
import com.akto.dto.Config;
import com.akto.dto.billing.Organization;
import com.akto.dto.billing.OrganizationUsage;
import com.akto.dto.usage.MetricTypes;
import com.akto.dto.usage.UsageMetric;
import com.mongodb.BasicDBObject;

/**
 * Reporting usage to Stigg: every metric maps to its own feature id, and a metric that cannot be
 * reported is skipped and listed instead of stopping the org's other metrics.
 */
public class TestUsageCalculatorStigg extends MongoBasedTest {

    private static Config.StiggConfig config(boolean withEndpointAssetsLabel) {
        Config.StiggConfig c = new Config.StiggConfig();
        c.setActiveEndpointsLabel("f-endpoints");
        c.setActiveAccountsLabel("f-accounts");
        c.setTestRunsLabel("f-runs");
        c.setCustomTestsLabel("f-custom");
        c.setAiAssetsLabel("f-ai");
        c.setMcpAssetsLabel("f-mcp");
        if (withEndpointAssetsLabel) {
            c.setEndpointAssetsLabel("f-endpoint-assets");
        }
        return c;
    }

    private static OrganizationUsage usage(Object... keyValues) {
        // insertion-ordered, so a test can put the metric that gets skipped first
        Map<String, Integer> metrics = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            metrics.put((String) keyValues[i], (Integer) keyValues[i + 1]);
        }
        return new OrganizationUsage("org1", 20261120, 1000, metrics, new HashMap<>(), new HashMap<>());
    }

    private static Object featureOf(UsageCalculator.StiggReport report, String featureId) {
        for (Object o : report.updateList) {
            BasicDBObject update = (BasicDBObject) o;
            if (featureId.equals(update.getString("featureId"))) {
                return update.get("value");
            }
        }
        return null;
    }

    @Test
    public void everyMetricMapsToItsOwnFeatureId() {
        Config.StiggConfig c = config(true);
        assertEquals("f-endpoints", UsageCalculator.featureIdFor(MetricTypes.ACTIVE_ENDPOINTS, c));
        assertEquals("f-accounts", UsageCalculator.featureIdFor(MetricTypes.ACTIVE_ACCOUNTS, c));
        assertEquals("f-runs", UsageCalculator.featureIdFor(MetricTypes.TEST_RUNS, c));
        assertEquals("f-custom", UsageCalculator.featureIdFor(MetricTypes.CUSTOM_TESTS, c));
        assertEquals("f-ai", UsageCalculator.featureIdFor(MetricTypes.AI_ASSET_COUNT, c));
        assertEquals("f-endpoint-assets", UsageCalculator.featureIdFor(MetricTypes.ENDPOINT_ASSET_COUNT, c));
    }

    @Test
    public void retiredMcpMetricIsNeverReportedEvenIfItsLabelIsStillConfigured() {
        Config.StiggConfig c = config(true); // still has f-mcp configured
        assertNull(UsageCalculator.featureIdFor(MetricTypes.MCP_ASSET_COUNT, c));

        UsageCalculator.StiggReport report = UsageCalculator.instance.buildStiggReport(
                usage("MCP_ASSET_COUNT", 7, "ACTIVE_ENDPOINTS", 5), c);
        assertEquals(Arrays.asList("MCP_ASSET_COUNT"), report.skipped);
        assertEquals(1, report.updateList.size());
        assertEquals(5, featureOf(report, "f-endpoints"));
    }

    @Test
    public void noFeatureIdWithoutConfigOrLabel() {
        assertNull(UsageCalculator.featureIdFor(MetricTypes.ACTIVE_ENDPOINTS, null));
        assertNull(UsageCalculator.featureIdFor(MetricTypes.ENDPOINT_ASSET_COUNT, config(false)));
    }

    @Test
    public void allMetricsAreReportedWhenConfigured() {
        UsageCalculator.StiggReport report = UsageCalculator.instance.buildStiggReport(
                usage("ACTIVE_ENDPOINTS", 5, "AI_ASSET_COUNT", 3, "ENDPOINT_ASSET_COUNT", 81), config(true));

        assertTrue(report.skipped.isEmpty());
        assertEquals(3, report.updateList.size());
        assertEquals(81, featureOf(report, "f-endpoint-assets"));
        assertEquals(3, featureOf(report, "f-ai"));
        assertEquals(5, featureOf(report, "f-endpoints"));
        assertEquals(3, report.usageMetrics.size());
    }

    @Test
    public void metricWithoutFeatureIdIsSkippedAndOthersStillReported() {
        UsageCalculator.StiggReport report = UsageCalculator.instance.buildStiggReport(
                usage("ENDPOINT_ASSET_COUNT", 81, "ACTIVE_ENDPOINTS", 5, "AI_ASSET_COUNT", 3), config(false));

        assertEquals(Arrays.asList("ENDPOINT_ASSET_COUNT"), report.skipped);
        assertEquals(2, report.updateList.size());
        assertEquals(5, featureOf(report, "f-endpoints"));
        assertEquals(3, featureOf(report, "f-ai"));
        assertEquals(2, report.usageMetrics.size());
        for (UsageMetric m : report.usageMetrics) {
            assertTrue(m.getMetricType() != MetricTypes.ENDPOINT_ASSET_COUNT);
        }
    }

    @Test
    public void unknownMetricKeyIsSkippedAndOthersStillReported() {
        UsageCalculator.StiggReport report = UsageCalculator.instance.buildStiggReport(
                usage("SOME_FUTURE_METRIC", 9, "ACTIVE_ENDPOINTS", 5), config(true));

        assertEquals(Arrays.asList("SOME_FUTURE_METRIC"), report.skipped);
        assertEquals(1, report.updateList.size());
        assertEquals(5, featureOf(report, "f-endpoints"));
    }

    @Test
    public void everythingIsSkippedWhenStiggConfigIsMissing() {
        UsageCalculator.StiggReport report = UsageCalculator.instance.buildStiggReport(
                usage("ACTIVE_ENDPOINTS", 5), null);

        assertEquals(Arrays.asList("ACTIVE_ENDPOINTS"), report.skipped);
        assertTrue(report.updateList.isEmpty());
    }

    @Test
    public void aMeasuredZeroIsReportedAsZero() {
        UsageCalculator.StiggReport report = UsageCalculator.instance.buildStiggReport(
                usage("ENDPOINT_ASSET_COUNT", 0), config(true));

        assertTrue(report.skipped.isEmpty());
        assertEquals(0, featureOf(report, "f-endpoint-assets"));
    }

    private static void seedUsage(String orgId, MetricTypes type, int usage, int savedAt) {
        UsageMetric m = new UsageMetric(orgId, ACCOUNT_ID, type, 0, 0, "test", "test");
        m.setUsage(usage);
        m.setAktoSaveEpoch(savedAt);
        UsageMetricsDao.instance.insertOne(m);
    }

    @Test
    public void aggregationSkipsRetiredMetricAndNeverFillsAMissingOneWithZero() {
        String orgId = "org-aggregate";
        int lower = 1700000000;
        int upper = lower + 3600;
        UsageMetricsDao.instance.getMCollection().drop();
        OrganizationUsageDao.instance.getMCollection().drop();
        seedUsage(orgId, MetricTypes.ACTIVE_ENDPOINTS, 5, lower + 10);
        seedUsage(orgId, MetricTypes.AI_ASSET_COUNT, 3, lower + 10);
        // Written by an older build, must not be aggregated any more.
        seedUsage(orgId, MetricTypes.MCP_ASSET_COUNT, 7, lower + 10);
        // ENDPOINT_ASSET_COUNT was not measured this hour: no document at all.

        Organization organization = new Organization(orgId, "customer", "admin@customer.com",
                new HashSet<>(Collections.singletonList(ACCOUNT_ID)), false);
        UsageCalculator.instance.aggregateUsageForOrg(organization, lower, upper, null);

        OrganizationUsage saved = OrganizationUsageDao.instance.findOne(OrganizationUsage.ORG_ID, orgId);
        assertTrue("usage must have been aggregated", saved != null);
        Map<String, Integer> aggregated = saved.getOrgMetricMap();
        assertEquals(5, (int) aggregated.get("ACTIVE_ENDPOINTS"));
        assertEquals(3, (int) aggregated.get("AI_ASSET_COUNT"));
        assertTrue("retired metric must not be aggregated", !aggregated.containsKey("MCP_ASSET_COUNT"));
        assertTrue("an unmeasured metric must be absent, not 0", !aggregated.containsKey("ENDPOINT_ASSET_COUNT"));
    }
}
